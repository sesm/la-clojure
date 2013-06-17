(ns plugin.resolve.lists
  "Symbol resolution infrastructure for list-like forms. Each list form
   that defines resolveable symbols should register (using
   plugin.resolve/register-symbols) a function returning a map of those
   symbols which will later be used for actual resolution. This map has
   the following structure:

   { <scope> { <name> <element> }
             { <name> { :element <element>
                        :condition <fn> }}
     <scope> ...etc etc... }

   The keys of the top layer in the map are the scopes of the symbols
   defined in their corresponding values. The scope can be either
   :ns (for namespace scope), :private (for namespace private scope) or
   can be an element, in which case that element defines the scope of the
   bindings. For example, a defn form would return a map like this:

   { :ns            { <defn name> <defn name element> }
     <defn element> { <param name> <param element>
                      <param name> <param element>
                      ... }}

   All elements in the above definitions are PsiElement instances. If more
   detail than just the element to resolve to is required, a map can be
   supplied instead. This map should contain:

   - an :element attribute indicating the element to resolve to
   - a :condition attribute, which should indicate a function to be
     which determines if the resolve is valid (optional)

   Additionally, if more than one symbol is defined with the same name (for
   example, shadowing let bindings), a vector of the symbol definitions can
   be supplied. These will be processed in reverse order to allow later
   bindings to shadow earlier ones."
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
    (instance? ClVector (first children))
    (let [bindings (sym-names (mapcat destructuring-symbols
                                      (psi/significant-children (first children))))]
      (assoc symbols element (merge (symbols element) bindings)))
    (instance? ClList (first children))
    (reduce (fn [symbols list]
              (let [params (first (psi/significant-children list))]
                (if (instance? ClVector params)
                  (let [bindings (sym-names (mapcat destructuring-symbols
                                                    (psi/significant-children params)))]
                    (assoc symbols list (merge (symbols list) bindings)))
                  symbols)))
            symbols
            (take-while #(instance? ClList %) children))
    :else symbols))

(defn name-symbol
  "Returns a symbol map for the name symbol of this def-like element.
   Scope defaults to :ns."
  ([element]
   (name-symbol element :ns))
  ([element scope]
   (let [children (psi/significant-children element)
         name-symbol (second children)]
     (if (instance? ClSymbol name-symbol)
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
         (instance? ClKeyword previous)
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
    (if (instance? ClVector params)
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

(defn resolve-binding [binding list processor state place]
  (if (instance? ClSymbol binding)
    (or (= binding place)
        (not (ResolveUtil/processElement processor binding state)))
    (let [{:keys [element condition]} binding]
      (or (= element place)
          (if (or (nil? condition)
                  (condition list element place))
            (not (ResolveUtil/processElement processor element state)))))))

(defn resolve-symbol [bindings list processor state place]
  (if-let [binding (bindings (name place))]
    (if (vector? binding)
      ; Process vector in reverse order so later bindings shadow earlier ones
      (some #(resolve-binding % list processor state place) (rseq binding))
      (resolve-binding binding list processor state place))))

; TODO disambiguate when we have more information from namespace elements
(defn resolve-keys [list]
  (if-let [head-element (first (psi/significant-children list))]
    (if (instance? ClSymbol head-element)
      (extension/list-keys-by-short-name (name head-element))
      [])
    []))

(defn calculate-resolve-symbols [list]
  (let [keys (filter resolve/has-symbols? (resolve-keys list))]
    (apply merge (map #((resolve/get-symbols %) list) keys))))

(defn get-resolve-symbols [list]
  (psi/cached-value list resolve-symbols-key calculate-resolve-symbols))

(defn resolve-from-symbols [list processor state place]
  (some (fn [[scope bindings]]
          (if (in-scope? place scope)
            (resolve-symbol bindings list processor state place)))
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
