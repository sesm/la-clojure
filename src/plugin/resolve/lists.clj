(ns plugin.resolve.lists
  "Symbol resolution infrastructure for list-like forms. Each list form
   that defines resolveable symbols should register (using
   plugin.resolve/register-symbols) a function returning a map of those
   symbols which will later be used for actual resolution. This map has
   the following structure:

   { <scope> { <name> <binding> }
     <scope> ...etc etc... }

   Where:

   <scope> = :ns |
             :private |
             <element> |
             { :scope <scope> :after <element> }

   and:

   <binding> = <element> |
               { :element <element> :condition <fn> } |
               [ <binding> ... ]

   The keys of the top layer in the map are the scopes of the symbols
   defined in their corresponding values. The scope can be either
   :ns (for namespace scope), :private (for namespace private scope) or
   can be an element, in which case that element defines the scope of the
   bindings. For example, a defn form would return a map like this:

   { :ns            { <defn name> <defn name element> }
     <defn element> { <param name> <param element>
                      <param name> <param element>
                      ... }}

   The scope may also be a map containing the scope itself in the :scope
   attribute, and an element in the :after attribute which restricts the
   scope to elements following the given element.

   All elements in the above definitions are PsiElement instances. If more
   detail than just the element to resolve to is required, a map can be
   supplied instead. This map should contain:

   - an :element attribute indicating the element to resolve to
   - a :condition attribute, which should indicate a function to be
     which determines if the resolve is valid (optional)

   Additionally, if more than one symbol is defined with the same name (for
   example, shadowing let bindings), a vector of the symbol bindings can
   be supplied. These will be processed in reverse order to allow later
   bindings to shadow earlier ones."

  (:import (org.jetbrains.plugins.clojure.psi.api ClList ClVector ClLiteral ClListLike ClMetadata ClKeyword ClMap)
           (org.jetbrains.plugins.clojure.psi.api.defs ClDef)
           (org.jetbrains.plugins.clojure.psi.impl.defs ClDefImpl)
           (org.jetbrains.plugins.clojure.psi.api.symbols ClSymbol)
           (org.jetbrains.plugins.clojure.psi.resolve ResolveUtil)
           (com.intellij.psi PsiNamedElement PsiElement ResolveState)
           (org.jetbrains.plugins.clojure.psi.impl ImportOwner ClMapEntry)
           (com.intellij.psi.scope PsiScopeProcessor NameHint))
  (:use [plugin.util :only [in? assoc-all safely]])
  (:require [plugin.resolve :as resolve]
            [plugin.psi :as psi]
            [clojure.string :as str]
            [plugin.extension :as extension]))

(def resolve-keys-key (psi/cache-key ::resolve-keys))
(def resolve-symbols-key (psi/cache-key ::resolve-symbols))

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
    (map? scope) (let [{:keys [scope after]} scope]
                   (and (in-scope? place scope)
                        (psi/after? place after)))
    (= scope :ns) true
    (instance? PsiElement scope) (psi/contains? scope place)
    :else true))

(defn resolve-binding [binding list processor state place]
  (if (vector? binding)
    ; Process vector in reverse order so later bindings shadow earlier ones
    (some #(resolve-binding % list processor state place) (rseq binding))
    (if (psi/symbol? binding)
      (or (= binding place)
          (not (ResolveUtil/processElement processor binding state)))
      (let [{:keys [element condition]} binding]
        (or (= element place)
            (if (or (nil? condition)
                    (condition list element place))
              (not (ResolveUtil/processElement processor element state))))))))

(defn name-hint [^PsiScopeProcessor processor ^ResolveState state]
  (if-let [hint (.getHint processor NameHint/KEY)]
    (.getName hint state)))

(defn resolve-symbol [bindings list processor state place]
  (if-let [name (name-hint processor state)]
    (if-let [binding (bindings name)]
      (resolve-binding binding list processor state place))
    (some #(resolve-binding % list processor state place) (vals bindings))))

; TODO disambiguate when we have more information from namespace elements
(defn resolve-keys [list]
  (if-let [head-element (first (psi/significant-children list))]
    (if (psi/symbol? head-element)
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
