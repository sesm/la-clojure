(ns plugin.testcase.PsiFile
  (:gen-class :prefix test-
              :state state
              :init init
              :extends com.intellij.testFramework.PsiTestCase
              :constructors {[clojure.lang.IFn] []}
              :methods [[testImpl [] void]
                        [doTest [] void]]
              :exposes-methods {createFile superCreateFile})
  (:import [org.jetbrains.plugins.clojure EditorModificationTestCase]
           (com.intellij.ide DataManager)))

(defn test-init [test-fn]
  [[] (atom {:test test-fn})])

(defn test-getTestName [this _]
  "testImpl")

(defn test-testImpl [this]
  ((@(.state this) :test) this {:project     (.getProject this),
                                :create-file (fn [file-name text]
                                               (.superCreateFile this file-name text))}))

(defn test-doTest [this]
  (.setName this "testImpl")
  (.runBare this))
