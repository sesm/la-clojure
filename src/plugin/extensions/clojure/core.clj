(ns plugin.extensions.clojure.core
  (:import (org.jetbrains.plugins.clojure.psi.api ClList ClVector ClKeyword ClMap ClLiteral)
           (org.jetbrains.plugins.clojure.psi.impl ImportOwner ClMapEntry)
           (com.intellij.psi PsiElement)
           (org.jetbrains.plugins.clojure.psi.api.symbols ClSymbol))
  (:use [plugin.util :only [safely]])
  (:require [plugin.resolve :as resolve]
            [plugin.psi :as psi]))

(def local-binding-forms [:clojure.core/let :clojure.core/with-open :clojure.core/with-local-vars
                          :clojure.core/when-let :clojure.core/when-first :clojure.core/for :clojure.core/if-let
                          :clojure.core/loop :clojure.core/doseq :clojure.core/dotimes])

; TODO verify this list - these are probably not all correct
(def defn-forms [:clojure.core/defn :clojure.core/defn- :clojure.core/defmacro
                 :clojure.core/defmethod :clojure.core/definline])
(def fn-forms [:clojure.core/fn :fn*])
(def name-only-forms [:def :clojure.core/declare :clojure.core/defmulti :clojure.core/defonce
                      :clojure.core/defstruct])

; Note that we use "true" to indicate "stop searching", not "continue searching"

(defn destructuring-symbols
  ([element]
   (destructuring-symbols element []))
  ([element symbols]
   (if (sequential? element)
     (reduce (fn [symbols element]
               (destructuring-symbols element symbols))
             symbols
             element)
     (cond
       (psi/vector? element) (destructuring-symbols (psi/significant-children element) symbols)
       (psi/map? element) (reduce (fn [symbols ^ClMapEntry map-entry]
                                    (let [key (.getKey map-entry)
                                          value (.getValue map-entry)
                                          keyword-text (if (psi/keyword? key)
                                                         (.getText key))]
                                      (cond
                                        (= ":keys" keyword-text)
                                        (destructuring-symbols value symbols)
                                        (= ":as" keyword-text)
                                        (destructuring-symbols value symbols)
                                        (= ":or" keyword-text)
                                        symbols
                                        :else (destructuring-symbols key symbols))))
                                  symbols
                                  (.getEntries ^ClMap element))
       (psi/symbol? element) (conj symbols element)
       :else symbols))))

(defn drop-instance [seq type]
  (if (instance? type (first seq))
    (drop 1 seq)
    seq))

(defn sym-names
  "Takes a collection of symbols and returns a map with the symbol
  names as keys, and the symbols themselves as values."
  [symbols]
  (reduce (fn [ret symbol]
            (assoc ret (name symbol) symbol))
          {}
          symbols))

(defn with-fn-arg-symbols [symbols children element]
  (cond
    (psi/vector? (first children))
    (let [params (first children)
          bindings (sym-names (mapcat destructuring-symbols
                                      (psi/significant-children params)))
          scope {:scope element :after params}]
      (assoc symbols scope (merge (symbols scope) bindings)))
    (psi/list? (first children))
    (reduce (fn [symbols list]
              (let [params (first (psi/significant-children list))]
                (if (psi/vector? params)
                  (let [bindings (sym-names (mapcat destructuring-symbols
                                                    (psi/significant-children params)))
                        scope {:scope list :after params}]
                    (assoc symbols scope (merge (symbols scope) bindings)))
                  symbols)))
            symbols
            (take-while #(psi/list? %) children))
    :else symbols))

(defn name-symbol
  "Returns a symbol map for the name symbol of this def-like element.
   Scope defaults to :ns."
  ([element]
   (name-symbol element :ns))
  ([element scope]
   (let [children (psi/significant-children element)
         name-symbol (second children)]
     (if (psi/symbol? name-symbol)
       {scope {(name name-symbol) name-symbol}}
       {}))))

(defn defn-symbols [element]
  (let [symbols (name-symbol element)
        children (-> (drop 1 (psi/significant-children element))
                     (drop-instance ClSymbol)
                     (drop-instance ClLiteral)
                     (drop-instance ClMap))]
    (with-fn-arg-symbols symbols children element)))

(defn fn-symbols [element]
  (let [symbols (name-symbol element element)
        children (-> (drop 1 (psi/significant-children element))
                     (drop-instance ClSymbol))]
    (with-fn-arg-symbols symbols children element)))

(defn or-variable?
  "true if place is a variable in a destructuring :or clause within
  the let-bindings of list."
  [place list]
  (let [parent (psi/parent place)
        grandparent (safely (psi/parent parent))
        previous (first (filter psi/significant? (safely (psi/prev-siblings grandparent))))]
    (and (instance? ClMapEntry parent)
         (= place (.getKey ^ClMapEntry parent))
         (psi/keyword? previous)
         (= ":or" (.getText ^PsiElement previous))
         (psi/contains? list place))))

(defn assoc-symbol
  ([symbols symbol]
   (assoc-symbol symbols symbol symbol))
  ([symbols symbol value]
   (let [name (name symbol)]
     (assoc symbols name (if-let [binding (symbols name)]
                           (if (vector? binding)
                             (conj binding value)
                             [binding value])
                           value)))))

(defn let-symbols [^ClList element]
  (let [children (psi/significant-children element)
        params (second children)]
    (if (psi/vector? params)
      (let [children (psi/significant-children params)]
        {element (reduce (fn [symbols [k v]]
                           (reduce (fn [symbols symbol]
                                     (assoc-symbol symbols symbol {:element   symbol
                                                                   :condition (fn [list element place]
                                                                                (or (psi/after? place v)
                                                                                    (or-variable? place list)))}))
                                   symbols
                                   (destructuring-symbols k)))
                         {}
                         (partition 2 children))}))))

(defn def-symbols [list]
  (name-symbol list))

(defn process-ns [^ClList list processor state last-parent place]
  (not (ImportOwner/processDeclarations list processor place)))

(defn process-import [^ClList list processor state last-parent place]
  (not (ImportOwner/processImports processor place list (.getHeadText list))))

(defn process-use [^ClList list processor state last-parent place]
  (not (ImportOwner/processUses processor place list (.getHeadText list))))

(defn process-refer [^ClList list processor state last-parent place]
  (not (ImportOwner/processRefer processor place list (.getHeadText list))))

(defn process-require [^ClList list processor state last-parent place]
  (not (ImportOwner/processRequires processor place list (.getHeadText list))))

(resolve/register-symbols local-binding-forms let-symbols)
(resolve/register-symbols fn-forms fn-symbols)
(resolve/register-symbols defn-forms defn-symbols)
(resolve/register-symbols name-only-forms def-symbols)

(resolve/register-resolver :clojure.core/ns process-ns)
(resolve/register-resolver :clojure.core/import process-import)
(resolve/register-resolver :clojure.core/use process-use)
(resolve/register-resolver :clojure.core/refer process-refer)
(resolve/register-resolver :clojure.core/require process-require)
