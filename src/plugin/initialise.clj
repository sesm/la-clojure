(ns plugin.initialise
  (:require plugin.formatting
            plugin.typing
            plugin.annotator
            plugin.repl
            plugin.resolve.core
            plugin.resolve.lists
            plugin.resolve.files
            plugin.resolve.namespaces
            plugin.documentation))

(defn initialise-all []
  (plugin.formatting/initialise)
  (plugin.typing/initialise)
  (plugin.annotator/initialise)
  (plugin.repl/initialise)
  (plugin.documentation/initialise))
