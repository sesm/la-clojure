(ns plugin.testcase.EditorModification
  (:gen-class :prefix test-
              :state state
              :init init
              :extends org.jetbrains.plugins.clojure.EditorModificationTestCase
              :constructors {[clojure.lang.IFn clojure.lang.IFn] []}
              :methods [[testImpl [] void]
                        [doTest [] void]])
  (:import [org.jetbrains.plugins.clojure EditorModificationTestCase]
           (com.intellij.ide DataManager)))

(defn test-init [modification-fn test-fn]
  [[] (atom {:modification modification-fn, :test test-fn})])

(defn test-getTestName [this _]
  "testImpl")

(defn test-doModification [this project editor psi-file data-context]
  ((@(.state this) :modification) this {:project      project,
                                        :editor       editor,
                                        :data-context data-context,
                                        :psi-file     psi-file}))

(defn test-testImpl [this]
  ((@(.state this) :test) this))

(defn test-doTest [this]
  (.setName this "testImpl")
  (.runBare this))
