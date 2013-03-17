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
            [plugin.editor :as edit]
            [plugin.tokens :as tokens]
            [plugin.actions.core :as actions]))

(def logger (Logger/getInstance "plugin.actions.paredit"))

(defn find-slurpee [^Editor editor finder]
  (if-let [project (.getProject editor)]
    (loop [sexp (ClojurePsiUtil/findSexpAtCaret editor false)]
      (if-not (nil? sexp)
        (if-let [^PsiElement slurpee (finder sexp)]
          [sexp slurpee]
          (recur (PsiTreeUtil/getParentOfType sexp ClBraced)))))))

(defn slurp-backwards [editor context]
  (if-let [[sexp slurpee] (find-slurpee editor
                                        #(PsiTreeUtil/getPrevSiblingOfType %1 ClojurePsiElement))]
    (let [sexp-range (edit/text-range sexp)
          slurpee-range (edit/text-range slurpee)
          brace-range (edit/text-range (.getFirstBrace ^ClBraced sexp))
          brace-text (edit/text-from editor brace-range)]
      (edit/delete-string editor
                          (edit/start-offset brace-range)
                          (edit/end-offset brace-range))
      (edit/insert-at editor (edit/start-offset slurpee-range) brace-text)
      (psi/commit-document editor)
      (edit/reformat-text editor
                          (edit/start-offset slurpee-range)
                          (edit/end-offset sexp-range)))))

(defn slurp-forwards [editor context]
  (if-let [[sexp slurpee] (find-slurpee editor
                                        #(PsiTreeUtil/getNextSiblingOfType %1 ClojurePsiElement))]
    (let [sexp-range (edit/text-range sexp)
          slurpee-range (edit/text-range slurpee)
          brace-range (edit/text-range (.getLastBrace ^ClBraced sexp))
          brace-text (edit/text-from editor brace-range)]
      (edit/insert-at editor (edit/end-offset slurpee-range) brace-text)
      (edit/delete-range editor brace-range)
      (psi/commit-document editor)
      (edit/reformat-text editor
                          (edit/start-offset sexp-range)
                          (+ (edit/end-offset slurpee-range)
                             (edit/range-length brace-range))))))

(defn splice [^Editor editor context]
  (if-let [sexp (ClojurePsiUtil/findSexpAtCaret editor false)]
    (let [left-range (edit/text-range (.getFirstBrace sexp))
          right-range (edit/text-range (.getLastBrace sexp))]
      (edit/delete-range editor right-range)
      (edit/delete-range editor left-range)
      (psi/commit-document editor)
      (edit/reformat-text editor
                          (edit/start-offset left-range)
                          (- (edit/end-offset right-range)
                             (edit/range-length left-range)
                             (edit/range-length right-range))))))

(defn initialise []
  ; Unregister these first for REPL convenience
  (actions/unregister-action "plugin.actions.paredit.slurp-backwards")
  (actions/unregister-action "plugin.actions.paredit.slurp-forwards")
  (actions/unregister-action "plugin.actions.paredit.splice")

  (defeditor-action
    "plugin.actions.paredit.slurp-backwards"
    "Slurp Backwards"
    "ctrl shift 9"
    slurp-backwards)

  (defeditor-action
    "plugin.actions.paredit.slurp-forwards"
    "Slurp Forwards"
    "ctrl shift 0"
    slurp-forwards)

  (defeditor-action
    "plugin.actions.paredit.splice"
    "Splice Sexp"
    "alt S"
    splice))
