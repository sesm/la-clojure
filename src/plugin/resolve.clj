(ns plugin.resolve
  (:import (org.jetbrains.plugins.clojure.psi.api ClQuotedForm ClojureFile)
           (com.intellij.psi ResolveResult PsiElement PsiNamedElement)
           (org.jetbrains.plugins.clojure.psi.api.symbols ClSymbol)
           (org.jetbrains.plugins.clojure.psi ClojureConsoleElement))
  (:require [plugin.extension :as extension]))

;(set! *warn-on-reflection* true)

(def resolvers (atom {}))

(defn has-resolver? [key]
  (extension/has-extension? ::resolver key))

(defn get-resolver [key]
  (extension/get-extension ::resolver key))

(defn register-resolver [key resolver]
  (extension/register-list-extension ::resolver key resolver))

(defn has-symbols? [key]
  (extension/has-extension? ::symbols-resolver key))

(defn get-symbols [key]
  (extension/get-extension ::symbols-resolver key))

(defn register-symbols [key symbols-resolver]
  (extension/register-list-extension ::symbols-resolver key symbols-resolver))