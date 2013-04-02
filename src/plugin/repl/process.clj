(ns plugin.repl.process
  (:import (org.jetbrains.plugins.clojure.repl REPLComponent REPLUtil ClojureConsoleView
                                               TerminateREPLDialog Printing)
           (com.intellij.openapi.actionSystem AnAction ActionManager DefaultActionGroup AnActionEvent)
           (java.io Writer PrintWriter StringReader StringWriter File Closeable)
           (clojure.lang LineNumberingPushbackReader)
           (com.intellij.openapi.diagnostic Logger)
           (com.intellij.execution.configurations JavaParameters GeneralCommandLine ParametersList)
           (com.intellij.facet FacetManager)
           (org.jetbrains.plugins.clojure.config ClojureFacet ClojureConfigUtil)
           (org.jetbrains.plugins.clojure.utils ClojureUtils Editors)
           (com.intellij.execution ExecutionHelper KillableProcess)
           (org.jetbrains.plugins.clojure ClojureBundle ClojureIcons)
           (com.intellij.execution.process ColoredProcessHandler ProcessTerminatedListener ProcessAdapter
                                           ProcessHandler)
           (javax.swing JPanel)
           (com.intellij.openapi.module Module)
           (com.intellij.openapi.roots ModuleRootManager ModuleRootModel)
           (java.util List)
           (com.intellij.openapi.editor.ex EditorEx)
           (com.intellij.openapi.vfs VirtualFile))
  (:require [plugin.actions :as actions]
            [clojure.tools.nrepl :as nrepl]
            [clojure.tools.nrepl.ack :as ack]
            [clojure.string :as str]
            [plugin.editor :as editor]
            [plugin.repl :as repl]
            [plugin.repl.toolwindow :as toolwindow]
            [plugin.util :as util]
            [plugin.executor :as executor]))

(def ^Logger logger (Logger/getInstance (str *ns*)))

(defn ^ClojureFacet clojure-facet [module]
  (let [facet-manager (FacetManager/getInstance module)]
    (.getFacetByType facet-manager ClojureFacet/ID)))

