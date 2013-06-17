(ns plugin.annotator
  (:import (com.intellij.lang.annotation Annotator AnnotationHolder)
           (org.jetbrains.plugins.clojure.psi.api ClList ClojureFile ClVector ClMetadata ClLiteral ClQuotedForm
                                                  ClKeyword)
           (org.jetbrains.plugins.clojure.psi.api.symbols ClSymbol)
           (com.intellij.openapi.editor.colors CodeInsightColors)
           (com.intellij.psi PsiClass PsiElement PsiFile PsiWhiteSpace PsiComment ResolveResult PsiManager PsiReference)
           (org.jetbrains.plugins.clojure.psi.resolve ClojureResolveResult)
           (org.jetbrains.plugins.clojure.highlighter ClojureSyntaxHighlighter)
           (com.intellij.codeInsight.intention IntentionAction)
           (com.intellij.psi.search PsiShortNamesCache)
           (com.intellij.codeInsight.daemon.impl.quickfix ImportClassFixBase)
           (com.intellij.lang LanguageAnnotators)
           (org.jetbrains.plugins.clojure ClojureLanguage ClojureBundle)
           (org.jetbrains.plugins.clojure.psi.api.defs ClDef)
           (org.jetbrains.plugins.clojure.parser ClojureSpecialFormTokens)
           (com.intellij.psi.util PsiTreeUtil)
           (com.intellij.psi.impl.source.tree LeafPsiElement)
           (com.intellij.codeInsight.daemon.impl.actions AddImportAction)
           (com.intellij.codeInsight.daemon DaemonCodeAnalyzer)
           (com.intellij.codeInspection ProblemHighlightType))
  (:require [plugin.psi :as psi]
            [plugin.util :as util]
            [plugin.logging :as log]
            [plugin.resolve.lists :as lists]
            [plugin.intellij.extension :as extension]))

;(set! *warn-on-reflection* true)

(def implicit-names #{"def" "new" "throw" "ns" "in-ns" "if" "do" "let"
                      "quote" "var" "fn" "loop" "recur" "try" "catch" "finally"
                      "monitor-enter" "monitor-exit" "." ".." "set!"
                      "%" "%1" "%2" "%3" "%4" "%5" "%6" "%7" "%8" "%9" "%&" "&"})

(def local-bindings #{"let", "with-open", "with-local-vars", "when-let",
                      "when-first", "for", "if-let", "loop", "doseq"})

