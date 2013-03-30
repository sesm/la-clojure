(ns plugin.repl
  (:refer-clojure :exclude [print])
  (:import (org.jetbrains.plugins.clojure.repl Printing)))

(def init-command "(println (str \"Clojure \" (clojure-version)))")

(defprotocol IRepl
  "Basic actions required of a REPL"
  (execute [this state command])
  (stop [this state])
  (completions [this state])
  (ns-symbols [this state ns-name]))

(defn print
  ([state message]
   (print state message Printing/NORMAL_TEXT))
  ([state message attributes]
   (let [{:keys [history-viewer]} @state]
     (Printing/printToHistory history-viewer message attributes))))

(defn print-error [state message]
  (print state message Printing/ERROR_TEXT))

(defn print-value [state value]
  (let [{:keys [history-viewer]} @state]
    (Printing/printValue history-viewer value)))
