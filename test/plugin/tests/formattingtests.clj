(ns plugin.tests.formattingtests
  (:import [com.intellij.openapi.actionSystem IdeActions]
           (junit.framework AssertionFailedError)
           (com.intellij.psi.codeStyle CodeStyleManager))
  (:use [clojure.test :only [deftest is assert-expr do-report use-fixtures]])
  (:require [plugin.test :as test]
            [clojure.string :as string]
            [plugin.psi :as psi]
            [plugin.util :as util]
            plugin.tests.editoractiontests))

(defn reformat []
  (let [project (test/project)
        psi-file (test/file)]
    (util/invoke-and-wait
      (util/with-write-action
        (let [style-manager (CodeStyleManager/getInstance project)]
          (.reformatText style-manager
                         psi-file
                         (psi/start-offset psi-file)
                         (psi/end-offset psi-file)))))))

(defmethod assert-expr 'reformat-result? [msg form]
  `(let [before# ~(nth form 1)
         after# ~(nth form 2)]
     (try
       (test/create-file "formatting-test.clj" before#)
       (reformat)
       (test/check-result after# false)
       (do-report {:type     :pass,
                   :message  ~msg,
                   :expected '~form,
                   :actual   nil})
       (catch AssertionFailedError e#
         (do-report {:type     :fail,
                     :message  (str ~msg ": " (.getMessage e#)),
                     :expected '~form,
                     :actual   e#})
         e#))))


(use-fixtures :once test/light-idea-fixture)


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
