(ns plugin.initialise
  (:require plugin.formatting
            plugin.typing
            plugin.annotator))

(defn initialise-all []
  (plugin.formatting/initialise)
  (plugin.typing/initialise)
  (plugin.annotator/initialise))
