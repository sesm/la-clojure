(ns plugin.predicates
  (:import (com.intellij.lang ASTNode)
           (com.intellij.psi.impl.source.tree LeafPsiElement))
  (:use [plugin.tokens]
        [plugin.util :only [safely]]))

(defn formattable? [^ASTNode node]
  (and (non-empty? node)
       (let [element (.getElementType node)]
         (not (or (whitespace element)
                  (metadata? element)
                  (comments element))))))

(defn significant? [^ASTNode node]
  (and (formattable? node)
       (not (instance? LeafPsiElement node))))

(defn significant-elements [^ASTNode node]
  (filter significant? (seq (.getChildren node nil))))

(defn matches? [node & predicates]
  (every? true? (map #(% node) predicates)))

(defn list-like? [^ASTNode node]
  (not (nil? (list-like-forms (.getElementType node)))))

(defn list-like-parent? [& predicates]
  (fn [^ASTNode node]
    (let [parent (.getTreeParent node)
          element-type (safely (.getElementType parent))]
      (if (list-like-forms element-type)
        (every? true? (map #(% parent) predicates))))))

(defn head-text? [text]
  (fn [^ASTNode node]
    (let [head ^ASTNode (first (significant-elements node))
          head-text (safely (.getText head))]
      (= head-text text))))

(defn head-text-in? [& options]
  (fn [^ASTNode node]
    (let [head ^ASTNode (first (significant-elements node))
          head-text (safely (.getText head))]
      (loop [items options]
        (cond
          (empty? items) false
          (= head-text (first items)) true
          :else (recur (rest items)))))))

(defn symbol-head? [node]
  (let [head ^ASTNode (first (significant-elements node))]
    (if (nil? head) false (symbol-token? (.getElementType head)))))

(defn keyword-head? [node]
  (let [head ^ASTNode (first (significant-elements node))]
    (if (nil? head) false (keyword-token? (.getElementType head)))))
