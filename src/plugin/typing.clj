(ns plugin.typing
  (:import (com.intellij.openapi.diagnostic Logger)
           (com.intellij.openapi.editor Editor EditorModificationUtil)
           (com.intellij.openapi.editor.ex EditorEx)
           (com.intellij.openapi.editor.actionSystem TypedActionHandler EditorActionManager)
           (com.intellij.openapi.editor.highlighter HighlighterIterator)
           (com.intellij.openapi.actionSystem PlatformDataKeys)
           (com.intellij.psi PsiElement)
           (com.intellij.psi.util PsiUtilBase)
           (org.jetbrains.plugins.clojure.file ClojureFileType)
           (com.intellij.psi.tree TokenSet)
           (org.jetbrains.plugins.clojure.psi.api ClList ClVector ClMap)
           (org.jetbrains.plugins.clojure.lexer ClojureTokenTypes))
  (:use [plugin.util :only [safely with-command]]
        [plugin.tokens]))

;(set! *warn-on-reflection* true)

(def ^Logger logger (Logger/getInstance "plugin.typing"))

(defn not-nil? [x] (not (nil? x)))

(defn offset [^Editor editor]
  (.getOffset (.getCaretModel editor)))

(defn ^HighlighterIterator highlighter-iterator [^EditorEx editor offset]
  (.createIterator (.getHighlighter editor) offset))

(defn looking-at [^HighlighterIterator highlighter predicate]
  (and (not (.atEnd highlighter))
       (predicate (.getTokenType highlighter))))

(defn looking-back-at [^HighlighterIterator highlighter predicate offset]
  (if (or (.atEnd highlighter)
          (= offset (.getStart highlighter)))
    (do
      (.retreat highlighter)
      (let [result (and (not (.atEnd highlighter))
                        (predicate (.getTokenType highlighter)))]
        (.advance highlighter)
        result))
    (looking-at highlighter predicate)))

(defn inside-string? [^Editor editor]
  (let [offset (offset editor)
        highlighter (highlighter-iterator editor offset)]
    (and (looking-at highlighter strings)
         (< (.getStart highlighter) offset (.getEnd highlighter)))))

(defn inside-comment? [editor]
  (let [offset (offset editor)
        highlighter (highlighter-iterator editor offset)]
    (or (and (looking-at highlighter comments)
             (< (.getStart highlighter) offset (.getEnd highlighter)))
        (if (and (> offset 0)
                 (= offset (.getStart highlighter)))
          (do
            (.retreat highlighter)
            (comments (.getTokenType highlighter)))
          false))))

(defn insert [editor string]
  (EditorModificationUtil/insertStringAtCaret editor string false true))

(defn insert-after [editor string]
  (EditorModificationUtil/insertStringAtCaret editor string false false))

(def matching-char {\( \), \[ \], \{ \}, \" \"})

(def matching-type {\) ClList, \] ClVector, \} ClMap})

(defn ^PsiElement find-enclosing [^Editor editor type]
  (loop [psi (PsiUtilBase/getElementAtCaret editor)]
    (cond
      (nil? psi) nil
      (and (instance? type psi)
           (> (offset editor) (.getStartOffset (.getTextRange psi)))) psi
      :else (recur (.getParent psi)))))

(defn open-matched [^Editor editor char-typed]
  (let [offset (offset editor)
        highlighter (highlighter-iterator editor offset)
        needs-whitespace-before (looking-back-at highlighter
                                                 (fn [token] (not (or (whitespace token)
                                                                      (opening-braces token)
                                                                      (modifiers token))))
                                                 offset)
        needs-whitespace-after (looking-at highlighter
                                           (fn [token] (not (or (whitespace token)
                                                                (closing-braces token)))))]
    (if needs-whitespace-before (insert editor " "))
    (insert editor (str char-typed))
    (if needs-whitespace-after (insert-after editor " "))
    (insert-after editor (str (matching-char char-typed)))))

(defn close-matched [^Editor editor char-typed]
  (if-let [enclosing (find-enclosing editor (matching-type char-typed))]
    (let [offset (.getEndOffset (.getTextRange enclosing))
          highlighter (highlighter-iterator editor offset)]
      (.moveToOffset (.getCaretModel editor) offset)
      (if (= offset (.getStart highlighter))
        (.retreat highlighter))
      (.retreat highlighter)
      (if (looking-at highlighter whitespace)
        (.deleteString (.getDocument editor)
                       (.getStart highlighter)
                       (.getEnd highlighter))))))

(defn process-key [project ^Editor editor psi-file char-typed]
  (with-command
    project "" nil
    (let [is-string (inside-string? editor)
          is-comment (inside-comment? editor)]
      (if (or is-string is-comment)
        (if (and is-string (= char-typed \"))
          (let [string (PsiUtilBase/getElementAtCaret editor)
                offset (offset editor)
                end-offset (.getEndOffset (.getTextRange string))]
            (if (= offset (dec end-offset))
              (.moveToOffset (.getCaretModel editor) end-offset)
              (insert editor "\\\"")))
          (insert editor (str char-typed)))
        (if (contains? #{\( \[ \{ \"} char-typed)
          (open-matched editor char-typed)
          (close-matched editor char-typed))))))

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

; For REPL use
(defn restore-handler []
  (let [action-manager (EditorActionManager/getInstance)
        typed-action (.getTypedAction action-manager)
        handler (.getHandler typed-action)]
    (if (instance? ClojureTypedHandler handler)
      (.setupHandler typed-action (:previous handler)))))