(defn ^List jvm-clojure-options [module]
  (let [facet (clojure-facet module)]
    (if-let [options (util/safely (.getJvmOptions facet))]
      (if-not (str/blank? options)
        (str/split (str/trim options) #"\s+")
        [])
      [])))

(defn ^List repl-clojure-options [module]
  (let [facet (clojure-facet module)]
    (if-let [options (util/safely (.getReplOptions facet))]
      (if-not (str/blank? options)
        (str/split (str/trim options) #"\s+")
        [])
      [])))

(defn ^GeneralCommandLine runtime-arguments [project module ^String working-dir]
  (let [params (JavaParameters.)
        vm-params ^ParametersList (.getVMParametersList params)
        program-params ^ParametersList (.getProgramParametersList params)
        class-path (.getClassPath params)]
    (.configureByModule params module JavaParameters/JDK_AND_CLASSES)
    (.addAll vm-params (jvm-clojure-options module))
    (.addAll program-params (repl-clojure-options module))
    (.addAll program-params ["--port" "0" "--ack" (str (REPLComponent/getLocalPort))])
    (when-not (ClojureConfigUtil/isClojureConfigured module)
      (.add class-path ClojureConfigUtil/CLOJURE_SDK)
      (ClojureConfigUtil/warningDefaultClojureJar module))
    (.add class-path ClojureConfigUtil/NREPL_LIB)
    (REPLUtil/addSourcesToClasspath module params)
    (.setMainClass params ClojureUtils/REPL_MAIN)
    (.setWorkingDirectory params (File. working-dir))
    (REPLUtil/getCommandLine params project)))

(defn create-process [project ^GeneralCommandLine command-line]
  (try
    (.createProcess command-line)
    (catch Exception e#
      (.error logger "Error creating REPL process" e#)
      (ExecutionHelper/showErrors project [e#] "Errors" nil)
      (throw e#))))

(defn set-editor-enabled [state enabled]
  (let [{:keys [^EditorEx console-editor]} @state]
    (util/invoke-later
      (.setRendererMode console-editor ^boolean (not enabled))
      (-> console-editor .getComponent .updateUI))))

(defn read-value [value]
  (try
    (read-string value)
    (catch Exception e#
      nil)))

(def completion-init
     (nrepl/code
       (ns la-clojure.repl)
       (defn ns-symbols [the-ns]
         (map str (keys (ns-interns the-ns))))
       (defn ns-symbols-by-name [ns-name]
         (if-let [the-ns (find-ns ns-name)]
           (apply vector (ns-symbols the-ns))))
       (defn completions [ns]
         (if-let [ns (find-ns ns)]
           {:imports    (map (fn [c] (.getName c)) (vals (ns-imports ns))),
            :symbols    (map str (keys (filter (fn [v] (var? (second v))) (ns-map ns))))
            :namespaces (map str (all-ns))}
           {}))))

(defn tooling-command [state command]
  (let [{:keys [tooling-session history-viewer]} @state]
    (if tooling-session
      (nrepl/combine-responses
        (nrepl/message tooling-session {:op   "eval"
                                        :code command})))))

(defn ns-symbols [state ns]
  (let [command (str "(la-clojure.repl/ns-symbols-by-name '" ns ")")]
    (if-let [result (tooling-command state command)]
      (if-let [error (:err result)]
        (repl/print-error state (str "Error completing ns:\n" error))
        (or (first (filter vector? (map read-value (:value result))))
            [])))))

(defn update-completions [state]
  (let [ns (:ns @state)
        command (str "(la-clojure.repl/completions '" ns ")")]
    (if-let [result (tooling-command state command)]
      (if-let [error (:err result)]
        (repl/print-error state (str "Error updating completions:\n" error))
        (swap! state assoc :completions (or (first (filter map? (map read-value (:value result))))
                                            {}))))))

(defn init-completion [state command]
  (if-let [result (tooling-command state command)]
    (if-let [error (:err result)]
      (repl/print-error state (str "Error initialising completion:\n" error))
      (update-completions state))))

(defn start [state]
  (ack/reset-ack-port!)
  (repl/print state "Starting nREPL server...\n")
  (let [{:keys [project module working-dir ^ClojureConsoleView console-view]} @state
        arguments (runtime-arguments project module working-dir)
        process (create-process project arguments)
        handler (proxy [ColoredProcessHandler] [process (.getCommandLineString arguments)]
                  (textAvailable [text attributes]
                    (.info logger (str/trim text))))]
    (ProcessTerminatedListener/attach handler)
    (.addProcessListener handler (proxy [ProcessAdapter] []
                                   (processTerminated [event]
                                     (toolwindow/stop state)
                                     (set-editor-enabled state false))))
    (.attachToProcess console-view handler)
    (.startNotify handler)
    (swap! state assoc :process-handler handler)
    (if-let [port (ack/wait-for-ack 30000)]
      (let [connection (nrepl/connect :port port)
            client (nrepl/client connection 1000)
            session (nrepl/client-session client)
            tooling-session (nrepl/client-session client)]
        (swap! state assoc
               :connection connection
               :client client
               :session session
               :tooling-session tooling-session)
        (init-completion state completion-init))
      (do
        (repl/print-error state "No nREPL ack received\n")
        (toolwindow/stop state)))))

(defn stop [state]
  (let [{:keys [^Closeable connection ^ProcessHandler process-handler]} @state]
    (if connection
      (.close connection)
      (swap! state dissoc :connection))
    (when process-handler
      (if (and (instance? KillableProcess process-handler)
               (.isProcessTerminating process-handler))
        (.killProcess ^KillableProcess process-handler))
      (.destroyProcess process-handler))))

(defn active? [state]
  (boolean
    (if-let [^ProcessHandler handler (:process-handler @state)]
      (if (.isStartNotified handler)
        (not (or (.isProcessTerminated handler)
                 (.isProcessTerminating handler)))
        false))))

(defn execute [state command print-values?]
  (let [{:keys [session history-viewer]} @state]
    (when session
      (doseq [item (nrepl/message session {:op "eval" :code command})]
        (when-let [ns (:ns item)]
          (toolwindow/set-title! state (str "nREPL: " ns))
          (swap! state assoc :ns ns))
        (when-let [error (:err item)]
          (repl/print-error state error))
        (when-let [output (:out item)]
          (repl/print state output))
        (if print-values?
          (when-let [value (:value item)]
            (repl/print state "=> " Printing/USER_INPUT_TEXT)
            (if-let [obj (read-value value)]
              (repl/print-value state obj)
              (repl/print state value))
            (repl/print state "\n")))
        (let [unknown-keys (disj (set (keys item)) :ns :err :out :value :id :session :status :ex :root-ex)]
          (if-not (empty? unknown-keys)
            (repl/print-error state (str "DEBUG: unknown response keys " unknown-keys "\n")))))
      (update-completions state))))

(defn nrepl-repl []
  (reify repl/IRepl
    (execute [this state command]
      (execute state command true))
    (stop [this state]
      (stop state))
    (completions [this state]
      (:completions @state))
    (ns-symbols [this state ns-name]
      (ns-symbols state ns-name))))

(defn create-new-repl [^AnActionEvent event]
  (let [module (actions/module event)
        module-root-manager (ModuleRootManager/getInstance module)
        content-root (first (seq (.getContentRoots module-root-manager)))
        working-dir (.getPath ^VirtualFile content-root)
        state (atom {:module      module
                     :project     (.getProject ^Module module)
                     :repl        (nrepl-repl)
                     :working-dir working-dir
                     :completions {}
                     :ns          "user"
                     :active?     active?})]
    (toolwindow/create-repl state "nREPL: user")
    (toolwindow/repl-submit state
                            (fn []
                              (start state)
                              (execute state repl/init-command false)
                              (toolwindow/enabled! state true)
                              (toolwindow/focus-editor state)))))

(defn initialise []
  (actions/unregister-action ::new-nrepl "ToolsMenu")
  (let [action (actions/dumb-aware
                 :action-performed create-new-repl
                 :icon ClojureIcons/REPL_GO
                 :text "Start local nREPL Console")]
    (actions/register-action action ::new-nrepl "ToolsMenu")))
