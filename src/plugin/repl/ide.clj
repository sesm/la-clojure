(ns plugin.repl.ide
  (:import (org.jetbrains.plugins.clojure.repl.toolwindow.actions NewConsoleActionBase)
           (org.jetbrains.plugins.clojure.repl REPLProviderBase Response)
           (org.jetbrains.plugins.clojure.repl.impl REPLBase)
           (com.intellij.openapi.actionSystem AnAction ActionManager DefaultActionGroup)
           (java.io Writer PrintWriter StringReader StringWriter)
           (clojure.lang LineNumberingPushbackReader)
           (com.intellij.openapi.diagnostic Logger))
  (:require [plugin.actions.core :as actions]))

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

(defn ns-symbols [the-ns]
  (map str (keys (ns-interns the-ns))))

(defn completions [client-state]
  (let [the-ns (get @client-state #'*ns*)]
    {:imports    (map (fn [^Class c] (.getName c)) (vals (ns-imports the-ns))),
     :symbols    (map str (keys (filter (fn [v] (var? (second v))) (seq (ns-map the-ns)))))
     :namespaces (map str (all-ns))}))

(defn create-repl [project module console-view working-dir]
  (let [active (atom false)
        client-state (atom {#'*ns* (create-ns 'user)})]
    (proxy [REPLBase] [console-view project]
      (start []
        (swap! active (fn [atom] true)))
      (doStop []
        (swap! active (fn [atom] false)))
      (doExecute [command]
        (let [response (ide-execute client-state command)]
          (proxy [Response] [nil]
            (combinedResponse []
              response)
            (values []
              (:value response)))))
      (isActive [] @active)
      (getType [] "IDE")
      (getCompletions []
        (completions client-state))
      (getSymbolsInNS [ns-name]
        (if-let [the-ns (find-ns (symbol ns-name))]
          (ns-symbols the-ns))))))

(defn initialise []
  (let [action (proxy [NewConsoleActionBase] []
                 (getProvider []
                   (proxy [REPLProviderBase] []
                     (isSupported [] true)
                     (newREPL [project module console-view working-dir]
                       (create-repl project module console-view working-dir)))))]
    (actions/register-action action "NewIDERepl" "ToolsMenu")
    (actions/set-text action "Start IDE Clojure Console")))
