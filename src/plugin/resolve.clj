(ns plugin.resolve
  (:import (org.jetbrains.plugins.clojure.psi.api ClQuotedForm ClojureFile)
           (com.intellij.psi ResolveResult PsiElement PsiNamedElement)
           (org.jetbrains.plugins.clojure.psi.api.symbols ClSymbol)
           (org.jetbrains.plugins.clojure.psi ClojureConsoleElement))
  (:require [plugin.extension :as extension]))

;(set! *warn-on-reflection* true)

(defprotocol Resolvable
  (process-declarations [this processor state last-parent place]))

(defn punt [element processor state last-parent place]
  (if (satisfies? Resolvable element)
    (process-declarations element processor state last-parent place)
    true))

(extend-type ClQuotedForm
  Resolvable
  (process-declarations [this processor state last-parent place]
    false))

(defn resolve-key [^PsiNamedElement item]
  (if (instance? ClojureConsoleElement item)
    (.getResolveKey ^ClojureConsoleElement item)
    (let [file (.getContainingFile item)]
      (if (instance? ClojureFile file)
        (if-let [namespace (.getNamespaceElement ^ClojureFile file)]
          (keyword (str (.getDefinedName namespace) "/" (.getName item)))
          (keyword (.getName item)))
        (keyword (.getName item))))))

(defn resolve-keys [^ClSymbol element]
  (if (or (.isQualified element)
          (let [name (.getNameString element)]
            (not (or (.startsWith name ".")
                     (.endsWith name ".")))))
    (let [elements (map #(.getElement ^ResolveResult %)
                        (seq (.multiResolve element false)))]
      (if (< 0 (count elements))
        (into #{} (map resolve-key elements))
        #{(keyword (.getName element))}))
    #{}))

(def resolvers (atom {}))

(defn has-resolver? [key]
  (extension/has-extension? ::resolver key))

(defn get-resolver [key]
  (extension/get-extension ::resolver key))

(defn register-resolver [key resolver]
  (extension/register-extension ::resolver key resolver))

(defn has-symbols? [key]
  (extension/has-extension? ::symbols-resolver key))

(defn get-symbols [key]
  (extension/get-extension ::symbols-resolver key))

(defn register-symbols [key symbols-resolver]
  (extension/register-extension ::symbols-resolver key symbols-resolver))
