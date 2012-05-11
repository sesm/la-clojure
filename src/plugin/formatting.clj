(ns plugin.formatting
  (:import (com.intellij.formatting FormattingModelBuilder FormattingModelProvider Indent Block Spacing ChildAttributes
                                    Alignment Wrap WrapType)
           (com.intellij.openapi.diagnostic Logger)
           (org.jetbrains.plugins.clojure.psi.api ClList ClListLike ClVector ClMap ClSet ClKeyword)
           (org.jetbrains.plugins.clojure.psi.impl ClMapEntry)
           (org.jetbrains.plugins.clojure.psi.api.symbols ClSymbol)
           (org.jetbrains.plugins.clojure.psi.util ClojurePsiCheckers)
           (org.jetbrains.plugins.clojure.parser ClojureElementTypes)
           (org.jetbrains.plugins.clojure.lexer ClojureTokenTypes)
           (com.intellij.psi PsiComment PsiFile)
           (com.intellij.psi.tree TokenSet)
           (com.intellij.lang ASTNode)
           (com.intellij.psi.impl.source.tree LeafPsiElement)
           (java.util Collection ArrayList))
  (:use [plugin.util :only [safely with-logging]]
        [plugin.tokens]
        [plugin.predicates]))

;(set! *warn-on-reflection* true)

(def ^Logger logger (Logger/getInstance "plugin.formatting"))

(def no-spacing (Spacing/createSpacing 0 0 0 false 0))
(def no-spacing-with-newline (Spacing/createSpacing 0 0 0 true 1))
(def mandatory-newline (Spacing/createSpacing 1 1 1 true 100))
(def ns-spacing (Spacing/createSpacing 1 1 2 true 100))
(def common-spacing (Spacing/createSpacing 1 1 0 true 100))
(def no-newline (Spacing/createSpacing 1 1 0 false 0))

(def spacing)
(def incomplete?)

(def sub-blocks)
(def child-attributes)

(defrecord ClojureBlock [^ASTNode node alignment indent wrap settings params ^Collection children]
  Block
  (getTextRange [this] (.getTextRange node))
  (getSubBlocks [this]
    (if (.isEmpty children)
      (.addAll children (with-logging (sub-blocks this))))
    children)
  (getWrap [this] wrap)
  (getIndent [this] indent)
  (getAlignment [this] alignment)
  (getSpacing [this child1 child2] (with-logging (spacing child1 child2)))
  (getChildAttributes [this newChildIndex] (with-logging (child-attributes this newChildIndex)))
  (isIncomplete [this] (incomplete? node))
  (isLeaf [this] (nil? (.getFirstChildNode node))))

(defn no-indent [] (Indent/getNoneIndent))
(defn absolute-no-indent [] (Indent/getAbsoluteNoneIndent))
(defn normal-indent [] (Indent/getNormalIndent true))
(defn continuation-indent [] (Indent/getContinuationIndent true))

(defn create-alignment [] (Alignment/createAlignment))
(defn shifting-alignment [] (Alignment/createAlignment true))
(defn child-alignment [parent] (Alignment/createChildAlignment parent))

;; parameters

(defn brace-params [] {:indent (no-indent)})

(defn create-params [align indent]
  (let [child (if (nil? align) nil (child-alignment align))]
    {:alignment align, :child child, :indent indent}))

(defn create-wrap-params [align indent wrap]
  (let [child (if (nil? align) nil (child-alignment align))]
    {:alignment align, :child child, :indent indent, :wrap wrap}))

(defn body-params []
  (create-params (create-alignment) (normal-indent)))

(defn head-params []
  (create-params nil (normal-indent)))

(defn parameter-params []
  (create-params (create-alignment) (continuation-indent)))

(defn shifting-params []
  (create-params (shifting-alignment) (normal-indent)))

(defn file-params [] (repeat {:indent (absolute-no-indent)}))

(defn import-class-params []
  (create-wrap-params (create-alignment) (normal-indent) (Wrap/createWrap (WrapType/NORMAL) true)))

(defn normal-params []
  (concat [(brace-params)]
          (repeat (body-params))))

(defn application-params [num-parameters]
  (concat [(brace-params) (head-params)]
          (repeat num-parameters (parameter-params))
          (repeat (body-params))))

(defn import-clause-params []
  (concat [(brace-params) (head-params)]
          (repeat (import-class-params))))

