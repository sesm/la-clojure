(ns plugin.tests.editoractiontests
  (:import (junit.framework AssertionFailedError))
  (:use [clojure.test :only [deftest is assert-expr do-report use-fixtures]])
  (:require [plugin.test :as test]))

(defmethod assert-expr 'editor-action-result? [msg form]
  `(let [action# ~(nth form 1)
         before# ~(nth form 2)
         after# ~(nth form 3)]
     (try
       (test/create-file "editor-action-test.clj" before#)
       (test/invoke-editor-action action#)
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

;(deftest barf-backwards-tests
;  (is (editor-action-result? "org.jetbrains.plugins.clojure.actions.editor.BarfBackwardsAction"
;                             "(a (b<caret> c) d e)"
;                             "(a b (c) d e)")))
;
;(deftest barf-forwards-tests
;  (is (editor-action-result? "org.jetbrains.plugins.clojure.actions.editor.BarfForwardsAction"
;                             "(a (b<caret> c) d e)"
;                             "(a (b) c d e)")))

(deftest slurp-backwards-tests
  (is (editor-action-result? ":plugin.actions.paredit/slurp-backwards"
                             "(foo bar (baz<caret> quux) zot)"
                             "(foo (bar baz<caret> quux) zot)"))
  (is (editor-action-result? ":plugin.actions.paredit/slurp-backwards"
                             "(a b ((c<caret> d)) e f)"
                             "(a (b (c<caret> d)) e f)"))
  (is (editor-action-result? ":plugin.actions.paredit/slurp-backwards"
                             (test/lines "(a"
                                         " (<caret>b c))")
                             (test/lines "((a"
                                         "   <caret>b c))"))))

(deftest slurp-forwards-tests
  (is (editor-action-result? ":plugin.actions.paredit/slurp-forwards"
                             "(foo (bar <caret>baz) quux zot)"
                             "(foo (bar <caret>baz quux) zot)"))
  (is (editor-action-result? ":plugin.actions.paredit/slurp-forwards"
                             "(a b ((c<caret> d)) e f)"
                             "(a b ((c<caret> d) e) f)"))
  (is (editor-action-result? ":plugin.actions.paredit/slurp-forwards"
                             (test/lines "((a b<caret>)"
                                         " c)")
                             (test/lines "((a b<caret>"
                                         "    c))"))))

(deftest splice-tests
  (is (editor-action-result? ":plugin.actions.paredit/splice"
                             "(a (b c <caret>d) e)"
                             "(a b c <caret>d e)"))
  (is (editor-action-result? ":plugin.actions.paredit/splice"
                             "(abc (def <caret>ghi))"
                             "(abc def <caret>ghi)"))
  (is (editor-action-result? ":plugin.actions.paredit/splice"
                             "((abc <caret>def) ghi)"
                             "(abc <caret>def ghi)"))
  (is (editor-action-result? ":plugin.actions.paredit/splice"
                             "((abc <caret>def ghi))"
                             "(abc <caret>def ghi)")))