(def instantiators #{"proxy" "reify" "definterface" "deftype" "defrecord"})

(def defn-names #{"defn" "defn-" "definline" "defmacro"})

(defn binding-resolves? [element binding]
  (if (instance? ClSymbol binding)
    (= element binding)
    (= element (:element binding))))

(defn should-resolve? [element]
  (nil? (some (fn [list]
                (some (fn [[scope bindings]]
                        (if-let [binding (bindings (name element))]
                          (if (vector? binding)
                            (some #(binding-resolves? element %) binding)
                            (binding-resolves? element binding))))
                      (lists/get-resolve-symbols list)))
              (filter #(instance? ClList %) (psi/ancestors element)))))

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
          ref-name (.getReferenceName element)
          name (cond
                 (nil? ref-name) nil
                 (.endsWith ref-name ".") (.substring ref-name 0 (dec (.length ref-name)))
                 :else ref-name)
          classes (if-not (nil? name) (.getClassesByName cache name scope))]
      (if (and (not (nil? classes))
               (< 0 (alength classes)))
        (let [annotation (.createInfoAnnotation holder
                                                element
                                                (str (.getText element) " can be imported"))]
          (.setTextAttributes annotation CodeInsightColors/WARNINGS_ATTRIBUTES)
          (.registerFix annotation
                        (proxy [ImportClassFixBase] [element element]
                          (getReferenceName [^ClSymbol reference]
                            (if-let [ref-name (.getReferenceName element)]
                              (if (.endsWith ref-name ".")
                                (.substring ref-name 0 (dec (.length ref-name)))
                                ref-name)))
                          (getReferenceNameElement [^ClSymbol reference]
                            (.getReferenceNameElement reference))
                          (hasTypeParameters [reference] false)
                          (getQualifiedName [^ClSymbol reference]
                            (.getText reference))
                          (isQualified [^ClSymbol reference]
                            (.isQualified reference))
                          (hasUnresolvedImportWhichCanImport [file name] false)
                          (isAccessible [class reference] true)
                          (createAddImportAction [classes project editor]
                            (proxy [AddImportAction] [project element editor classes]
                              (bindReference [^PsiReference ref targetClass]
                                (.bindToElement ref targetClass)
                                (.dropResolveCaches (.getManager element))
                                (.restart (DaemonCodeAnalyzer/getInstance project)
                                          (.getContainingFile element)))))))
          true)))))

(defn annotate-unresolved [^ClSymbol element ^AnnotationHolder holder]
  (if-not (annotate-import element holder)
    (let [annotation (.createInfoAnnotation holder
                                            element
                                            (str (.getText element) " cannot be resolved"))]
      (.setTextAttributes annotation CodeInsightColors/WARNINGS_ATTRIBUTES))))

(defn annotate-selfresolve [^ClSymbol element ^AnnotationHolder holder]
  (let [annotation (.createInfoAnnotation holder
                                          element
                                          (str (.getText element) " resolves to itself"))]
    (.setTextAttributes annotation CodeInsightColors/ERRORS_ATTRIBUTES)))

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
    (.setTextAttributes annotation CodeInsightColors/WEAK_WARNING_ATTRIBUTES)
    (.registerFix annotation
                  (reify IntentionAction
                    (getText [this] "Import Class")
                    (getFamilyName [this] (.getText this))
                    (isAvailable [this project editor psi-file] true)
                    (invoke [this project editor psi-file]
                      (import-fully-qualified project editor psi-file element target))
                    (startInWriteAction [this] true)))))

(defn has-ns-ancestor [^PsiElement element]
  (if (nil? element)
    false
    (if (instance? ClList element)
      (let [head-text (.getHeadText ^ClList element)]
        (if (= "ns" head-text)
          true
          (recur (.getContext element))))
      (recur (.getContext element)))))

(defn annotate-symbol [^ClSymbol element ^AnnotationHolder holder]
  (let [result (.multiResolve element false)]
    (cond
      (and (= 0 (alength result))
           (not (implicit-names (.getText element)))
           (should-resolve? element)) (annotate-unresolved element holder)
      (and (.isQualified element)
           (not (has-ns-ancestor element))) (if-let [target ^ClojureResolveResult (first (filter #(resolves-to? % PsiClass) (seq result)))]
                                              (annotate-fqn element (.getElement target) holder))
      (some #(= element (.getElement ^ResolveResult %)) (seq result)) (annotate-selfresolve element holder))))

(defn check-keyword-text-consistency [^ClKeyword element ^AnnotationHolder holder]
  (let [text (.getText element)
        index (.lastIndexOf text "/")]
    (if (or (and (<= 0 index)
                 (= \: (.charAt text (dec index))))
            (.endsWith text "::")
            (-> (.substring text 1)
                (.contains "::")))
      (let [annotation (.createErrorAnnotation holder element (ClojureBundle/message "invalid.token" (object-array [text])))]
        (.setHighlightType annotation ProblemHighlightType/GENERIC_ERROR_OR_WARNING)))))

(defprotocol Annotatable
  (annotate [this holder]))

(extend-protocol Annotatable
  ClList
  (annotate [this holder]
    (annotate-list this holder))
  ClSymbol
  (annotate [this holder]
    (annotate-symbol this holder))
  ClKeyword
  (annotate [this holder]
    (check-keyword-text-consistency this holder))
  PsiElement
  (annotate [this holder]))

(defn initialise []
  (extension/register LanguageAnnotators/INSTANCE
                      (reify Annotator
                        (annotate [this element holder]
                          (log/with-logging
                            (annotate element holder))))))
