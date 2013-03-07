(ns plugin.annotator
  (:import (com.intellij.lang.annotation Annotator AnnotationHolder)
           (com.intellij.openapi.diagnostic Logger)
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
            [plugin.util :as util]))

;(set! *warn-on-reflection* true)

(def ^Logger logger (Logger/getInstance "plugin.annotator"))

(def implicit-names #{"def" "new" "throw" "ns" "in-ns" "if" "do" "let"
                      "quote" "var" "fn" "loop" "recur" "try" "catch" "finally"
                      "monitor-enter" "monitor-exit" "." ".." "set!"
                      "%" "%1" "%2" "%3" "%4" "%5" "%6" "%7" "%8" "%9" "%&" "&"})

(def local-bindings #{"let", "with-open", "with-local-vars", "when-let",
                      "when-first", "for", "if-let", "loop", "doseq"})

(def instantiators #{"proxy" "reify" "definterface" "deftype" "defrecord"})

(def defn-names #{"defn" "defn-" "definline" "defmacro"})

(defn impl-method?
  "Checks to see if an element is a method implementation for proxy et al"
  [^PsiElement element]
  (if-let [parent (.getParent element)]
    (and (instance? ClList element)
         (instance? ClList parent)
         (instantiators (.getHeadText ^ClList parent)))
    false))

(defn ancestor?
  ([ancestor element]
   (ancestor? ancestor element true))
  ([ancestor element strict]
   (PsiTreeUtil/isAncestor ancestor element strict)))

(defn find-context-ancestor [^PsiElement element pred strict]
  (if-not (nil? element)
    (loop [current (if strict (.getContext element) element)]
      (if-not (nil? current)
        (if (pred current)
          current
          (recur (.getContext current)))))))

(defn local-def? [^PsiElement element]
  (if-let [let-block (find-context-ancestor element
                                            (fn [element]
                                              (and (instance? ClList element)
                                                   (local-bindings (.getHeadText ^ClList element))))
                                            true)]
    (let [params (second (psi/significant-children let-block))
          definitions (take-nth 2 (psi/significant-children params))]
      (some #(ancestor? % element) definitions))))

(defn let-binding? [^ClSymbol element]
  (loop [current element
         parent (.getParent element)
         grand-parent (util/safely (.getParent parent))]
    (cond
      (nil? grand-parent) false
      (and (instance? ClList grand-parent)
           (instance? ClVector parent)
           (local-bindings (.getHeadText ^ClList grand-parent))
           (even? (psi/significant-offset current))) true
      :else (recur parent grand-parent (.getParent grand-parent)))))

; TODO duplicated from lists.clj
(defn third [coll]
  (first (rest (rest coll))))

(defn fourth [coll]
  (first (rest (rest (rest coll)))))

(defn fn-arg? [^ClSymbol element]
  (loop [current element
         parent (.getParent element)
         grandparent (util/safely (.getParent parent))]
    (cond
      (nil? grandparent) false
      (and (instance? ClList grandparent)
           (instance? ClVector parent)
           (let [head-text (.getHeadText ^ClList grandparent)
                 offset (psi/significant-offset parent)
                 children (psi/significant-children grandparent)]
             (or (and (= "fn" head-text)
                      (= (if (instance? ClSymbol (second children)) 2 1) offset))
                 (and (defn-names head-text)
                      (= (if (instance? ClLiteral (third children)) 3 2) offset))
                 (if-let [great-grandparent (.getParent grandparent)]
                   (and (instance? ClList great-grandparent)
                        (= 0 (psi/significant-offset parent))
                        (defn-names (.getHeadText ^ClList great-grandparent))))))) true
      :else (recur parent grandparent (.getParent grandparent)))))

(defn should-resolve? [^ClSymbol element]
  (let [parent (.getParent element)
        grandparent (util/safely (.getParent parent))]
    (cond
      ; names of def/defn etc
      (and (instance? ClDef parent)
           (= element (.getNameSymbol ^ClDef parent))) false
      ; let-bound variables
      (let-binding? element) false
      ; fn args
      (and (fn-arg? element)
           (not (instance? ClMetadata parent))) false
      ; parameters of implementation methods
      (and (instance? ClVector parent)
           (impl-method? grandparent)
           ;(= parent (.getSecondNonLeafElement grandparent)) TODO - some need third, eg proxy
           ) false
      (local-def? element) false
      (not (nil? (PsiTreeUtil/getParentOfType element ClQuotedForm))) false
      :else true)))

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
                        (proxy [ImportClassFixBase] [element]
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
      (let [annotation (.createErrorAnnotation holder element (ClojureBundle/message "invalid.token" text))]
        (.setHighlightType annotation ProblemHighlightType/GENERIC_ERROR_OR_WARNING)))))

(defn annotate [element holder]
  (cond
    (instance? ClList element) (annotate-list element holder)
    (instance? ClSymbol element) (annotate-symbol element holder)
    (instance? ClKeyword element) (check-keyword-text-consistency element holder)))

(defn initialise []
  (.addExplicitExtension
    LanguageAnnotators/INSTANCE
    (ClojureLanguage/getInstance)
    (reify Annotator
      (annotate [this element holder]
        (util/with-logging
          (annotate element holder))))))
