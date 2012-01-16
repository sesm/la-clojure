(ns plugin.initialise
  (:require plugin.formatting
   plugin.typing))

(defn initialise-all []
  (plugin.formatting/initialise)
  (plugin.typing/initialise))
