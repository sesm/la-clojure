(ns plugin.tests.wordsscanner
  (:import (org.jetbrains.plugins.clojure.lexer ClojureFlexLexer ClojureTokenTypes)
           (org.jetbrains.plugins.clojure.findUsages ClojureWordsScanner)
           (com.intellij.util Processor)
           (com.intellij.lang.cacheBuilder WordOccurrence))
  (:use [clojure.test :only [deftest is]]))

(defn get-tokens [text]
  (let [lexer (ClojureFlexLexer.)
        scanner (ClojureWordsScanner. lexer
                                      ClojureTokenTypes/IDENTIFIERS
                                      ClojureTokenTypes/COMMENTS
                                      ClojureTokenTypes/STRINGS)
        result (atom [])
        processor (reify Processor
                    (process [this item]
                      (swap! result conj (.subSequence (.getBaseText item)
                                                       (.getStart item)
                                                       (.getEnd item)))
                      true))]
    (.processWords scanner text processor)
    @result))

(deftest symbol-scanner-tests
  (is (= (get-tokens "a") ["a"]))
  (is (= (get-tokens "get-item") ["get" "item" "get-item"]))
  (is (= (get-tokens "get-item?") ["get" "item" "get-item?"]))
  (is (= (get-tokens "->") ["->"]))
  (is (= (get-tokens ".method") ["method" ".method"]))
  (is (= (get-tokens "Constructor.") ["Constructor" "Constructor."])))

(deftest comment-scanner-tests
  (is (= (get-tokens "; a") ["a"]))
  (is (= (get-tokens "; get-item") ["get" "item" "get-item"]))
  (is (= (get-tokens "; get-item?") ["get" "item" "get-item?"]))
  (is (= (get-tokens "; ->") ["->"]))
  (is (= (get-tokens "; .method") ["method" ".method"]))
  (is (= (get-tokens "; Constructor.") ["Constructor" "Constructor."])))

(deftest string-scanner-tests
  (is (= (get-tokens "\"a\"") ["a"]))
  (is (= (get-tokens "\"get-item\"") ["get" "item" "get-item"]))
  (is (= (get-tokens "\"get-item?\"") ["get" "item" "get-item?"]))
  (is (= (get-tokens "\"->\"") ["->"]))
  (is (= (get-tokens "\".method\"") ["method" ".method"]))
  (is (= (get-tokens "\"Constructor.\"") ["Constructor" "Constructor."])))
