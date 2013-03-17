(ns plugin.editor
  (:import (com.intellij.openapi.editor.highlighter HighlighterIterator)
           (com.intellij.openapi.editor Editor EditorModificationUtil Document)
           (com.intellij.openapi.editor.ex EditorEx)
           (com.intellij.openapi.util TextRange)
           (com.intellij.psi.codeStyle CodeStyleManager)
           (com.intellij.psi PsiDocumentManager PsiElement)))

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

(defn ^TextRange text-range [^PsiElement element]
  (.getTextRange element))

(defn start-offset [^TextRange range]
  (.getStartOffset range))

(defn end-offset [^TextRange range]
  (.getEndOffset range))

(defn range-length [range]
  (- (end-offset range)
     (start-offset range)))

(defn insert [editor string]
  (EditorModificationUtil/insertStringAtCaret editor string false true))

(defn insert-after [editor string]
  (EditorModificationUtil/insertStringAtCaret editor string false false))

(defn insert-at [^Editor editor at string]
  (.insertString (.getDocument editor) at string))

(defn delete-string [^Editor editor start end]
  (.deleteString (.getDocument editor) start end))

(defn delete-range [^Editor editor range]
  (.deleteString (.getDocument editor) (start-offset range) (end-offset range)))

(defn text-from [^Editor editor start end]
  (text-from editor (TextRange. start end)))

(defn text-from [^Editor editor ^TextRange range]
  (.getText (.getDocument editor) range))

(defn move-to [^Editor editor offset]
  (.moveToOffset (.getCaretModel editor) offset))

(defn reformat-text [^Editor editor start end]
  (let [document (.getDocument editor)
        project (.getProject editor)]
    (if-let [psi-file (.getPsiFile (PsiDocumentManager/getInstance project) document)]
      (.reformatText (CodeStyleManager/getInstance project) psi-file start end))))
