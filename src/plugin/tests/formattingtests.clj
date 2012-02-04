(ns plugin.tests.formattingtests
  (:use [clojure.test :only [deftest is]])
  (:require plugin.test
            [clojure.string :as string])
  (:import [com.intellij.openapi.actionSystem IdeActions]))

(defn lines [& strings]
  (string/join "\n" strings))

(deftest application-tests
  (is (reformat-result?
        (lines "(defn f []"
               ")")
        (lines "(defn f []"
               "  )")))
  (is (reformat-result?
        (lines "(defn f"
               "[]"
               ")")
        (lines "(defn f"
               "      []"
               "  )")))
  (is (reformat-result?
        (lines "(defn f"
               "[]"
               ")")
        (lines "(defn f"
               "      []"
               "  )"))))

(deftest application-enter-tests
  (is (editor-action-result?
        IdeActions/ACTION_EDITOR_ENTER
        (lines "(defn f []<caret>)")
        (lines "(defn f []"
               "  <caret>)")))
  (is (editor-action-result?
        IdeActions/ACTION_EDITOR_ENTER
        (lines "(defn f <caret>[])")
        (lines "(defn f "
               "      <caret>[])"))))

(deftest application-tab-tests
  (is (editor-action-result?
        "EmacsStyleIndent"
        (lines "(defn f []"
               "<caret>)")
        (lines "(defn f []"
               "  <caret>)")))
  (is (editor-action-result?
        "EmacsStyleIndent"
        (lines "(defn f "
               "<caret>[])")
        (lines "(defn f "
               "      <caret>[])"))))

(deftest list-tests
  (is (reformat-result?
        (lines "[a"
               "b]")
        (lines "[a"
               " b]"))))

(deftest list-enter-tests
  (is (editor-action-result?
        IdeActions/ACTION_EDITOR_ENTER
        (lines "[a <caret>b]")
        (lines "[a "
               " <caret>b]"))))

(deftest list-tab-tests
  (is (editor-action-result?
        "EmacsStyleIndent"
        (lines "[a "
               "<caret>b]")
        (lines "[a "
               " <caret>b]"))))
