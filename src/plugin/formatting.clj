(ns plugin.formatting
  (:import (com.intellij.formatting FormattingModelBuilder
                                    FormattingModelProvider
                                    Indent
                                    Block
                                    Spacing
                                    ChildAttributes
                                    Alignment)
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
   (java.util Collection ArrayList)))

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

(defrecord ClojureBlock [^ASTNode node alignment indent wrap settings ^Collection children] Block
  (getTextRange [this] (.getTextRange node))
  (getSubBlocks [this]
                (if (.isEmpty children)
                  (.addAll children (sub-blocks this)))
                children)
  (getWrap [this] wrap)
  (getIndent [this] indent)
  (getAlignment [this] alignment)
  (getSpacing [this child1 child2] (spacing child1 child2))
  (getChildAttributes [this newChildIndex] (child-attributes this newChildIndex))
  (isIncomplete [this] (incomplete? node))
  (isLeaf [this] (nil? (.getFirstChildNode node))))

(defn no-indent [] (Indent/getNoneIndent))
(defn absolute-no-indent [] (Indent/getAbsoluteNoneIndent))
(defn normal-indent [] (Indent/getNormalIndent true))
(defn continuation-indent [] (Indent/getContinuationIndent true))

(defn create-alignment [] (Alignment/createAlignment))
(defn shifting-alignment [] (Alignment/createAlignment true))
(defn child-alignment [parent] (Alignment/createChildAlignment parent))

(defn brace? [element] (.contains ClojureElementTypes/BRACES element))

(def opening-braces #{ClojureTokenTypes/LEFT_PAREN
                      ClojureTokenTypes/LEFT_SQUARE
                      ClojureTokenTypes/LEFT_CURLY})

(def comments #{ClojureTokenTypes/LINE_COMMENT})

(def whitespace #{ClojureTokenTypes/EOL
                  ClojureTokenTypes/EOF
                  ClojureTokenTypes/WHITESPACE
                  ClojureTokenTypes/COMMA})

(defn opening-brace? [element] (opening-braces element))
(defn comment? [element] (comments element))
(defn whitespace? [element] (whitespace element))
(defn meta-form? [element] (= ClojureElementTypes/META_FORM element))

(defn brace-params [] {:indent (no-indent)})

(defn create-params [align indent]
  (let [child (if (nil? align) nil (child-alignment align))]
    {:alignment align, :child child, :indent indent}))

(defn body-params []
  (create-params (create-alignment) (normal-indent)))

(defn head-params []
  (create-params nil (normal-indent)))

(defn parameter-params []
  (create-params (create-alignment) (continuation-indent)))

(defn shifting-params []
  (create-params (shifting-alignment) (normal-indent)))

(defn file-params [] (repeat {:indent (absolute-no-indent)}))

(defn normal-params []
  (concat [(brace-params)]
          (repeat (body-params))))

(defn application-params [num-parameters]
  (concat [(brace-params) (head-params)]
          (repeat num-parameters (parameter-params))
          (repeat (body-params))))

; TODO fix lexer to lex #{ as single token
(defn set-params []
  (concat (repeat 2 (brace-params))
          (repeat (body-params))))

(defn map-params []
  (concat [(brace-params)]
          (cycle [(body-params) (shifting-params)])))

(defn non-empty? [^ASTNode node] (> (.length (.trim (.getText node))) 0))

(defn non-empty-children [^ASTNode node]
  (filter non-empty?
          (flatten
            (map
              (fn [^ASTNode node] (if (instance? ClMapEntry (.getPsi node))
                                    (seq (.getChildren node nil))
                                    node))
              (seq (.getChildren node nil))))))

(defn significant-elements [^ASTNode node]
  (filter (fn [node]
            (and (non-empty? node)
                 (let [element (.getElementType node)]
                   (not (or (whitespace? element)
                            (meta-form? element)
                            (comment? element)
                            (instance? LeafPsiElement node))))))
          (seq (.getChildren node nil))))

(defn has-types? [^ASTNode node types]
  (let [elements (map #(.getElementType %) (significant-elements node))]
    (every? true? (map = elements types))))

(defn defn-parameters [^ASTNode node]
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

(def indent-form {:ns        1,
                  :let       1,
                  :if-let    1,
                  :when-let  1,
                  :with-open 1,
                  :binding   1,
                  :defmethod 3,
                  :defn      defn-parameters,
                  :defmacro  defn-parameters,
                  :definline defn-parameters,
                  :defn-     defn-parameters,
                  :fn        fn-parameters,
                  :defrecord 3,
                  :assoc     1,
                  :loop      1,
                  :deftest   1,
                  :if        1,
                  :if-not    1,
                  :when      1,
                  :when-not  1,
                  :doseq     1,
                  :dotimes   1,
                  :catch     2})

(defn num-parameters [^ASTNode node]
  (let [psi (.getPsi node)
        head (.getFirstNonLeafElement ^ClList psi)]
    (if (instance? ClSymbol head)
      (let [indent (get indent-form (keyword (.getText head)) 0)]
        (if (fn? indent)
          (indent node)
          indent))
      0)))

(defn get-params [^ASTNode node]
  (let [psi (.getPsi node)]
    (cond
      (instance? ClList psi) (let [first (.getFirstNonLeafElement ^ClList psi)]
                               (if (instance? ClSymbol first)
                                 (application-params (num-parameters node))
                                 (normal-params)))
      (instance? ClVector psi) (normal-params)
      (instance? ClSet psi) (set-params)
      (instance? ClMap psi) (map-params)
      (instance? PsiFile psi) (file-params))))

(defn sub-blocks
  ([block] (sub-blocks block (get-params (:node block))))
  ([block params]
   (loop [children (non-empty-children (:node block))
          child-params params
          result []]
     (if (seq children)
       (let [child (first children)
             element (.getElementType ^ASTNode child)
             params (first child-params)]
         (if (or (comment? element)
                 (whitespace? element)
                 (meta-form? element))
           (recur (rest children)
                  child-params
                  (conj result (ClojureBlock. child
                                              (cond
                                                (whitespace? element) nil
                                                (meta-form? element) (:alignment params)
                                                :else (:child params))
                                              (:indent params)
                                              (:wrap params)
                                              (:settings block)
                                              (ArrayList.))))
           (recur (rest children)
                  (rest child-params)
                  (conj result (ClojureBlock. child
                                              (:alignment params)
                                              (:indent params)
                                              (:wrap params)
                                              (:settings block)
                                              (ArrayList.))))))
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
        (ClojurePsiCheckers/isNs psi1) ns-spacing
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
  (let [params (get-params (:node block))
        item-params (nth params index)]
    (ChildAttributes. (:indent item-params) (:alignment item-params))))

(defn incomplete? [node] false)

(defrecord ClojureFormattingModelBuilder [] FormattingModelBuilder
  (createModel [this element settings]
               (let [file (.getContainingFile element)
                     node (.getNode file)
                     block (ClojureBlock. node nil nil nil settings (ArrayList.))]
                 (FormattingModelProvider/createFormattingModelForPsiFile file block settings)))
  (getRangeAffectingIndent [this file offset elementAtOffset] nil))

(defn initialise []
  (.addExplicitExtension
    com.intellij.lang.LanguageFormatting/INSTANCE
    (org.jetbrains.plugins.clojure.ClojureLanguage/getInstance)
    (ClojureFormattingModelBuilder.)))

;; debugging tools
(defn get-extension []
  (.forLanguage com.intellij.lang.LanguageFormatting/INSTANCE
                (org.jetbrains.plugins.clojure.ClojureLanguage/getInstance)))
