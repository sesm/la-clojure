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
           (com.intellij.openapi.vfs JarFileSystem LocalFileSystem VirtualFile)))

(defn test-init [test-fn]
  [[] (atom {:test test-fn})])

(defn test-getTestName [this _]
  "testImpl")

(def load-clojure-core
     (memoize
       (fn []
         (let [lib-dir (.findFileByPath (LocalFileSystem/getInstance)
                                        (str (System/getProperty "plugin.path") "/lib"))
               clojure-lib (first (filter #(.startsWith (.getName ^VirtualFile %) "clojure-")
                                          (seq (.getChildren lib-dir))))
               clojure-fs (.getJarRootForLocalFile (JarFileSystem/getInstance) clojure-lib)
               core-file (.findFileByRelativePath clojure-fs "clojure/core.clj")]
           (String. (.contentsToByteArray core-file)
                    (.getCharset core-file))))))

(defn test-testImpl [this]
  ((@(.state this) :test) this {:project      (.getProject this),
                                :create-file  (fn [file-name text]
                                                (.superCreateFile this file-name text))
                                :clojure-core load-clojure-core}))

(defn test-doTest [this]
  (.setName this "testImpl")
  (.runBare this))
