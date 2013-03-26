(ns plugin.repl.process
  (:import (org.jetbrains.plugins.clojure.repl REPLComponent REPLUtil ClojureConsoleView
                                               TerminateREPLDialog Printing)
           (com.intellij.openapi.actionSystem AnAction ActionManager DefaultActionGroup AnActionEvent)
           (java.io Writer PrintWriter StringReader StringWriter File)
           (clojure.lang LineNumberingPushbackReader)
           (com.intellij.openapi.diagnostic Logger)
           (com.intellij.execution.configurations JavaParameters GeneralCommandLine)
           (com.intellij.facet FacetManager)
           (org.jetbrains.plugins.clojure.config ClojureFacet ClojureConfigUtil)
           (org.jetbrains.plugins.clojure.utils ClojureUtils Editors)
           (com.intellij.execution ExecutionHelper KillableProcess)
           (org.jetbrains.plugins.clojure ClojureBundle ClojureIcons)
           (com.intellij.execution.process ColoredProcessHandler ProcessTerminatedListener ProcessAdapter
                                           ProcessHandler)
           (javax.swing JPanel)
           (com.intellij.openapi.module Module)
           (com.intellij.openapi.roots ModuleRootManager))
  (:require [plugin.actions :as actions]
            [clojure.tools.nrepl :as nrepl]
            [clojure.tools.nrepl.ack :as ack]
            [clojure.string :as str]
            [plugin.editor :as editor]
            [plugin.repl :as repl]
            [plugin.repl.toolwindow :as toolwindow]
            [plugin.util :as util]))

(def logger (Logger/getInstance (str *ns*)))

(defn ^ClojureFacet clojure-facet [module]
  (let [facet-manager (FacetManager/getInstance module)]
    (.getFacetByType facet-manager ClojureFacet/ID)))

