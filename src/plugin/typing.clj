(ns plugin.typing
  (:import (com.intellij.openapi.diagnostic Logger)
           (com.intellij.openapi.editor Editor EditorModificationUtil)
           (com.intellij.openapi.editor.ex EditorEx)
           (com.intellij.openapi.editor.actionSystem TypedActionHandler EditorActionManager)
           (com.intellij.openapi.editor.highlighter HighlighterIterator)
           (com.intellij.openapi.actionSystem PlatformDataKeys)
           (com.intellij.psi PsiElement PsiDocumentManager)
           (com.intellij.psi.util PsiUtilBase)
           (org.jetbrains.plugins.clojure.file ClojureFileType)
           (com.intellij.psi.tree TokenSet)
           (org.jetbrains.plugins.clojure.psi.api ClList ClVector ClMap)
           (org.jetbrains.plugins.clojure.lexer ClojureTokenTypes))
  (:use [plugin.util :only [safely with-command]])
  (:require [plugin.psi :as psi]
            [plugin.editor :as editor]
            [plugin.tokens :as tokens]))

;(set! *warn-on-reflection* true)

(def ^Logger logger (Logger/getInstance "plugin.typing"))

(defn not-nil? [x] (not (nil? x)))

(defn inside-string? [^Editor editor]
  (let [offset (editor/offset editor)
        highlighter (editor/highlighter-iterator editor offset)]
    (and (editor/looking-at highlighter tokens/strings)
         (< (.getStart highlighter) offset (.getEnd highlighter)))))

(defn inside-comment? [editor]
  (let [offset (editor/offset editor)
        highlighter (editor/highlighter-iterator editor offset)]
    (or (and (editor/looking-at highlighter tokens/comments)
             (< (.getStart highlighter) offset (.getEnd highlighter)))
        (if (and (> offset 0)
                 (= offset (.getStart highlighter)))
          (do
            (.retreat highlighter)
            (tokens/comments (.getTokenType highlighter)))
          false))))

(def matching-char {\( \), \[ \], \{ \}, \" \"})

(def matching-type {\) ClList, \] ClVector, \} ClMap})

(defn ^PsiElement find-enclosing [^Editor editor type]
  (loop [element (PsiUtilBase/getElementAtCaret editor)]
    (cond
      (nil? element) nil
      (and (instance? type element)
           (> (editor/offset editor) (psi/start-offset element))) element
      :else (recur (psi/parent element)))))

(defn open-matched [^Editor editor char-typed]
  (let [offset (editor/offset editor)
        selection-model (.getSelectionModel editor)
        start (if (.hasSelection selection-model)
                (.getSelectionStart selection-model)
                offset)
        end (if (.hasSelection selection-model)
              (.getSelectionEnd selection-model)
              offset)
        needs-whitespace-before (editor/looking-back-at (editor/highlighter-iterator editor start)
                                                        (fn [token]
                                                          (not (or (tokens/whitespace token)
                                                                   (tokens/opening-braces token)
                                                                   (tokens/modifiers token))))
                                                        start)
        needs-whitespace-after (editor/looking-at (editor/highlighter-iterator editor end)
                                                  (fn [token]
                                                    (not (or (tokens/whitespace token)
                                                             (tokens/closing-braces token)))))]
    (if needs-whitespace-before (editor/insert editor " "))
    (editor/insert editor (str char-typed))
    (if needs-whitespace-after (editor/insert-after editor " "))
    (editor/insert-after editor (str (matching-char char-typed)))))

(defn close-matched [^Editor editor char-typed]
  (if-let [enclosing (find-enclosing editor (matching-type char-typed))]
    (let [offset (psi/end-offset enclosing)
          highlighter (editor/highlighter-iterator editor offset)]
      (editor/move-to editor offset)
      (if (= offset (.getStart highlighter))
        (.retreat highlighter))
      (.retreat highlighter)
      (if (editor/looking-at highlighter tokens/whitespace)
        (editor/delete-string editor
                              (.getStart highlighter)
                              (.getEnd highlighter))))))

(defn process-key [project ^Editor editor psi-file char-typed]
  (let [is-string (inside-string? editor)
        is-comment (inside-comment? editor)]
    (if (or is-string is-comment)
      (if (and is-string (= char-typed \"))
        (let [string (PsiUtilBase/getElementAtCaret editor)
              offset (editor/offset editor)
              end-offset (psi/end-offset string)]
          (if (= offset (dec end-offset))
            (editor/move-to editor end-offset)
            (editor/insert editor "\\\"")))
        (editor/insert editor (str char-typed)))
      (if (contains? #{\( \[ \{ \"} char-typed)
        (open-matched editor char-typed)
        (close-matched editor char-typed)))))

(defrecord ClojureTypedHandler [^TypedActionHandler previous]
  TypedActionHandler
  (execute [this editor char-typed data-context]
    (let [do-original (fn [] (safely (.execute previous editor char-typed data-context)))]
      (if (contains? #{\( \) \[ \] \{ \} \"} char-typed)
        (let [project (.getData PlatformDataKeys/PROJECT data-context)]
          (if (or (nil? project) (.isColumnMode editor))
            (do-original)
            (let [psi-file (PsiUtilBase/getPsiFileInEditor editor project)]
              (if (nil? psi-file)
                (do-original)
                (if (= (.getLanguage psi-file) ClojureFileType/CLOJURE_LANGUAGE)
                  (process-key project editor psi-file char-typed)
                  (do-original))))))
        (do-original)))))

(defn initialise []
  (let [action-manager (EditorActionManager/getInstance)
        typed-action (.getTypedAction action-manager)
        handler (.getHandler typed-action)
        new-handler (if (instance? ClojureTypedHandler handler)
                      (:previous handler)
                      handler)]
    (.setupHandler typed-action (ClojureTypedHandler. new-handler))))
