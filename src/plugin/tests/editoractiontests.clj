(ns plugin.tests.editoractiontests
  (:use [clojure.test :only [deftest is]])
  (:require plugin.test))

(deftest barf-backwards-tests
         (is (editor-action-result? "org.jetbrains.plugins.clojure.actions.editor.BarfBackwardsAction"
                                    "(a (b<caret> c) d e)"
                                    "(a b (c) d e)")))

(deftest barf-forwards-tests
         (is (editor-action-result? "org.jetbrains.plugins.clojure.actions.editor.BarfForwardsAction"
                                    "(a (b<caret> c) d e)"
                                    "(a (b) c d e)")))

(deftest slurp-backwards-tests
         (is (editor-action-result? "org.jetbrains.plugins.clojure.actions.editor.SlurpBackwardsAction"
                                    "(a b (<caret>c d) e)"
                                    "(a (b c d) e)")))

(deftest slurp-forwards-tests
         (is (editor-action-result? "org.jetbrains.plugins.clojure.actions.editor.SlurpForwardsAction"
                                    "(a (b<caret> c) d e)"
                                    "(a (b c d) e)")))

(deftest splice-tests
         (is (editor-action-result? "org.jetbrains.plugins.clojure.actions.editor.SpliceAction"
                                    "(a (b c <caret>d) e)"
                                    "(a b c d e)")))
