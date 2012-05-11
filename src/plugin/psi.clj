(ns plugin.psi
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

(defn common-parent [first second]
  (PsiTreeUtil/findCommonParent first second))

(defn contains? [container containee]
  (= container (common-parent containee container)))
