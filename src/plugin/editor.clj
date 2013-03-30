(ns plugin.editor
  (:import (com.intellij.openapi.editor.highlighter HighlighterIterator)
           (com.intellij.openapi.editor Editor EditorModificationUtil Document ScrollType)
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

(defn ^String text-from
  ([^Editor editor start end]
   (text-from editor (TextRange. start end)))
  ([^Editor editor ^TextRange range]
   (.getText (.getDocument editor) range))
  ([^Editor editor]
    (.getText (.getDocument editor))))

(defn set-text [^Editor editor text]
  (.setText (.getDocument editor) text))

(defn move-to [^Editor editor offset]
  (.moveToOffset (.getCaretModel editor) offset)
  (.scrollToCaret (.getScrollingModel editor) ScrollType/MAKE_VISIBLE))

(defn reformat-text [^Editor editor start end]
  (let [document (.getDocument editor)
        project (.getProject editor)]
    (if-let [psi-file (.getPsiFile (PsiDocumentManager/getInstance project) document)]
      (.reformatText (CodeStyleManager/getInstance project) psi-file start end))))

(defn line-count [^Editor editor]
  (-> editor
      .getDocument
      .getLineCount))

(defn text-length [^Editor editor]
  (-> editor
      .getDocument
      .getTextLength))

(defn line-number [^Editor editor]
  (-> editor
      .getDocument
      (.getLineNumber (offset editor))))

(defn scroll-down [editor]
  (move-to editor (text-length editor)))

(defn selected-text [^Editor editor]
  (-> editor .getSelectionModel .getSelectedText))

(defn selected-text-range [^Editor editor]
  (let [model (.getSelectionModel editor)]
    (TextRange. (.getSelectionStart model) (.getSelectionEnd model))))
