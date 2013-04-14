(ns plugin.psi
  (:refer-clojure :exclude [contains? descendants tree-seq])
  (:import (org.jetbrains.plugins.clojure.psi.api ClMetadata)
           (com.intellij.psi PsiElement PsiComment PsiWhiteSpace SmartPointerManager PsiDocumentManager
                             SmartPsiElementPointer)
           (com.intellij.psi.impl.source.tree LeafPsiElement)
           (com.intellij.openapi.editor Editor)
           (com.intellij.psi.util PsiTreeUtil CachedValuesManager CachedValueProvider)
           (com.intellij.psi.codeStyle CodeStyleManager)
           (com.intellij.psi.util CachedValueProvider$Result)
           (clojure.lang IDeref)
           (com.intellij.openapi.util Key)))

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
  (cond
    (nil? containee) false
    (= container containee) true
    :else (recur container (parent containee))))

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

(defn cached-value
  "Gets a cached value from element using key, calculating it if not
   found by calling calculator passing the element as an argument.
   dependencies are used as a dependency of the cached value, or element
   is added as a dependency if dependencies are not passed."
  ([^PsiElement element key calculator & dependencies]
   (let [project (.getProject element)
         manager (CachedValuesManager/getManager project)
         provider (reify CachedValueProvider
                    (compute [this]
                      (CachedValueProvider$Result. (calculator element) (into-array (or dependencies
                                                                                        [element])))))]
     (.getCachedValue manager element key provider false))))

(defn create-cached-value
  "Creates a cached value for attachment to element, calculating it
   by calling calculator passing element as an argument. dependencies
   are used as a dependency of the cached value, or element is added
   as a dependency if dependencies are not passed. Cached value is not
   actually added to element."
  [^PsiElement element calculator & dependencies]
  (let [project (.getProject element)
        manager (CachedValuesManager/getManager project)
        provider (reify CachedValueProvider
                   (compute [this]
                     (CachedValueProvider$Result. (calculator element) (into-array (or dependencies
                                                                                       [element])))))]
    (.createCachedValue manager provider)))

(defn cache-key [item]
  (Key/create (str item)))

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

(defn after?
  "Return true if first appears after second in the file."
  [^PsiElement first ^PsiElement second]
  (>= (start-offset first)
      (end-offset second)))
