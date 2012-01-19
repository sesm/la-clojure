(ns plugin.testcase.typing
  (:gen-class
    :prefix test-
    :state state
    :init init
    :extends org.jetbrains.plugins.clojure.EditorTypingTestCase
    :constructors {[clojure.lang.IFn] []}
    :methods [[testImpl [] void]
              [doTest [] void]]))

(defn test-init [test-fn]
  [[] (atom {:test test-fn})])

(defn test-getTestName [this _]
  "testImpl")

(defn test-testImpl [this]
  ((@(.state this) :test) this))

(defn test-doTest [this]
  (.setName this "testImpl")
  (.runBare this))
