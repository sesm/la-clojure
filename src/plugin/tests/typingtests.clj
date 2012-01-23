(ns plugin.tests.typingtests
  (:use [clojure.test :only [deftest is]])
  (:require plugin.test))

(deftest open-paren-tests
  (is (typing-result? \( "<caret>" "(<caret>)"))
  (is (typing-result? \( "(a b <caret>c d)" "(a b (<caret>) c d)"))
  (is (typing-result? \( "(a b<caret> c d)" "(a b (<caret>) c d)"))
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
