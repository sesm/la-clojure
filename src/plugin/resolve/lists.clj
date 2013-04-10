(ns plugin.resolve.lists
  (:import (org.jetbrains.plugins.clojure.psi.impl.list ListDeclarations)
           (org.jetbrains.plugins.clojure.psi.api ClList ClVector ClLiteral ClListLike ClMetadata ClKeyword ClMap)
           (org.jetbrains.plugins.clojure.psi.api.defs ClDef)
           (org.jetbrains.plugins.clojure.psi.impl.defs ClDefImpl)
           (org.jetbrains.plugins.clojure.psi.api.symbols ClSymbol)
           (org.jetbrains.plugins.clojure.psi.resolve ResolveUtil)
           (com.intellij.psi PsiNamedElement PsiElement)
           (org.jetbrains.plugins.clojure.psi.impl ImportOwner ClMapEntry))
  (:require [plugin.resolve :as resolve]
            [plugin.psi :as psi]
            [clojure.string :as str]))

;(set! *warn-on-reflection* true)

(def local-binding-forms [:clojure.core/let :clojure.core/with-open :clojure.core/with-local-vars :clojure.core/when-let :clojure.core/when-first :clojure.core/for :clojure.core/if-let :clojure.core/loop :clojure.core/doseq])

; TODO verify this list - these are probably not all correct
(def defn-forms [:def :clojure.core/defn :clojure.core/defn- :clojure.core/defmacro :clojure.core/defmethod :clojure.core/defmulti :clojure.core/defonce :clojure.core/defstruct :clojure.core/definline])

; Note that we use "true" to indicate "stop searching", not "continue searching"

(defn elem [^PsiElement element]
  (str (.getText element) ":" (.getTextOffset element)))

(declare process-elements)

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

(defn process-params [processor ^ClListLike params ^PsiElement place last-parent]
  (process-elements processor (psi/significant-children params) place))

(defn third [coll]
  (first (rest (rest coll))))

(defn fourth [coll]
  (first (rest (rest (rest coll)))))

(defn offset-in [element ancestor]
  (loop [offset 0
         ^PsiElement current element]
    (if (= current ancestor)
      offset
      (recur (+ offset (.getStartOffsetInParent current))
             (.getParent current)))))

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

(defn process-let [list processor state last-parent place]
  (if (psi/contains? list place)
    (let [children (psi/significant-children list)
          params (second children)]
      (and (instance? ClVector params)
           (let [children (psi/significant-children params)]
             (if-not (psi/contains? params place)
               ; Basic case - place outside let bindings
               (process-elements processor (take-nth 2 children) place)
               ; More complex - place is within bindings
               (let [definitions (partition 2 children)]
                 (process-elements processor
                                   (map first
                                        (take-while #(not (psi/contains? (second %) place))
                                                    definitions))
                                   place))))))))

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
  (not (ListDeclarations/processDeclareDeclaration processor list place last-parent)))

(defn to-keyword [^ClKeyword key]
  (keyword (.substring (.getName key) 1)))

(defn calculate-resolve-keys [^ClList element]
  (if-let [head-element (first (psi/significant-children element))]
    (cond
      (instance? ClSymbol head-element) (resolve/resolve-keys head-element)
      (instance? ClKeyword head-element) [(to-keyword head-element)]
      :else [])
    []))

(extend-type ClList
  resolve/Resolvable
  (process-declarations [this processor state last-parent place]
    (if-let [head-element (first (psi/significant-children this))]
      (if (= place head-element)
        false
        (let [resolve-keys (psi/cached-value this calculate-resolve-keys)]
          (reduce (fn [return key]
                    (or ((resolve/get-resolver key) this processor state last-parent place)
                        return))
                  false
                  (filter resolve/has-resolver? resolve-keys)))))))

(resolve/register-resolver local-binding-forms process-let)
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
