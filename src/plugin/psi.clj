(ns plugin.psi
  (:refer-clojure :exclude [contains?])
  (:import (org.jetbrains.plugins.clojure.psi.impl ClMetaForm)
           (org.jetbrains.plugins.clojure.psi.api ClMetadata)
           (com.intellij.psi PsiComment PsiWhiteSpace)
           (com.intellij.psi.impl.source.tree LeafPsiElement)
           (com.intellij.psi.util PsiTreeUtil)))

(defn significant? [element]
  (not (or (nil? element)
           (instance? LeafPsiElement element)
           (instance? PsiWhiteSpace element)
           (instance? PsiComment element)
           (instance? ClMetadata element)
           (instance? ClMetaForm element))))

(defn significant-children [element]
  (filter significant? (.getChildren element)))

(defn significant-offset [element]
  (loop [offset 0
         current (.getPrevSibling element)]
    (cond
      (nil? current) offset
      (significant? current) (recur (inc offset) (.getPrevSibling current))
      :else (recur offset (.getPrevSibling current)))))

(defn common-parent [first second]
  (PsiTreeUtil/findCommonParent first second))

(defn contains? [container containee]
  (= container (common-parent containee container)))
