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
  (:require [plugin.tokens :as tokens]
            [plugin.util :as util]
            [plugin.psi :as psi]
            [plugin.editor :as edit]
            [plugin.tokens :as tokens]
            [plugin.actions :as actions]))

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
  (actions/unregister-action ::slurp-backwards)
  (actions/unregister-action ::slurp-forwards)
  (actions/unregister-action ::splice)

  (actions/register-action (actions/editor-write-action :execute slurp-backwards
                                                        :text "Slurp Backwards")
                           ::slurp-backwards)
  (actions/register-shortcut ::slurp-backwards "ctrl shift 9")

  (actions/register-action (actions/editor-write-action :execute slurp-forwards
                                                        :text "Slurp Forwards")
                           ::slurp-forwards)
  (actions/register-shortcut ::slurp-forwards "ctrl shift 0")

  (actions/register-action (actions/editor-write-action :execute splice
                                                        :text "Splice Sexp")
                           ::splice)
  (actions/register-shortcut ::splice "alt S"))
