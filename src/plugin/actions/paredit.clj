(ns plugin.actions.paredit
  (:import (org.jetbrains.plugins.clojure.psi.util ClojurePsiUtil)
           (org.jetbrains.plugins.clojure.psi ClojurePsiElement)
           (com.intellij.psi PsiElement PsiFile)
           (com.intellij.psi.util PsiTreeUtil)
           (org.jetbrains.plugins.clojure.psi.api ClBraced)
           (com.intellij.openapi.diagnostic Logger))
  (:use [plugin.actions.editor :only [defeditor-action]])
  (:require [plugin.tokens :as tokens]
            [plugin.util :as util]
            [plugin.psi :as psi]))

(def logger (Logger/getInstance "plugin.actions.paredit"))

(defn next-ascending [node]
  (if-not (nil? node)
    (if-let [next (.getTreeNext node)]
      next
      (recur (.getTreeParent node)))))

(defn next-non-ws [^PsiElement element]
  (loop [node (next-ascending (.getNode element))]
    (if-not (nil? node)
      (if (tokens/whitespace node)
        (recur (next-ascending node))
        (.getPsi node)))))

(defn move-cursor [editor from to]
  (let [caret-model (.getCaretModel editor)
        offset (.getOffset caret-model)
        from-range (.getTextRange from)]
    (if (<= (.getStartOffset from-range) offset (.getEndOffset from-range))
      (let [to-range (.getTextRange to)]
        (.info logger (str to-range))
        (.moveToOffset caret-model (+ (.getStartOffset to-range)
                                      (- offset (.getStartOffset from-range))))))))

(defn slurp [editor context find-slurpee do-slurp]
  (if-let [project (.getProject editor)]
    (loop [sexp (ClojurePsiUtil/findSexpAtCaret editor false)]
      (if-not (nil? sexp)
        (if-let [slurpee (find-slurpee sexp)]
          (let [copy (.copy slurpee)
                parent (psi/common-parent slurpee sexp)
                to-reformat (if (instance? PsiFile parent) sexp parent)
                to-reformat-ptr (psi/smart-ptr to-reformat)]
            (.delete slurpee)
            (do-slurp sexp copy)
            (psi/commit-all project)
            (psi/reformat @to-reformat-ptr))
          (recur (PsiTreeUtil/getParentOfType sexp ClBraced)))))))

(defn splice [editor context]
  (if-let [project (.getProject editor)]
    (if-let [sexp (ClojurePsiUtil/findSexpAtCaret editor false)]
      (if-let [parent (psi/parent sexp)]
        (let [caret-model (.getCaretModel editor)
              offset (.getOffset caret-model)
              left (.getFirstBrace sexp)
              right (.getLastBrace sexp)
              parent-ptr (psi/smart-ptr parent)]
          (doseq [item (psi/next-siblings left)]
            (if-not (= item right)
              (.addBefore parent (.copy item) sexp)))
          (.delete sexp)
          (.moveToOffset caret-model (dec offset))
          (psi/commit-all project)
          (psi/reformat @parent-ptr))))))

(defn initialise []
  (defeditor-action
    "plugin.actions.paredit.slurp-backwards"
    "Slurp Backwards"
    "ctrl shift 9"
    (fn [editor context]
      (slurp editor
             context
             (fn [sexp]
               (PsiTreeUtil/getPrevSiblingOfType sexp ClojurePsiElement))
             (fn [sexp slurpee]
               (.addAfter sexp slurpee (.getFirstBrace sexp)))))
    nil)

  (defeditor-action
    "plugin.actions.paredit.slurp-forwards"
    "Slurp Forwards"
    "ctrl shift 0"
    (fn [editor context]
      (slurp editor
             context
             (fn [sexp]
               (PsiTreeUtil/getNextSiblingOfType sexp ClojurePsiElement))
             (fn [sexp slurpee]
               (.addBefore sexp slurpee (.getLastBrace sexp)))))
    nil)

  (defeditor-action
    "plugin.actions.paredit.splice"
    "Splice Sexp"
    "alt meta S"
    (fn [editor context]
      (splice editor context))
    nil))
