(ns plugin.resolve.core)

;(set! *warn-on-reflection* true)

(defprotocol Resolvable
  (process-declarations [this processor state last-parent place]))

(defn punt [element processor state last-parent place]
  (if (satisfies? Resolvable element)
    (process-declarations element processor state last-parent place)
    true))
