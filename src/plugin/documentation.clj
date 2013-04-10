(ns plugin.documentation
  (:import (com.intellij.codeInsight.javadoc JavaDocUtil)
           (com.intellij.psi PsiElement)
           (org.jetbrains.plugins.clojure.psi ClojurePsiElement)
           (org.jetbrains.plugins.clojure.psi.api.symbols ClSymbol)
           (org.jetbrains.plugins.clojure.psi.api.defs ClDef)
           (com.intellij.lang.documentation DocumentationProvider)
           (org.jetbrains.plugins.clojure ClojureLanguage)
           (com.intellij.lang LanguageDocumentation)
           (org.jetbrains.plugins.clojure.psi.api ClojureFile ClLiteral ClMetadata ClVector ClList ClListLike ClMap))
  (:require [plugin.psi :as psi]
            [plugin.extension :as extension]))

(defn process-string [^PsiElement element]
  (let [text (.getText element)
        trimmed (.substring text 1 (dec (.length text)))]
    (.replaceAll trimmed "\n *" "<br/>")))

; TODO make test cases out of these
;(defn test1 [])
;(defn test2 ([]) ([x]))
;(defn test3 "test" [])
;(defn test4 "test" ([]) ([x]))
;(defn test5 {:test "test"} [])
;(defn test6 {:test "test"} ([]) ([x]))
;(defn test7 "test" {:test "test"} [])
;(defn test8 "test" {:test "test"} ([]) ([x]))
;(def test9 (fn [x]))
;(def test10 (fn ([]) ([x])))
;(def test11 (fn name [x]))
;(def test12 (fn name ([]) ([x])))

(defn get-multi-arity-lists [candidates]
  (map #(first (psi/significant-children %))
       (filter #(and (instance? ClList %)
                     (instance? ClVector (first (psi/significant-children %))))
               candidates)))

; TODO duplicated from lists.clj
(defn third [coll]
  (first (rest (rest coll))))

(defn drop-if [pred coll]
  (if (pred (first coll))
    (rest coll)
    coll))

(defn get-param-lists [^ClDef element]
  (let [name-symbol (.getNameSymbol element)
        siblings (filter psi/significant? (psi/next-siblings name-symbol))
        after-name (first (drop-if
                            #(instance? ClMap %)
                            (drop-if
                              #(instance? ClLiteral %)
                              siblings)))]
    (if (instance? ClVector after-name)
      [after-name]
      (if (instance? ClList after-name)
        (let [children (psi/significant-children after-name)
              head (first children)]
          (if (and (instance? ClSymbol head)
                   (= "fn" (.getText ^ClSymbol head)))
            (let [params (drop-if #(instance? ClSymbol %)
                                  (rest children))]
              (if (instance? ClVector (first params))
                [(first params)]
                (get-multi-arity-lists params)))
            (get-multi-arity-lists siblings)))))))

(defn get-defn-doc [^ClDef element]
  (let [namespace (.getNamespace ^ClojureFile (.getContainingFile element))
        name (.getName element)
        param-lists (get-param-lists element)
        header (str "<b>"
                    namespace "/" name
                    "</b><br/>"
                    (if param-lists
                      (apply str (map #(str (.getText ^PsiElement %) "<br/>") param-lists))
                      "")
                    "<br/>")
        name-symbol (.getNameSymbol element)
        metadata (psi/metadata name-symbol)
        after-name (first (filter psi/significant? (psi/next-siblings name-symbol)))]
    (cond
      (instance? ClLiteral after-name) (str header (process-string after-name))
      metadata (if-let [doc (.getValue metadata "doc")]
                 (str header (process-string doc)))
      :else header)))

(defn get-doc [element]
  (cond
    (instance? ClDef element) (get-defn-doc element)
    ; TODO formatting bug with let here
    (and (instance? ClSymbol element)
         (instance? ClDef (.getParent ^ClSymbol element)))
    (let [^ClDef parent (.getParent ^ClSymbol element)]
      (if (= element (.getNameSymbol parent))
        (get-defn-doc parent)))))

(defn initialise []
  (extension/remove-all LanguageDocumentation/INSTANCE)
  (extension/register
    LanguageDocumentation/INSTANCE
    (reify DocumentationProvider
      (getQuickNavigateInfo [this element original-element]
        (cond
          (instance? ClDef element) (.getPresentationText ^ClDef element)
          (instance? ClSymbol element) (.getNameString ^ClSymbol element)
          :else nil))
      (generateDoc [this element original-element]
        (str "<pre>"
             (get-doc element)
             "</pre>"))
      (getDocumentationElementForLookupItem [this manager object element]
        (if (instance? ClojurePsiElement object)
          object
          nil))
      (getDocumentationElementForLink [this manager link context]
        (JavaDocUtil/findReferenceTarget manager link context))
      (getUrlFor [this element original-element]
        nil))))
