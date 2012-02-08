(ns plugin.annotator
  (:import (com.intellij.lang.annotation Annotator AnnotationHolder)
           (com.intellij.openapi.diagnostic Logger)
           (org.jetbrains.plugins.clojure.psi.api ClList)
           (org.jetbrains.plugins.clojure.psi.api.symbols ClSymbol)
           (com.intellij.openapi.editor.colors CodeInsightColors)
           (org.jetbrains.plugins.clojure.highlighter ClojureSyntaxHighlighter))
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
        (.info logger (.getText first))
        (if (or (< 0 (alength (.multiResolve first false)))
                (implicit-names (.getHeadText element)))
          (let [annotation (.createInfoAnnotation holder first nil)]
            (.setTextAttributes annotation ClojureSyntaxHighlighter/DEF)))))))

(defn annotate-symbol [^ClSymbol element ^AnnotationHolder holder]
  (if (and (not (implicit-names (.getText element)))
           (= 0 (alength (.multiResolve element false))))
    (let [annotation (.createInfoAnnotation holder
                                            element
                                            (str (.getText element) " cannot be resolved"))]
      (.setTextAttributes annotation CodeInsightColors/WEAK_WARNING_ATTRIBUTES))))

(defn annotate [element holder]
  (cond
    (instance? ClList element) (annotate-list element holder)
    (instance? ClSymbol element) (annotate-symbol element holder)))

(defn initialise []
  (.addExplicitExtension
    com.intellij.lang.LanguageAnnotators/INSTANCE
    (org.jetbrains.plugins.clojure.ClojureLanguage/getInstance)
    (reify Annotator
      (annotate [this element holder]
        (with-logging
          (annotate element holder))))))
