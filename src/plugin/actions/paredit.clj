(ns plugin.actions.paredit
  (:import (org.jetbrains.plugins.clojure.psi.util ClojurePsiUtil)
           (org.jetbrains.plugins.clojure.psi ClojurePsiElement)
           (com.intellij.psi PsiElement PsiFile)
           (com.intellij.lang ASTNode)
           (com.intellij.psi.util PsiTreeUtil)
           (com.intellij.openapi.editor Editor)
           (com.intellij.openapi.util TextRange)
           (org.jetbrains.plugins.clojure.psi.api ClBraced)
           (com.intellij.openapi.diagnostic Logger))
  (:use [plugin.actions.editor :only [defeditor-action]])
  (:require [plugin.tokens :as tokens]
            [plugin.util :as util]
            [plugin.psi :as psi]
            [plugin.editor :as editor]
            [plugin.tokens :as tokens]
            [plugin.actions.core :as actions]))

(def logger (Logger/getInstance "plugin.actions.paredit"))

(defn ^ASTNode next-ascending [^ASTNode node]
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

(defn tidy-braces-before [^Editor editor ^ClBraced sexp]
  (let [offset (psi/start-offset sexp)
        highlighter (plugin.editor/highlighter-iterator editor offset)]
    (while (plugin.editor/looking-back-at highlighter tokens/whitespace offset)
      (.retreat highlighter))
    (if (plugin.editor/looking-back-at highlighter tokens/opening-braces offset)
      (plugin.editor/delete-string editor (.getEnd highlighter) offset))))

(defn tidy-braces-after [^Editor editor ^ClBraced sexp]
  (let [offset (psi/end-offset sexp)
        highlighter (plugin.editor/highlighter-iterator editor offset)]
    (while (plugin.editor/looking-at highlighter tokens/whitespace)
      (.advance highlighter))
    (if (plugin.editor/looking-at highlighter tokens/closing-braces)
      (plugin.editor/delete-string editor offset (.getStart highlighter)))))

(defn slurp [^Editor editor context find-slurpee do-slurp forwards?]
  (if-let [project (.getProject editor)]
    (loop [sexp (ClojurePsiUtil/findSexpAtCaret editor false)]
      (if-not (nil? sexp)
        (if-let [^PsiElement slurpee (find-slurpee sexp)]
          (let [copy (.copy slurpee)
                parent (psi/common-parent slurpee sexp)
                to-reformat (if (instance? PsiFile parent) sexp parent)
                sexp-ptr (psi/smart-ptr sexp)
                to-reformat-ptr (psi/smart-ptr to-reformat)]
            (.delete slurpee)
            (do-slurp sexp copy)
            (psi/commit-document editor)
            (if forwards?
              (tidy-braces-after editor @sexp-ptr)
              (tidy-braces-before editor @sexp-ptr))
            (psi/reformat @to-reformat-ptr))
          (recur (PsiTreeUtil/getParentOfType sexp ClBraced)))))))

(defn splice [^Editor editor context]
  (if-let [project (.getProject editor)]
    (if-let [sexp (ClojurePsiUtil/findSexpAtCaret editor false)]
      (if-let [parent (psi/parent sexp)]
        (let [offset (plugin.editor/offset editor)
              left (.getFirstBrace sexp)
              right (.getLastBrace sexp)
              parent-ptr (psi/smart-ptr parent)]
          (doseq [^PsiElement item (psi/next-siblings left)]
            (if-not (= item right)
              (.addBefore parent (.copy item) sexp)))
          (.delete sexp)
          (plugin.editor/move-to editor (dec offset))
          (psi/commit-document editor)
          (psi/reformat @parent-ptr))))))

(defn initialise []
  ; Unregister these first for REPL convenience
  (actions/unregister-action "plugin.actions.paredit.slurp-backwards")
  (actions/unregister-action "plugin.actions.paredit.slurp-forwards")
  (actions/unregister-action "plugin.actions.paredit.splice")

  (defeditor-action
    "plugin.actions.paredit.slurp-backwards"
    "Slurp Backwards"
    "ctrl shift 9"
    (fn [editor context]
      (slurp editor
             context
             (fn [sexp]
               (PsiTreeUtil/getPrevSiblingOfType sexp ClojurePsiElement))
             (fn [^ClBraced sexp slurpee]
               (let [offset (- (psi/end-offset sexp) (plugin.editor/offset editor))]
                 (.addAfter sexp slurpee (.getFirstBrace sexp))
                 (plugin.editor/move-to editor (- (psi/end-offset sexp) offset))))
             false)))

  (defeditor-action
    "plugin.actions.paredit.slurp-forwards"
    "Slurp Forwards"
    "ctrl shift 0"
    (fn [editor context]
      (slurp editor
             context
             (fn [sexp]
               (PsiTreeUtil/getNextSiblingOfType sexp ClojurePsiElement))
             (fn [^ClBraced sexp slurpee]
               (.addBefore sexp slurpee (.getLastBrace sexp)))
             true)))

  (defeditor-action
    "plugin.actions.paredit.splice"
    "Splice Sexp"
    "alt meta S"
    (fn [editor context]
      (splice editor context))))
