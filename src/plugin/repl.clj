(ns plugin.repl)

(def init-command "(println (str \"Clojure \" (clojure-version)))")

(defprotocol IRepl
  "Basic actions required of a REPL"
  (execute [this state command])
  (stop [this state])
  (completions [this state])
  (ns-symbols [this state ns-name]))
