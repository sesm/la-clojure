(ns plugin.resolve.lists
  (:import (org.jetbrains.plugins.clojure.psi.api ClList ClVector ClLiteral ClListLike ClMetadata ClKeyword ClMap)
           (org.jetbrains.plugins.clojure.psi.api.defs ClDef)
           (org.jetbrains.plugins.clojure.psi.impl.defs ClDefImpl)
           (org.jetbrains.plugins.clojure.psi.api.symbols ClSymbol)
           (org.jetbrains.plugins.clojure.psi.resolve ResolveUtil)
           (com.intellij.psi PsiNamedElement PsiElement)
           (org.jetbrains.plugins.clojure.psi.impl ImportOwner ClMapEntry))
  (:use [plugin.util :only [in? assoc-all safely]])
  (:require [plugin.resolve :as resolve]
            [plugin.psi :as psi]
            [clojure.string :as str]
            [plugin.extension :as extension]))

(def resolve-keys-key (psi/cache-key ::resolve-keys))
(def resolve-symbols-key (psi/cache-key ::resolve-symbols))

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
       (instance? ClVector element) (destructuring-symbols (psi/significant-children element) symbols)
       (instance? ClMap element) (reduce (fn [symbols ^ClMapEntry map-entry]
                                           (let [key (.getKey map-entry)
                                                 value (.getValue map-entry)
                                                 keyword-text (if (instance? ClKeyword key)
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
       (instance? ClSymbol element) (conj symbols element)
       :else symbols))))

(defn drop-instance [seq type]
  (if (instance? type (first seq))
    (drop 1 seq)
    seq))

(defn with-fn-arg-symbols [symbols children element]
  (cond
    (instance? ClVector (first children))
    (assoc-all symbols
               (mapcat destructuring-symbols
                       (psi/significant-children (first children)))
               {:scope element})
    (instance? ClList (first children))
    (reduce (fn [symbols list]
              (let [params (first (psi/significant-children list))]
                (if (instance? ClVector params)
                  (assoc-all symbols
                             (mapcat destructuring-symbols
                                     (psi/significant-children params))
                             {:scope list})
                  symbols)))
            symbols
            (take-while #(instance? ClList %) children))
    :else symbols))

(defn name-symbol
  ([element]
   (name-symbol element {:scope :ns}))
  ([element params]
   (let [children (psi/significant-children element)
         name-symbol (second children)]
     (if (instance? ClSymbol name-symbol)
       {name-symbol params}
       {}))))

(defn defn-symbols [element]
  (let [symbols (name-symbol element)
        children (-> (drop 1 (psi/significant-children element))
                     (drop-instance ClSymbol)
                     (drop-instance ClLiteral)
                     (drop-instance ClMap))]
    (with-fn-arg-symbols symbols children element)))

(defn fn-symbols [element]
  (let [symbols (name-symbol element {:scope element})
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
         (instance? ClKeyword previous)
         (= ":or" (.getText ^PsiElement previous))
         (psi/contains? list place))))

(defn let-symbols [^ClList element]
  (let [children (psi/significant-children element)
        params (second children)]
    (if (instance? ClVector params)
      (let [children (psi/significant-children params)]
        (reduce (fn [symbols [k v]]
                  (assoc-all symbols
                             (destructuring-symbols k)
                             {:scope     element
                              :condition (fn [list element place]
                                           (or (psi/after? place v)
                                               (or-variable? place list)))}))
                {}
                (partition 2 children))))))

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

(defn to-keyword [^ClKeyword key]
  (let [name (.getName key)
        length (count name)]
    (loop [index 0]
      (if (and (< index length)
               (= (get name index) \:))
        (recur (inc index))
        (keyword (.substring name index))))))

(defn in-scope? [place scope]
  (cond
    (= scope :ns) true
    (instance? PsiElement scope) (psi/contains? scope place)
    :else true))

(defn resolve-symbol [element {:keys [scope condition]
                               :or   {condition (constantly true)}} list processor state place]
  (or (= element place)
      (if (and (in-scope? place scope)
               (condition list element place))
        (not (ResolveUtil/processElement processor element state))
        false)))

(defn local-name [^ClSymbol sym]
  (if (.isQualified sym)
    (-> (.getSeparatorToken sym)
        .getNextSibling
        .getText)
    (.getName sym)))

; TODO disambiguate when we have more information from namespace elements
(defn resolve-keys [list]
  (if-let [head-element (first (psi/significant-children list))]
    (if (instance? ClSymbol head-element)
      (extension/list-keys-by-short-name (local-name head-element))
      [])
    []))

(defn calculate-resolve-symbols [list]
  (let [keys (filter resolve/has-symbols? (resolve-keys list))]
    (apply merge (map #((resolve/get-symbols %) list) keys))))

(defn get-resolve-symbols [list]
  (psi/cached-value list resolve-symbols-key calculate-resolve-symbols))

(defn resolve-from-symbols [list processor state place]
  (some (fn [[element params]]
          (resolve-symbol element params list processor state place))
        (get-resolve-symbols list)))

(defn process-list-declarations [this processor state last-parent place]
  (if-let [head-element (first (psi/significant-children this))]
    (if (= place head-element)
      false
      (let [keys (resolve-keys this)]
        (if (some resolve/has-symbols? keys)
          (resolve-from-symbols this processor state place)
          (some (fn [key]
                  ((resolve/get-resolver key) this processor state last-parent place))
                (filter resolve/has-resolver? keys)))))))

(resolve/register-symbols local-binding-forms let-symbols)
(resolve/register-symbols fn-forms fn-symbols)
(resolve/register-symbols defn-forms defn-symbols)
(resolve/register-symbols name-only-forms def-symbols)

(resolve/register-resolver :clojure.core/ns process-ns)
(resolve/register-resolver :clojure.core/import process-import)
(resolve/register-resolver :clojure.core/use process-use)
(resolve/register-resolver :clojure.core/refer process-refer)
(resolve/register-resolver :clojure.core/require process-require)
