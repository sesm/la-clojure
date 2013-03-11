(ns plugin.resolve.core
  (:import (org.jetbrains.plugins.clojure.psi.api ClQuotedForm ClojureFile)
           (com.intellij.psi ResolveResult PsiElement)
           (org.jetbrains.plugins.clojure.psi.api.symbols ClSymbol)))

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

(defn resolve-key [^ClSymbol item]
  (let [file (.getContainingFile item)]
    (if (instance? ClojureFile file)
      (if-let [namespace (.getNamespaceElement ^ClojureFile file)]
        (keyword (str (.getDefinedName namespace) "/" (.getName item)))
        (keyword (.getName item)))
      (keyword (.getName item)))))

(defn resolve-keys [^ClSymbol element]
  (let [elements (map #(.getElement ^ResolveResult %)
                      (seq (.multiResolve element false)))]
    (if (< 0 (count elements))
      (into #{} (map resolve-key elements))
      #{(keyword (.getName element))})))

(def resolvers (atom {}))

(defn has-resolver? [key]
  (not (nil? (get @resolvers key))))

(defn get-resolver [key]
  (get @resolvers key))

(defn register-resolver [key resolver]
  (if (sequential? key)
    (doseq [item key]
      (register-resolver item resolver))
    (swap! resolvers assoc key resolver)))
