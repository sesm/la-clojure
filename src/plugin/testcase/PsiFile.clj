(ns plugin.testcase.PsiFile
  (:gen-class :prefix test-
              :state state
              :init init
              :extends com.intellij.testFramework.PsiTestCase
              :constructors {[clojure.lang.IFn] []}
              :methods [[testImpl [] void]
                        [doTest [] void]]
              :exposes-methods {createFile superCreateFile})
  (:import [com.intellij.testFramework PsiTestCase]
           (java.io File)
           (java.util.zip ZipFile)))

(defn test-init [test-fn]
  [[] (atom {:test test-fn})])

(defn test-getTestName [this _]
  "testImpl")

(def load-clojure-core
     (memoize
       (fn []
         (let [lib-dir (File. (str (System/getProperty "plugin.path") "/lib"))
               clojure-lib-name (first (filter #(.startsWith % "clojure-1.")
                                          (seq (.list lib-dir))))
               clojure-lib (ZipFile. (File. lib-dir clojure-lib-name))
               core-file-entry (.getEntry clojure-lib "clojure/core.clj")]
           (with-open [stream (.getInputStream clojure-lib core-file-entry)]
             (slurp stream))))))

(defn test-testImpl [this]
  ((@(.state this) :test) this {:project      (.getProject this),
                                :create-file  (fn [file-name text]
                                                (.superCreateFile this file-name text))
                                :clojure-core load-clojure-core}))

(defn test-doTest [this]
  (.setName this "testImpl")
  (.runBare this))