(defn has-types? [^ASTNode node types]
  (let [elements (map #(.getElementType ^ASTNode %) (significant-elements node))]
    (every? true? (map = elements types))))

(defn defn-parameters [^ASTNode
                       node]
  (if (has-types? node [ClojureElementTypes/SYMBOL
                        ClojureElementTypes/SYMBOL
                        ClojureElementTypes/VECTOR])
    2
    1))

(defn fn-parameters [^ASTNode node]
  (cond (has-types? node [ClojureElementTypes/SYMBOL
                          ClojureElementTypes/SYMBOL
                          ClojureElementTypes/VECTOR]) 2
        (has-types? node [ClojureElementTypes/SYMBOL
                          ClojureElementTypes/VECTOR]) 1
        (has-types? node [ClojureElementTypes/SYMBOL
                          ClojureElementTypes/SYMBOL]) 1
        :else 0))

(def indent-form {:ns              1,
                  :let             1,
                  :if-let          1,
                  :when-let        1,
                  :when-first      1,
                  :with-open       1,
                  :with-local-vars 1,
                  :loop            1,
                  :binding         1,
                  :defmethod       3,
                  :defn            defn-parameters,
                  :defmacro        defn-parameters,
                  :definline       defn-parameters,
                  :defn-           defn-parameters,
                  :fn              fn-parameters,
                  :defrecord       2,
                  :deftype         2,
                  :defprotocol     1,
                  :extend-type     1,
                  :reify           1,
                  :proxy           2,
                  :assoc           1,
                  :deftest         1,
                  :describe        1,
                  :given           1,
                  :testing         1,
                  :if              1,
                  :if-not          1,
                  :when            1,
                  :when-not        1,
                  :doseq           1,
                  :dotimes         1,
                  :catch           2,
                  :locking         1,
                  :case            1})

(defn num-parameters [^ASTNode node]
  (let [head-token ^ASTNode (first (significant-elements node))
        element-type (.getElementType head-token)]
    (if (symbol-token? element-type)
      (let [indent (get indent-form (keyword (.getText head-token)) 0)]
        (if (fn? indent)
          (indent node)
          indent))
      0)))

(defn list-params [^ASTNode node]
  (cond
    (matches? node
              list-like?
              symbol-head?
              (list-like-parent?
                (head-text-in? "defrecord" "extend-type" "reify" "deftype" "proxy"))) (application-params 1)
    (matches? node
              list-like?
              symbol-head?
              (list-like-parent?
                keyword-head?
                (head-text? ":import")
                (list-like-parent?
                  symbol-head?
                  (head-text? "ns")))) (import-clause-params)
    (matches? node
              symbol-head?) (application-params (num-parameters node))
    (matches? node
              list-like?
              keyword-head?
              (list-like-parent?
                (head-text? "ns"))) (application-params 0)
    :else (normal-params)))

; TODO fix lexer to lex #{ as single token
(defn set-params []
  (concat (repeat 2 (brace-params))
          (repeat (body-params))))

(defn map-params []
  (concat [(brace-params)]
          (cycle [(body-params) (shifting-params)])))


(defn flatten-children [^ASTNode node]
  (flatten
    (map
      (fn [^ASTNode node] (if (instance? ClMapEntry (.getPsi node))
                            (seq (.getChildren node nil))
                            node))
      (seq (.getChildren node nil)))))

(defn non-empty-children [^ASTNode node]
  (filter non-empty? (flatten-children node)))

(defn get-params [^ASTNode node]
  (let [psi (.getPsi node)]
    (cond
      (instance? ClList psi) (list-params node)
      (instance? ClVector psi) (normal-params)
      (instance? ClSet psi) (set-params)
      (instance? ClMap psi) (map-params)
      (instance? PsiFile psi) (file-params))))

(defn create-block
  ([node settings]
   (ClojureBlock. node nil nil nil settings (file-params) (ArrayList.)))
  ([node alignment indent wrap settings]
   (ClojureBlock. node alignment indent wrap settings (get-params node) (ArrayList.))))

(defn sub-blocks
  ([block] (sub-blocks block (:params block)))
  ([block params]
   (loop [children (non-empty-children (:node block))
          child-params params
          result []]
     (if (seq children)
       (let [child (first children)
             element (.getElementType ^ASTNode child)
             params (first child-params)
             wrap (if (brace? element) nil (:wrap params))]
         (if (or (comments element)
                 (whitespace element)
                 (meta-form? element))
           (recur (rest children)
                  child-params
                  (conj result (create-block child
                                             (cond
                                               (whitespace element) nil
                                               (meta-form? element) (:alignment params)
                                               :else (:child params))
                                             (:indent params)
                                             wrap
                                             (:settings block))))
           (recur (rest children)
                  (rest child-params)
                  (conj result (create-block child
                                             (:alignment params)
                                             (:indent params)
                                             wrap
                                             (:settings block))))))
       result))))


(defn spacing [child1 child2]
  (if (and (instance? ClojureBlock child1)
           (instance? ClojureBlock child2))
    (let [node1 ^ASTNode (:node child1)
          node2 ^ASTNode (:node child2)
          type1 (.getElementType node1)
          type2 (.getElementType node2)
          psi1 (.getPsi node1)
          psi2 (.getPsi node2)]
      (cond
        (= ClojureElementTypes/NS type1) ns-spacing
        (ClojurePsiCheckers/isImportingClause psi2) mandatory-newline
        (= "," (.getText node2)) no-spacing
        (or (brace? type1)
            (brace? type2)) no-spacing-with-newline
        (instance? ClKeyword psi1) no-newline
        (and (instance? ClListLike psi1)
             (instance? ClListLike psi2)
             (= (.getParent psi1) (.getParent psi2))
             (ClojurePsiCheckers/isImportingClause (.getParent psi1))) mandatory-newline
        (.contains ClojureElementTypes/MODIFIERS type1) no-spacing
        (.contains ClojureTokenTypes/ATOMS type2) no-spacing
        :else common-spacing))))


(defn child-attributes [block index]
  (loop [children (flatten-children (:node block))
         params (:params block)
         item index]
    (cond
      (= item 0) (ChildAttributes. (:indent (first params)) (:alignment (first params)))
      (formattable? (first children)) (recur (rest children) (rest params) (dec item))
      :else (recur (rest children) params (if (non-empty? (first children)) (dec item) item)))))

(defn incomplete? [node] false)

(defn initialise []
  (.addExplicitExtension
    com.intellij.lang.LanguageFormatting/INSTANCE
    (org.jetbrains.plugins.clojure.ClojureLanguage/getInstance)
    (reify FormattingModelBuilder
      (createModel [this element settings]
        (with-logging
          (let [file (.getContainingFile element)
                node (.getNode file)
                block (create-block node settings)]
            (FormattingModelProvider/createFormattingModelForPsiFile file block settings))))
      (getRangeAffectingIndent [this file offset elementAtOffset] nil))))

;; debugging tools
(defn get-extension []
  (.forLanguage com.intellij.lang.LanguageFormatting/INSTANCE
                (org.jetbrains.plugins.clojure.ClojureLanguage/getInstance)))
