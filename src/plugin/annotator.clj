(ns plugin.annotator
  (:import (com.intellij.lang.annotation Annotator AnnotationHolder)
           (com.intellij.openapi.diagnostic Logger)
           (org.jetbrains.plugins.clojure.psi.api ClList ClojureFile)
           (org.jetbrains.plugins.clojure.psi.api.symbols ClSymbol)
           (com.intellij.openapi.editor.colors CodeInsightColors)
           (com.intellij.psi PsiClass PsiElement PsiFile)
           (org.jetbrains.plugins.clojure.psi.resolve ClojureResolveResult)
           (org.jetbrains.plugins.clojure.highlighter ClojureSyntaxHighlighter)
           (com.intellij.codeInsight.intention IntentionAction)
           (com.intellij.psi.search PsiShortNamesCache)
           (com.intellij.codeInsight.daemon.impl.quickfix ImportClassFixBase)
           (com.intellij.lang LanguageAnnotators)
           (org.jetbrains.plugins.clojure ClojureLanguage))
  (:use [plugin.util :only [with-logging]]))

;(set! *warn-on-reflection* true)

(def ^Logger logger (Logger/getInstance "plugin.annotator"))

(def implicit-names #{"def" "new" "throw" "ns" "in-ns" "if" "do" "let"
                      "quote" "var" "fn" "loop" "recur" "try"
                      "monitor-enter" "monitor-exit" "." "set!"})

(defn annotate-list [^ClList element ^AnnotationHolder holder]
  (let [first (.getFirstSymbol element)]
    (if (not (nil? first))
      (do
        (if (or (< 0 (alength (.multiResolve first false)))
                (implicit-names (.getHeadText element)))
          (let [annotation (.createInfoAnnotation holder first nil)]
            (.setTextAttributes annotation ClojureSyntaxHighlighter/DEF)))))))

(defn resolves-to? [^ClojureResolveResult result type]
  (instance? type (.getElement result)))

(defn annotate-import [^ClSymbol element ^AnnotationHolder holder]
  (if (not (.isQualified element))
    (let [cache (PsiShortNamesCache/getInstance (.getProject element))
          scope (.getResolveScope element)
          name (.getReferenceName element)
          classes (if-not (nil? name) (.getClassesByName cache name scope))]
      (if (and (not (nil? classes))
               (< 0 (alength classes)))
        (let [annotation (.createInfoAnnotation holder
                                                element
                                                (str (.getText element) " can be imported"))]
          (.setTextAttributes annotation CodeInsightColors/WARNINGS_ATTRIBUTES)
          (.registerFix annotation
                        (proxy [ImportClassFixBase] [element]
                          (getReferenceName [^ClSymbol reference]
                            (.getReferenceName reference))
                          (getReferenceNameElement [^ClSymbol reference]
                            (.getReferenceNameElement reference))
                          (hasTypeParameters [reference] false)
                          (getQualifiedName [^ClSymbol reference]
                            (.getText reference))
                          (isQualified [^ClSymbol reference]
                            (.isQualified reference))
                          (hasUnresolvedImportWhichCanImport [file name] false)
                          (isAccessible [class reference] true)))
          true)))))

(defn annotate-unresolved [^ClSymbol element ^AnnotationHolder holder]
  (if-not (annotate-import element holder)
    (let [annotation (.createInfoAnnotation holder
                                            element
                                            (str (.getText element) " cannot be resolved"))]
      (.setTextAttributes annotation CodeInsightColors/WEAK_WARNING_ATTRIBUTES))))

(defn process-element [^PsiElement element pred action]
  (if (pred element)
    (action element))
  (doseq [child (seq (.getChildren element))]
    (process-element child pred action)))

(defn import-fully-qualified [project editor ^ClojureFile psi-file ^ClSymbol element target]
  (let [ns (.findOrCreateNamespaceElement psi-file)
        element-text (.getText element)]
    (.addImportForClass ns element target)
    (process-element (.getContainingFile element)
                     #(and (instance? ClSymbol %)
                           (= element-text (.getText ^ClSymbol %)))
                     #(let [qualifier (.getQualifierSymbol ^ClSymbol %)
                            separator (.getSeparatorToken ^ClSymbol %)]
                        (.delete qualifier)
                        (.delete separator)))))

(defn annotate-fqn [^ClSymbol element target ^AnnotationHolder holder]
  (let [annotation (.createInfoAnnotation holder
                                          element
                                          (str (.getText element) " is fully qualified"))]
    (.setTextAttributes annotation CodeInsightColors/WARNINGS_ATTRIBUTES)
    (.registerFix annotation
                  (reify IntentionAction
                    (getText [this] "Import Class")
                    (getFamilyName [this] (.getText this))
                    (isAvailable [this project editor psi-file] true)
                    (invoke [this project editor psi-file]
                      (import-fully-qualified project editor psi-file element target))
                    (startInWriteAction [this] true)))))

(defn annotate-symbol [^ClSymbol element ^AnnotationHolder holder]
  (let [result (.multiResolve element false)]
    (cond
      (and (not (implicit-names (.getText element)))
           (= 0 (alength result))) (annotate-unresolved element holder)
      (.isQualified element) (if-let [target ^ClojureResolveResult (first (filter #(resolves-to? % PsiClass) (seq result)))]
                               (annotate-fqn element (.getElement target) holder)))))

(defn annotate [element holder]
  (cond
    (instance? ClList element) (annotate-list element holder)
    (instance? ClSymbol element) (annotate-symbol element holder)))

(defn initialise []
  (.addExplicitExtension
    LanguageAnnotators/INSTANCE
    (ClojureLanguage/getInstance)
    (reify Annotator
      (annotate [this element holder]
        (with-logging
          (annotate element holder))))))
