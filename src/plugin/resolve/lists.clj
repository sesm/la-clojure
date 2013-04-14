(ns plugin.resolve.lists
  (:import (org.jetbrains.plugins.clojure.psi.impl.list ListDeclarations)
           (org.jetbrains.plugins.clojure.psi.api ClList ClVector ClLiteral ClListLike ClMetadata ClKeyword ClMap)
           (org.jetbrains.plugins.clojure.psi.api.defs ClDef)
           (org.jetbrains.plugins.clojure.psi.impl.defs ClDefImpl)
           (org.jetbrains.plugins.clojure.psi.api.symbols ClSymbol)
           (org.jetbrains.plugins.clojure.psi.resolve ResolveUtil)
           (com.intellij.psi PsiNamedElement PsiElement)
           (org.jetbrains.plugins.clojure.psi.impl ImportOwner ClMapEntry))
  (:use [plugin.util :only [in? assoc-all safely]])
  (:require [plugin.resolve :as resolve]
            [plugin.psi :as psi]
            [clojure.string :as str]))

(def resolve-keys-key (psi/cache-key ::resolve-keys))
(def resolve-symbols-key (psi/cache-key ::resolve-symbols))

(def local-binding-forms [:clojure.core/let :clojure.core/with-open :clojure.core/with-local-vars :clojure.core/when-let :clojure.core/when-first :clojure.core/for :clojure.core/if-let :clojure.core/loop :clojure.core/doseq])

; TODO verify this list - these are probably not all correct
(def defn-forms [:def :clojure.core/defn :clojure.core/defn- :clojure.core/defmacro :clojure.core/defmethod :clojure.core/defmulti :clojure.core/defonce :clojure.core/defstruct :clojure.core/definline])

; Note that we use "true" to indicate "stop searching", not "continue searching"

(declare process-elements)

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

(defn process-element [processor element place]
  (cond
    (instance? ClVector element) (process-elements processor (psi/significant-children element) place)
    (instance? ClMap element) (reduce (fn [result ^ClMapEntry map-entry]
                                        (let [key (.getKey map-entry)
                                              value (.getValue map-entry)
                                              keyword-text (if (instance? ClKeyword key)
                                                             (.getText key))]
                                          (or (cond
                                                (= ":keys" keyword-text) (process-element processor
                                                                                          value
                                                                                          place)
                                                (= ":as" keyword-text) (process-element processor
                                                                                        value
                                                                                        place)
                                                (= ":or" keyword-text) false
                                                :else (process-element processor key place))
                                              result)))
                                      false
                                      (.getEntries ^ClMap element))
    (instance? ClDef element) (process-element processor (.getNameSymbol ^ClDef element) place)
    (instance? ClSymbol element) (not (or (= place element)
                                          (= "&" (.getText ^ClSymbol element))
                                          (ResolveUtil/processElement processor element)))
    :else false))

(defn process-elements [processor elements place]
  (reduce (fn [result item]
            (or (process-element processor item place)
                result))
          false
          elements))

(defn third [coll]
  (first (rest (rest coll))))

(defn fourth [coll]
  (first (rest (rest (rest coll)))))

(defn process-fn [list processor state last-parent place]
  (if (psi/contains? list place)
    (let [children (psi/significant-children list)
          second-item (second children)]
      (or
        ; Check fn name
        (and (instance? ClSymbol second-item)
             (not (= place second-item))
             (process-element processor second-item place))
        ; Check fn params
        (if-let [params (cond (instance? ClVector second-item) second-item
                              (and
                                (instance? ClSymbol (second children))
                                (instance? ClVector (third children))) (third children)
                              (and
                                (instance? ClSymbol (second children))
                                (instance? ClLiteral (third children))
                                (instance? ClVector (fourth children))) (fourth children)
                              :else nil)]
          (process-element processor params place))
        ; Check fn params for multi-arity fn
        (and (instance? ClList last-parent)
             (let [params (first (psi/significant-children last-parent))]
               (and (instance? ClVector params)
                    (process-element processor params place))))))))

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
                             {:scope     :local
                              :scoped-by element
                              :condition (fn [list element place]
                                           (or (psi/after? place v)
                                               (or-variable? place list)))}))
                {}
                (partition 2 children))))))

(defn process-defn [list processor state ^PsiElement last-parent place]
  (if (and (not (nil? last-parent))
           (= (.getParent last-parent) list))
    (process-fn list processor state last-parent place)
    (process-element processor list place)))

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

(defn process-memfn [^ClList list processor state last-parent place]
  (not (ListDeclarations/processMemFnDeclaration processor list place)))

(defn process-dot [^ClList list processor state last-parent place]
  (not (ListDeclarations/processDotDeclaration processor list place last-parent)))

(defn process-declare [^ClList list processor state last-parent place]
  (if-let [sym (second (psi/significant-children list))]
    (if (instance? ClSymbol sym)
      (process-element processor sym place))))

(defn to-keyword [^ClKeyword key]
  (keyword (.substring (.getName key) 1)))

(defn calculate-resolve-keys [^ClList element]
  (if-let [head-element (first (psi/significant-children element))]
    (cond
      (instance? ClSymbol head-element) (resolve/resolve-keys head-element)
      (instance? ClKeyword head-element) [(to-keyword head-element)]
      :else [])
    []))

(defn in-scope? [place scope scoped-by]
  (case scope
    :ns true
    :local (psi/contains? scoped-by place)
    true))

(defn resolve-symbol [element {:keys [scope scoped-by condition]
                               :or   {condition (constantly true)}} list processor state place]
  (or (= element place)
      (if (and (in-scope? place scope scoped-by)
               (condition list element place))
        (not (ResolveUtil/processElement processor element state))
        false)))

(defn resolve-from-symbols [key list processor state place]
  (let [calculator (resolve/get-symbols key)
        symbols (psi/cached-value list resolve-symbols-key calculator)]
    (reduce (fn [result [element params]]
              (or (resolve-symbol element params list processor state place)
                  result))
            false
            symbols)))

(defn resolve-keys [list]
  (psi/cached-value list resolve-keys-key calculate-resolve-keys))

(extend-type ClList
  resolve/Resolvable
  (process-declarations [this processor state last-parent place]
    (if-let [head-element (first (psi/significant-children this))]
      (if (= place head-element)
        false
        (let [keys (resolve-keys this)]
          (or (reduce (fn [return key]
                        (or (resolve-from-symbols key this processor state place)
                            return))
                      false
                      (filter resolve/has-symbols? keys))
              (reduce (fn [return key]
                        (or ((resolve/get-resolver key) this processor state last-parent place)
                            return))
                      false
                      (filter resolve/has-resolver? keys))))))))

(resolve/register-symbols local-binding-forms let-symbols)

(resolve/register-resolver :clojure.core/fn process-fn)
(resolve/register-resolver defn-forms process-defn)
(resolve/register-resolver :clojure.core/ns process-ns)
(resolve/register-resolver :clojure.core/import process-import)
(resolve/register-resolver :clojure.core/use process-use)
(resolve/register-resolver :clojure.core/refer process-refer)
(resolve/register-resolver :clojure.core/require process-require)
(resolve/register-resolver :clojure.core/memfn process-memfn)
(resolve/register-resolver :clojure.core/declare process-declare)
(resolve/register-resolver :. process-dot)
