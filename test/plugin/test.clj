(ns plugin.test
  (:import (com.intellij.testFramework LightPlatformCodeInsightTestCase)
           (com.intellij.openapi.actionSystem ActionManager AnActionEvent)
           (com.intellij.ide DataManager)
           (com.intellij.psi PsiDocumentManager PsiReference PsiPolyVariantReference PsiNamedElement)
           (com.intellij.openapi.editor.actionSystem EditorActionManager)
           (com.intellij.psi.codeStyle CodeStyleManager)
           (junit.framework Assert AssertionFailedError)
           (org.jetbrains.plugins.clojure.psi.api.symbols ClSymbol)
           (org.jetbrains.plugins.clojure.psi.api ClojureFile)
           (com.intellij.psi.stubs StubTree)
           (com.intellij.testFramework.fixtures CodeInsightTestFixture IdeaTestFixtureFactory)
           (com.intellij.testFramework.fixtures.impl LightTempDirTestFixtureImpl)
           (org.apache.log4j BasicConfigurator)
           (org.jetbrains.plugins.clojure.psi.impl.ns NamespaceUtil))
  (:refer-clojure :exclude [type])
  (:use [clojure.test :only [assert-expr do-report]])
  (:require [clojure.string :as str]
            [plugin.util :as util]))

(defn lines [& strings]
  (str/join "\n" strings))

(def ^:dynamic ^CodeInsightTestFixture *fixture* nil)

(defn create-file [filename text]
  (.configureByText *fixture* filename text))

(defn add-file [relative-path text]
  (.addFileToProject *fixture* relative-path text))

(defn project []
  (.getProject *fixture*))

(defn editor []
  (.getEditor *fixture*))

(defn file []
  (.getFile *fixture*))

(defn editor-text []
  (let [document (.getDocument (editor))]
    (.getText document)))

(defn file-text []
  (let [psi-file (.getFile *fixture*)]
    (.getText psi-file)))

(defn type [c]
  (.type *fixture* c))

(defn check-result [text strip-trailing-spaces?]
  (.checkResult *fixture* text strip-trailing-spaces?))

(defn invoke-editor-action [id]
  (let [fixture *fixture*]
    (util/invoke-and-wait
      (.performEditorAction fixture id))))

(defn light-idea-fixture [f]
  (BasicConfigurator/configure)
  (let [factory (IdeaTestFixtureFactory/getFixtureFactory)
        builder (.createLightFixtureBuilder factory)
        fixture (.getFixture builder)
        tmp-dir-fixture (LightTempDirTestFixtureImpl. true)
        test-fixture (.createCodeInsightFixture factory fixture tmp-dir-fixture)]
    (.setUp test-fixture)
    (try
      (binding [*fixture* test-fixture]
        (f))
      (finally
        (.tearDown test-fixture)))))

(defmacro with-tmp-files [& body]
  `(try
     ~@body
     (finally
       (let [tmp-dir-fixture# (.getTempDirFixture *fixture*)]
         (util/invoke-and-wait
           (.deleteAll ^LightTempDirTestFixtureImpl tmp-dir-fixture#))))))

(defmacro with-unique-default-definitions [& body]
  `(try
     ~@body
     (finally
       (.putUserData (project) NamespaceUtil/DEFAULT_DEFINITIONS_KEY nil))))

(defn fail [& messages]
  (throw (AssertionFailedError. (apply str messages))))

(defn assert= [expected actual message]
  (Assert/assertEquals message expected actual))
