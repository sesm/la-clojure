(ns plugin.editor
  (:import (com.intellij.openapi.editor.highlighter HighlighterIterator)
           (com.intellij.openapi.editor Editor EditorModificationUtil)
           (com.intellij.openapi.editor.ex EditorEx)))

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

(defn insert [editor string]
  (EditorModificationUtil/insertStringAtCaret editor string false true))

(defn insert-after [editor string]
  (EditorModificationUtil/insertStringAtCaret editor string false false))

(defn delete-string [^Editor editor start end]
  (.deleteString (.getDocument editor) start end))

(defn move-to [^Editor editor offset]
  (.moveToOffset (.getCaretModel editor) offset))