(defn jvm-clojure-options [module]
  (let [facet (clojure-facet module)]
    (if-let [options (util/safely (.getJvmOptions facet))]
      (if-not (str/blank? options)
        (str/split (str/trim options) #"\w+")
        [])
      [])))

(defn repl-clojure-options [module]
  (let [facet (clojure-facet module)]
    (if-let [options (util/safely (.getReplOptions facet))]
      (if-not (str/blank? options)
        (str/split (str/trim options) #"\w+")
        [])
      [])))

(defn runtime-arguments [module working-dir]
  (let [params (JavaParameters.)
        vm-params (.getVMParametersList params)
        program-params (.getProgramParametersList params)
        class-path (.getClassPath params)]
    (.configureByModule params module JavaParameters/JDK_AND_CLASSES)
    ;    (.addAll vm-params (jvm-clojure-options module))
    ;    (.addAll program-params (repl-clojure-options module))
    (.addAll program-params ["--port" "0" "--ack" (str (REPLComponent/getLocalPort))])
    (when-not (ClojureConfigUtil/isClojureConfigured module)
      (.add class-path ClojureConfigUtil/CLOJURE_SDK)
      (ClojureConfigUtil/warningDefaultClojureJar module))
    (.add class-path ClojureConfigUtil/NREPL_LIB)
    (REPLUtil/addSourcesToClasspath module params)
    (.setMainClass params ClojureUtils/REPL_MAIN)
    (.setWorkingDirectory params (File. working-dir))
    (REPLUtil/getCommandLine params)))

(defn create-process [project ^GeneralCommandLine command-line]
  (try
    (.createProcess command-line)
    (catch Exception e#
      (.error logger "Error creating REPL process" e#)
      (ExecutionHelper/showErrors project [e#] "Errors" nil)
      (throw e#))))

(defn hide-editor [state]
  (let [{:keys [console-view console history-viewer]} @state]
    (util/invoke-later
      (let [component (.getComponent console-view)
            parent (.getParent component)]
        (if (instance? JPanel parent)
          (do
            (.add parent (.getComponent history-viewer))
            (.remove parent component)
            (editor/scroll-down history-viewer)
            (.updateUI parent)))))))

(defn set-editor-enabled [state enabled]
  (let [{:keys [console-editor]} @state]
    (util/invoke-later
      (.setRendererMode console-editor (not enabled))
      (-> console-editor .getComponent .updateUI))))

(defn read-value [value]
  (try
    (read-string value)
    (catch Exception e#
      nil)))

(def completion-init
     (str "(defn ns-symbols [the-ns]\n"
          "  (map str (keys (ns-interns the-ns))))\n"
          "(defn ns-symbols-by-name [ns-name]\n"
          "  (if-let [the-ns (find-ns (symbol ns-name))]\n"
          "    (ns-symbols the-ns)))\n"
          "(defn completions []\n"
          "  {:imports    (map (fn [c] (.getName c)) (vals (ns-imports *ns*))),\n"
          "   :symbols    (map str (keys (filter (fn [v] (var? (second v))) (seq (ns-map *ns*)))))\n"
          "   :namespaces (map str (all-ns))})\n"))

(defn init-completion [state ns command]
  (let [{:keys [client history-viewer]} @state]
    (if client
      (let [item (nrepl/combine-responses
                   (nrepl/message client {:op   "eval"
                                          :code command
                                          :ns   ns}))]
        (when-let [error (:err item)]
          (repl/print-error state (str "Error initialising completion:\n" error))
          (util/invoke-later
            (editor/scroll-down history-viewer)))))))

(defn start [state]
  (ack/reset-ack-port!)
  (let [{:keys [project module working-dir console-view]} @state
        arguments (runtime-arguments module working-dir)
        process (create-process project arguments)
        handler (proxy [ColoredProcessHandler] [process (.getCommandLineString arguments)]
                  (textAvailable [text attributes]
                    (.info logger (str/trim text))))]
    (ProcessTerminatedListener/attach handler)
    (.addProcessListener handler (proxy [ProcessAdapter] []
                                   (processTerminated [event]
                                     (set-editor-enabled state false)
                                     (hide-editor state))))
    (.attachToProcess console-view handler)
    (.startNotify handler)
    (swap! state assoc :process-handler handler)
    ; TODO error handling
    (let [port (ack/wait-for-ack 30000)
          connection (nrepl/connect :port port)
          client (nrepl/client connection 1000)]
      (swap! state assoc
             :connection connection
             :client client))))

(defn stop [state]
  (let [{:keys [connection process-handler]} @state]
    (if connection
      (.close connection)
      (swap! state dissoc :connection))
    (when process-handler
      (if (and (instance? KillableProcess process-handler)
               (.isProcessTerminating process-handler))
        (.killProcess ^KillableProcess process-handler))
      (if (.detachIsDefault process-handler)
        (.detachProcess process-handler)
        (.destroyProcess process-handler)))))

(defn active? [state]
  (if-let [handler (:process-handler @state)]
    (if (.isStartNotified handler)
      (not (or (.isProcessTerminated handler)
               (.isProcessTerminating handler)))
      false)))

(defn execute [state command print-values?]
  (let [{:keys [client history-viewer]} @state]
    (if client
      (doseq [item (nrepl/message client {:op "eval" :code command})]
        (when-let [ns (:ns item)]
          (toolwindow/set-title! state (str "nREPL: " ns)))
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
            (repl/print-error state (str "DEBUG: unknown response keys " unknown-keys "\n"))))
        (util/invoke-later
          (editor/scroll-down history-viewer))))))

(defn nrepl-repl []
  (reify repl/IRepl
    (execute [this state command]
      (execute state command true))
    (stop [this state]
      (stop state))
    (completions [this state]
      (:completions @state))
    (ns-symbols [this state ns-name]
      [])))

(defn create-new-repl [^AnActionEvent event]
  (let [module (actions/module event)
        module-root-manager (ModuleRootManager/getInstance module)
        content-root (first (seq (.getContentRoots module-root-manager)))
        working-dir (.getPath content-root)
        state (atom {:module      module
                     :project     (.getProject ^Module module)
                     :repl        (nrepl-repl)
                     :working-dir working-dir
                     :completions {}
                     :active?     active?})]
    (toolwindow/create-repl state "nREPL: user")
    (start state)
;    (init-completion state "user" "(ns la-clojure.repl)")
;    (init-completion state "la-clojure.repl" completion-init)
    (execute state repl/init-command false)
    (toolwindow/enabled! state true)
    (toolwindow/focus-editor state)))

(defn initialise []
  (actions/unregister-action ::new-nrepl "ToolsMenu")
  (let [action (actions/dumb-aware
                 :action-performed create-new-repl
                 :icon ClojureIcons/REPL_GO
                 :text "Start local nREPL Console")]
    (actions/register-action action ::new-nrepl "ToolsMenu")))
