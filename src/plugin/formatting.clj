(ns plugin.formatting
  (:import (com.intellij.formatting FormattingModelBuilder
                                    FormattingModelProvider
                                    Indent
                                    Block
                                    Spacing
                                    ChildAttributes
                                    Alignment)
   (com.intellij.openapi.diagnostic Logger)
   (org.jetbrains.plugins.clojure.psi.api ClList ClListLike ClVector ClKeyword)
   (org.jetbrains.plugins.clojure.psi.api.symbols ClSymbol)
   (org.jetbrains.plugins.clojure.psi.util ClojurePsiCheckers)
   (org.jetbrains.plugins.clojure.parser ClojureElementTypes)
   (org.jetbrains.plugins.clojure.lexer ClojureTokenTypes)))

(def logger (Logger/getInstance "plugin.formatting"))

(def no-spacing (Spacing/createSpacing 0 0 0 false 0))
(def no-spacing-with-newline (Spacing/createSpacing 0 0 0 true 1))
(def mandatory-newline (Spacing/createSpacing 1 1 1 true 100))
(def ns-spacing (Spacing/createSpacing 1 1 2 true 100))
(def common-spacing (Spacing/createSpacing 1 1 0 true 100))
(def no-newline (Spacing/createSpacing 1 1 0 false 0))

(defmulti sub-blocks :type )
(defmulti child-attributes :type )

(def spacing)
(def incomplete?)

(defrecord ClojureBlock [type node alignment indent wrap settings] Block
  (getTextRange [this] (.getTextRange node))
  (getSubBlocks [this] (sub-blocks this))
  (getWrap [this] wrap)
  (getIndent [this] indent)
  (getAlignment [this] (:this alignment))
  (getSpacing [this child1 child2] (spacing child1 child2))
  (getChildAttributes [this newChildIndex] (child-attributes this newChildIndex))
  (isIncomplete [this] (incomplete? node))
  (isLeaf [this] (nil? (.getFirstChildNode node))))

(defn no-indent [] (Indent/getNoneIndent))
(defn absolute-no-indent [] (Indent/getAbsoluteNoneIndent))
(defn normal-indent [] (Indent/getNormalIndent true))
(defn continuation-indent [] (Indent/getContinuationIndent true))

(defn create-alignment [] (Alignment/createAlignment))
(defn child-alignment [parent] (Alignment/createChildAlignment parent))

(defn brace? [element] (.contains ClojureElementTypes/BRACES element))
(defn comment? [element] (.contains ClojureTokenTypes/COMMENTS element))

(defn create-block [node alignment indent wrap settings]
  (let [psi (.getPsi node)]
    (cond
      (instance? ClList psi)
      (let [first (.getFirstNonLeafElement psi)]
        (if (instance? ClSymbol first)
            (let [parameter (create-alignment)
                  parameter-child (child-alignment parameter)
                  body (create-alignment)
                  body-child (child-alignment body)
                  application-alignment (assoc alignment :parameter parameter
                                                         :parameter-child parameter-child
                                                         :body body
                                                         :body-child body-child)]
              (ClojureBlock. :application node application-alignment indent wrap settings))
            (ClojureBlock. :list node (assoc alignment :child (create-alignment)) indent wrap settings)))
      (instance? ClVector psi)
      (ClojureBlock. :list node (assoc alignment :child (create-alignment)) indent wrap settings)
      :else (ClojureBlock. :basic node alignment indent wrap settings))))

(defn non-empty? [node] (> (.length (.trim (.getText node))) 0))

(defmethod sub-blocks :basic [block]
  (let [sub-block #(create-block % {} (no-indent) (:wrap block) (:settings block))]
    (java.util.ArrayList.
      (into [] (map sub-block
                    (filter non-empty?
                            (seq (.getChildren (:node block) nil))))))))

(defmethod sub-blocks :list [block]
  (let [sub-block #(let [brace? (brace? (.getElementType %))
                         indent (if brace? (no-indent) (normal-indent))
                         align (if brace? {} {:this (:child (:alignment block))})]
                     (create-block % align indent (:wrap block) (:settings block)))]
    (java.util.ArrayList.
      (into [] (map sub-block
                    (filter non-empty?
                            (seq (.getChildren (:node block) nil))))))))

(def indent-form {:ns 1, :let 1, :defmethod 3, :defn 2, :defrecord 3, :assoc 1, :loop 1})

(defn num-parameters [block]
  (let [psi (.getPsi (:node block))
        head (.getFirstNonLeafElement psi)]
    (if (instance? ClSymbol head)
        (get indent-form (keyword (.getText head)) 0)
        0)))

(defmethod sub-blocks :application [block]
  (let [parameters (num-parameters block)]
    (loop [children (filter non-empty? (seq (.getChildren (:node block) nil)))
           index 0
           result []]
      (if (seq children)
          (let [child (first children)
                element (.getElementType child)
                increment (if (comment? element) 0 1)
                params (cond
                         (brace? element) [index {} (no-indent)]
                         (= index 0) [(+ index increment) {} (normal-indent)]
                         (< (dec index) parameters) [(+ index increment)
                                                     {:this ((if (comment? element) :parameter-child :parameter )
                                                             (:alignment block))}
                                                     (continuation-indent)]
                         :else [(+ index increment)
                                {:this ((if (comment? element) :body-child :body )
                                        (:alignment block))}
                                (normal-indent)])]
            (recur (rest children)
                   (params 0)
                   (conj result (create-block child (params 1) (params 2) (:wrap block) (:settings block)))))
          (java.util.ArrayList. result)))))

(defn spacing [child1 child2]
  (if (and (instance? ClojureBlock child1)
           (instance? ClojureBlock child2))
      (let [node1 (:node child1)
            node2 (:node child2)
            type1 (.getElementType node1)
            type2 (.getElementType node2)
            psi1 (.getPsi node1)
            psi2 (.getPsi node2)]
        (cond
          (ClojurePsiCheckers/isNs psi1) ns-spacing
          (ClojurePsiCheckers/isImportingClause psi2) mandatory-newline
          (instance? ClKeyword psi1) no-newline
          (and (instance? ClListLike psi1)
               (instance? ClListLike psi2)
               (= (.getParent psi1) (.getParent psi2))
               (ClojurePsiCheckers/isImportingClause (.getParent psi1))) mandatory-newline
          (.contains ClojureElementTypes/MODIFIERS type1) no-spacing
          (.contains ClojureTokenTypes/ATOMS type2) no-spacing
          (= "," (.getText node2)) no-spacing
          (or (brace? type1)
              (brace? type2)) no-spacing-with-newline
          :else common-spacing))))

(defmethod child-attributes :default [block index]
  (ChildAttributes. (no-indent) nil))

(defmethod child-attributes :list [block index]
  (ChildAttributes. (no-indent) (:child (:alignment block))))

(defmethod child-attributes :application [block index]
  (let [parameters (num-parameters block)]
    (cond
      (= index 1) (ChildAttributes. (normal-indent) nil)
      (< index parameters) (ChildAttributes. (continuation-indent) (:parameter (:alignment block)))
      :else (ChildAttributes. (normal-indent) (:body (:alignment block))))))

(defn incomplete? [node] false)

(defrecord ClojureFormattingModelBuilder [] FormattingModelBuilder
  (createModel [this element settings]
               (let [file (.getContainingFile element)
                     node (.getNode file)
                     block (ClojureBlock. :basic node {} (absolute-no-indent) nil settings)]
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
