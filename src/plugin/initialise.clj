(ns plugin.initialise
  (:require plugin.formatting
            plugin.typing
            plugin.annotator
            plugin.repl.ide
            plugin.resolve.core
            plugin.resolve.lists
            plugin.resolve.files
            plugin.resolve.namespaces
            plugin.documentation
            plugin.actions.paredit
            plugin.repl.actions))

(defn initialise-all []
  (plugin.formatting/initialise)
  (plugin.typing/initialise)
  (plugin.annotator/initialise)
  (plugin.repl.actions/initialise)
  (plugin.repl.ide/initialise)
  (plugin.documentation/initialise)
  (plugin.actions.paredit/initialise))
