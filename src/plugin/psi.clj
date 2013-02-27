(ns plugin.psi
  (:refer-clojure :exclude [contains? descendants tree-seq])
  (:import (org.jetbrains.plugins.clojure.psi.api ClMetadata)
           (com.intellij.psi PsiElement PsiComment PsiWhiteSpace SmartPointerManager PsiDocumentManager
                             SmartPsiElementPointer)
           (com.intellij.psi.impl.source.tree LeafPsiElement)
           (com.intellij.openapi.editor Editor)
           (com.intellij.psi.util PsiTreeUtil)
           (com.intellij.psi.codeStyle CodeStyleManager)
           (clojure.lang IDeref)))

(defn visible?
  "Visible elements are any real program elements - non-leaf,
  non-comment, non-whitespace"
  [element]
  (not (or (nil? element)
           (instance? LeafPsiElement element)
           (instance? PsiWhiteSpace element)
           (instance? PsiComment element))))

(defn significant?
  "Significant elements are any visible elements except metadata"
  [element]
  (and (visible? element)
       (not (instance? ClMetadata element))))

(defn ^PsiElement next-siblings
  "Lazy sequence of the following siblings of element"
  [^PsiElement element]
  (lazy-seq
    (when (.getNextSibling element)
      (let [next (.getNextSibling element)]
        (cons next
              (next-siblings next))))))

(defn prev-siblings
  "Lazy sequence of the previous siblings of element"
  [^PsiElement element]
  (lazy-seq
    (when (.getPrevSibling element)
      (let [prev (.getPrevSibling element)]
        (cons prev
              (prev-siblings prev))))))

(defn children
  "Lazy sequence of children of element"
  [^PsiElement element]
  (if element
    (let [first (.getFirstChild element)]
      (cons first (next-siblings first)))
    '()))

(defn significant-children
  "Lazy sequence of the significant children of element"
  [element]
  (filter significant? (children element)))

(defn significant-offset [element]
  (count (filter significant? (prev-siblings element))))

; TODO make this inline, replace usages
(defn ^PsiElement parent [^PsiElement element]
  (.getParent element))

(defn common-parent [first second]
  (PsiTreeUtil/findCommonParent first second))

(defn contains? [container containee]
  (= container (common-parent containee container)))

(defn ^ClMetadata metadata [element]
  (if-let [test (first (filter visible? (prev-siblings element)))]
    (if (instance? ClMetadata test)
      test)))

(defn tree-seq [^PsiElement element]
  (clojure.core/tree-seq
    (fn [^PsiElement element] (if (.getFirstChild element) true false))
    (fn [^PsiElement element] (children element))
    element))

(defn significant-tree-seq [^PsiElement element]
  (clojure.core/tree-seq
    (fn [^PsiElement element] (if (.getFirstChild element) true false))
    (fn [^PsiElement element] (significant-children element))
    element))

(defn smart-ptr [^PsiElement element]
  (let [^SmartPointerManager ptr-manager (SmartPointerManager/getInstance (.getProject element))
        ^SmartPsiElementPointer ptr (.createSmartPsiElementPointer ptr-manager element)]
    (reify IDeref
      (deref [this]
        (.getElement ptr)))))

(defn commit-document [^Editor editor]
  (let [project (.getProject editor)
        manager (PsiDocumentManager/getInstance project)
        document (.getDocument editor)]
    (.commitDocument manager document)
    (.doPostponedOperationsAndUnblockDocument manager document)))

(defn reformat [^PsiElement element]
  (.reformat (CodeStyleManager/getInstance (.getProject element)) element))

(defn start-offset [^PsiElement element]
  (.getStartOffset (.getTextRange element)))

(defn end-offset [^PsiElement element]
  (.getEndOffset (.getTextRange element)))
