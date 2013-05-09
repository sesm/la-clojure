(ns plugin.tests.formattingtests
  (:use [clojure.test :only [deftest is]])
  (:require [plugin.test :as test]
            [clojure.string :as string])
  (:import [com.intellij.openapi.actionSystem IdeActions]))

(deftest application-tests
  (is (reformat-result?
        (test/lines "(defn f []"
                    ")")
        (test/lines "(defn f []"
                    "  )")))
  (is (reformat-result?
        (test/lines "(defn f"
                    "[]"
                    ")")
        (test/lines "(defn f"
                    "      []"
                    "  )")))
  (is (reformat-result?
        (test/lines "(defn f"
                    "[]"
                    ")")
        (test/lines "(defn f"
                    "      []"
                    "  )"))))

(deftest application-enter-tests
  (is (editor-action-result?
        IdeActions/ACTION_EDITOR_ENTER
        (test/lines "(defn f []<caret>)")
        (test/lines "(defn f []"
                    "  <caret>)")))
  (is (editor-action-result?
        IdeActions/ACTION_EDITOR_ENTER
        (test/lines "(defn f <caret>[])")
        (test/lines "(defn f "
                    "      <caret>[])"))))

(deftest application-tab-tests
  (is (editor-action-result?
        "EmacsStyleIndent"
        (test/lines "(defn f []"
                    "<caret>)")
        (test/lines "(defn f []"
                    "  <caret>)")))
  (is (editor-action-result?
        "EmacsStyleIndent"
        (test/lines "(defn f "
                    "<caret>[])")
        (test/lines "(defn f "
                    "      <caret>[])"))))

(deftest list-tests
  (is (reformat-result?
        (test/lines "[a"
                    "b]")
        (test/lines "[a"
                    " b]"))))

(deftest list-enter-tests
  (is (editor-action-result?
        IdeActions/ACTION_EDITOR_ENTER
        (test/lines "[a <caret>b]")
        (test/lines "[a "
                    " <caret>b]"))))

(deftest list-tab-tests
  (is (editor-action-result?
        "EmacsStyleIndent"
        (test/lines "[a "
                    "<caret>b]")
        (test/lines "[a "
                    " <caret>b]"))))

(deftest formatting-bugs
  (is (reformat-result?
        (test/lines "{#'*ns* (create-ns 'user)}")
        (test/lines "{#'*ns* (create-ns 'user)}"))))
