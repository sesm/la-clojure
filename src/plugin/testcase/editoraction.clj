(ns plugin.testcase.editoraction
  (:gen-class
    :prefix test-
    :state state
    :init init
    :extends com.intellij.testFramework.EditorActionTestCase
    :constructors {[String clojure.lang.IFn] []}
    :methods [[testImpl [] void]
              [doTest [] void]]))

(defn test-init [action test-fn]
  [[] (atom {:action action, :test test-fn})])

(defn test-getActionId [this]
  (@(.state this) :action))

(defn test-getTestName [this _]
  "testImpl")

(defn test-testImpl [this]
  ((@(.state this) :test) this))

(defn test-doTest [this]
  (.setName this "testImpl")
  (.runBare this))
