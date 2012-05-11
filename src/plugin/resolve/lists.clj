(ns plugin.resolve.lists
  (:import (org.jetbrains.plugins.clojure.psi.impl.list ListDeclarations)
           (org.jetbrains.plugins.clojure.psi.api ClList ClVector)
           (org.jetbrains.plugins.clojure.psi.api.defs ClDef)
           (org.jetbrains.plugins.clojure.psi.impl.defs ClDefImpl)
           (org.jetbrains.plugins.clojure.psi.api.symbols ClSymbol)
           (org.jetbrains.plugins.clojure.psi.resolve ResolveUtil)
           (com.intellij.psi PsiNamedElement))
  (:require [plugin.resolve.core :as resolve]
            [plugin.psi :as psi]
            [clojure.string :as str]))

;(set! *warn-on-reflection* true)

(def local-binding-forms #{"let" "with-open" "with-local-vars" "when-let" "when-first"
                           "for" "if-let" "loop"})

; Note that we use "true" to indicate "stop searching", not "continue searching"

(defn elem [element]
  (str (.getText element) ":" (.getTextOffset element)))

(defn process-element [processor element place]
  ;(println "element:" (elem element) (elem place))
  (and (instance? PsiNamedElement element)
       (not (ResolveUtil/processElement processor element))))

(defn process-elements [processor elements place]
  (if (seq elements)
    (or (process-element processor (first elements) place)
        (recur processor (next elements) place))))

(defn process-params [processor params place]
  ;(println "params: "  (.getText params)  (elem place))
  (or (psi/contains? params place)
      (process-elements processor (seq (.getAllSymbols params)) place)))

(defn third [coll]
  (first (rest (rest coll))))

(defn offset-in [element ancestor]
  (loop [offset 0
         current element]
    (if (= current ancestor)
      offset
      (recur (+ offset (.getStartOffsetInParent current))
             (.getParent current)))))

(defn process-fn [processor list place last-parent]
  (if (psi/contains? list place)
    (let [children (psi/significant-children list)
          second (second children)]
      ;(println (.getText list) ":"  (elem place) (.getText second))
      (or
        ; Check fn name
        (and (instance? ClSymbol second)
             (not (= place second))
             (process-element processor second place))
        ; Check fn params
        (if-let [params (cond (instance? ClVector second) second
                              (instance? ClVector (third children)) (third children)
                              :else nil)]
          (process-params processor params place))
        ; Check fn params for multi-arity fn
        (and (instance? ClList last-parent)
             (let [params (first (psi/significant-children last-parent))]
               (and (instance? ClVector params)
                    (process-params processor params place))))
        :else false))))

(defn process-let [processor list place last-parent]
  (if (psi/contains? list place)
    (let [children (psi/significant-children list)
          params (second children)]
      ;(println (.getText list) ": "  (elem place) " " (.getText params))
      (and (instance? ClVector params)
           (let [children (psi/significant-children params)]
             (if-not (psi/contains? params place)
               ; Basic case - place outside let bindings
               (process-elements processor (take-nth 2 children) place)
               ; More complex - place is within bindings
               (let [definitions (partition 2 children)]
                 (or (some #(psi/contains? % place) (map first definitions))
                     (process-elements processor
                                       (map first
                                            (take-while #(not (psi/contains? (second %) place))
                                                        definitions))
                                       place)))))))))

(extend-type ClList
  resolve/Resolvable
  (process-declarations [this processor state last-parent place]
    (if (if-let [head-text (.getHeadText this)]
          (case head-text
                "fn" (process-fn processor this place last-parent)
                (if (local-binding-forms head-text)
                  (process-let processor this place last-parent)
                  (not (ListDeclarations/get processor state last-parent place this (.getHeadText this))))))
      true
      false)))

(extend-type ClDef
  resolve/Resolvable
  (process-declarations [this processor state last-parent place]
    (not (ClDefImpl/processDeclarations this processor state last-parent place))))
