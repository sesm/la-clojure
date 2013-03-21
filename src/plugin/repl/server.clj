(ns plugin.repl.server
  (:require [clojure.tools.nrepl.server :as server]
            [clojure.tools.nrepl.ack :as ack]))

(defn start-server []
  (server/start-server
    :handler (ack/handle-ack (server/default-handler))))
