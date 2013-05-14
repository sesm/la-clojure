(ns plugin.tests.typingtests
  (:import (junit.framework AssertionFailedError)
           (com.intellij.openapi.editor.actionSystem EditorActionManager)
           (com.intellij.ide DataManager))
  (:use [clojure.test :only [deftest is assert-expr do-report use-fixtures]])
  (:require [plugin.test :as test]
            [plugin.psi :as psi]
            [plugin.util :as util]))

(defmethod assert-expr 'typing-result? [msg form]
  `(let [character# ~(nth form 1)
         before# ~(nth form 2)
         after# ~(nth form 3)]
     (try
       (test/create-file "typing-test.clj" before#)
       (test/type character#)
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

(deftest open-paren-tests
  (is (typing-result? \( "<caret>" "(<caret>)"))
  (is (typing-result? \( "(a b <caret>c d)" "(a b (<caret>) c d)"))
  (is (typing-result? \( "(a b<caret> c d)" "(a b (<caret>) c d)"))
  (is (typing-result? \( "(a b <caret><selection>c</selection> d)" "(a b (<caret>) d)"))
  (is (typing-result? \( "(a <selection>b</selection><caret> c d)" "(a (<caret>) c d)"))
  (is (typing-result? \( "(te<caret>st)" "(te (<caret>) st)"))
  (is (typing-result? \( "\"bar <caret>baz\"" "\"bar (<caret>baz\""))
  (is (typing-result? \( "; bar <caret>baz" "; bar (<caret>baz"))
  (is (typing-result? \[ "<caret>" "[<caret>]"))
  (is (typing-result? \{ "<caret>" "{<caret>}")))

(deftest close-paren-tests
  (is (typing-result? \) "(a b <caret>c   )" "(a b c)<caret>"))
  (is (typing-result? \) "(a b c)<caret>" "(a b c)<caret>"))
  (is (typing-result? \) "\"bar <caret>baz\"" "\"bar )<caret>baz\""))
  (is (typing-result? \) "; bar <caret>baz" "; bar )<caret>baz"))
  (is (typing-result? \) "(let [a <caret>b]   )" "(let [a b])<caret>"))
  (is (typing-result? \] "(let [a <caret>b   ])" "(let [a b]<caret>)"))
  (is (typing-result? \} "{:a <caret>:b   }" "{:a :b}<caret>")))

(deftest quote-tests
  (is (typing-result? \" "<caret>" "\"<caret>\""))
  (is (typing-result? \" "\"<caret> \"" "\"\\\" \""))
  (is (typing-result? \" "\"<caret>\"" "\"\"<caret>")))
