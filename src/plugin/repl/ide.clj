(ns plugin.repl.ide
  (:import (org.jetbrains.plugins.clojure.repl Printing)
           (com.intellij.openapi.actionSystem AnAction ActionManager DefaultActionGroup AnActionEvent)
           (java.io Writer PrintWriter StringReader StringWriter)
           (clojure.lang LineNumberingPushbackReader)
           (com.intellij.openapi.diagnostic Logger)
           (org.jetbrains.plugins.clojure ClojureIcons)
           (com.intellij.openapi.module Module))
  (:require [plugin.actions :as actions]
            [plugin.repl :as repl]
            [plugin.repl.toolwindow :as toolwindow]
            [plugin.editor :as editor]
            [plugin.util :as util]))

(defn ide-execute [client-state code]
  (let [code-reader (LineNumberingPushbackReader. (StringReader. code))
        out-buffer (StringWriter.)
        err-buffer (StringWriter.)
        values (atom [])
        out (PrintWriter. out-buffer)
        err (PrintWriter. err-buffer)
        in ""]
    (try
      (clojure.main/repl
        :init #(push-thread-bindings (merge @client-state
                                            {#'*in*  (LineNumberingPushbackReader. (StringReader. in))
                                             #'*out* out
                                             #'*err* err}))
        :read (fn [prompt exit] (read code-reader false exit))
        :caught (fn [e]
                  (let [repl-exception (clojure.main/repl-exception e)]
                    (swap! client-state assoc #'*e e)
                    (binding [*out* *err*]
                      (prn repl-exception)
                      (flush))))
        :prompt (fn [])
        :need-prompt (constantly false)
        :print (fn [value]
                 (swap! client-state (fn [m]
                                       (with-meta (assoc (get-thread-bindings)
                                                    #'*3 *2
                                                    #'*2 *1
                                                    #'*1 value)
                                                  (meta m))))
                 (swap! values conj value)))
      (finally
        (pop-thread-bindings)
        (.flush out)
        (.flush err)))
    {:value @values,
     :out   (str out-buffer),
     :err   (str err-buffer),
     :ns    (str (ns-name (get @client-state #'*ns*)))}))

(defn completions [client-state]
  (let [the-ns (get @client-state #'*ns*)]
    {:imports    (map (fn [^Class c] (.getName c)) (vals (ns-imports the-ns))),
     :symbols    (map str (keys (filter (fn [v] (var? (second v))) (ns-map the-ns))))
     :namespaces (map str (all-ns))}))

(defn do-execute [state command print-values?]
  (let [{:keys [history-viewer client-state]} @state
        result (util/with-read-action
                 (ide-execute client-state command))]
    (when-let [ns (:ns result)]
      (toolwindow/set-title! state (str "IDE: " ns)))
    (when-let [error (:err result)]
      (repl/print-error state error))
    (when-let [output (:out result)]
      (repl/print state output))
    (if print-values?
      (when-let [values (:value result)]
        (doseq [value values]
          (repl/print state "=> " Printing/USER_INPUT_TEXT)
          (repl/print-value state value)
          (repl/print state "\n"))))))

(defn ide-repl []
  (reify repl/IRepl
    (execute [this state command]
      (do-execute state command true))
    (stop [this state]
      (swap! state assoc :active? (fn [state] false)))
    (completions [this state]
      (completions (:client-state @state)))
    (ns-symbols [this state ns-name]
      (if-let [the-ns (find-ns (symbol ns-name))]
        (map str (keys (ns-interns the-ns)))))))

(defn create-new-repl [^AnActionEvent event]
  (let [module (actions/module event)
        state (atom {:module  module
                     :project (.getProject ^Module module)
                     :repl (ide-repl)
                     :client-state (atom {#'*ns* (create-ns 'user)})
                     :active? (fn [state] true)})]
    (toolwindow/create-repl state "IDE: user")
    (do-execute state repl/init-command false)
    (toolwindow/enabled! state true)
    (toolwindow/focus-editor state)))

(defn initialise []
  (actions/unregister-action ::new-ide-repl "ToolsMenu")
  (let [action (actions/dumb-aware
                 :action-performed create-new-repl
                 :icon ClojureIcons/REPL_GO
                 :text "Start IDE Clojure Console")]
    (actions/register-action action ::new-ide-repl "ToolsMenu")))
