(ns plugin.resolve.namespaces
  (:import (org.jetbrains.plugins.clojure.psi.api.ns ClNs)
           (org.jetbrains.plugins.clojure.psi.impl.ns ClNsImpl NamespaceUtil)
           (org.jetbrains.plugins.clojure.psi.impl ClojureFileImpl ImportOwner)
           (org.jetbrains.plugins.clojure.psi.api ClList ClQuotedForm)
           (org.jetbrains.plugins.clojure.psi.api.symbols ClSymbol))
  (:require [plugin.resolve :as resolve]
            [plugin.psi :as psi]
            [plugin.extension :as extension]
            [plugin.resolve.lists :as lists]))

;(set! *warn-on-reflection* true)

(def ns-forms [:clojure.core/ns :in-ns])

(defn process-synthetic-ns-decls [this processor state last-parent place]
  (not (org.jetbrains.plugins.clojure.psi.impl.ns.NamespaceUtil$MyClSyntheticNamespace/processDeclarations this processor state last-parent place)))

(defn process-completion-ns-decls [this processor state last-parent place]
  (not (org.jetbrains.plugins.clojure.psi.impl.ClojureFileImpl$CompletionSyntheticNamespace/processDeclarations this processor state)))

(defn symbol-ns [sym]
  (if-let [ancestor (psi/top-ancestor sym)]
    (some (fn [[list key]]
            (and (extension/has-extension? ::namespace-decl key)
                 ((extension/get-extension ::namespace-decl key) list)))
          (mapcat (fn [list]
                    (map (fn [key] [list key]) (lists/resolve-keys list)))
                  (filter #(psi/list? %) (cons ancestor (psi/prev-siblings ancestor)))))))

(defn declared-ns [^ClList list]
  (if-let [sym (second (psi/significant-children list))]
    (cond
      (psi/symbol? sym) (.getNameString ^ClSymbol sym)
      (psi/quoted? sym)
      (if-let [sym (first (psi/significant-children sym))]
        (if (psi/symbol? sym)
          (.getNameString ^ClSymbol sym))))))

(extension/register-extension ::namespace-decl ns-forms declared-ns)
