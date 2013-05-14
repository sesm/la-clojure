(ns plugin.tests.resolvetests
  (:import (java.io File)
           (java.util.zip ZipFile)
           (java.util.regex Matcher)
           (com.intellij.psi PsiPolyVariantReference PsiNamedElement)
           (org.jetbrains.plugins.clojure.psi.api ClojureFile)
           (junit.framework AssertionFailedError))
  (:use [clojure.test :only [deftest is run-tests use-fixtures assert-expr do-report]])
  (:require [plugin.test :as test]
            [clojure.string :as str]))

(defn load-clojure-core []
  (let [lib-dir (File. (str (System/getProperty "plugin.path") "/lib"))
        clojure-lib-name (first (filter #(.startsWith % "clojure-1.")
                                        (seq (.list lib-dir))))
        clojure-lib (ZipFile. (File. lib-dir clojure-lib-name))
        core-file-entry (.getEntry clojure-lib "clojure/core.clj")]
    (with-open [stream (.getInputStream clojure-lib core-file-entry)]
      (slurp stream))))

(defn re-pos [re string]
  (loop [s string
         res {}]
    (let [^Matcher m (re-matcher re s)]
      (if (.find m)
        (let [start (.start m)
              match (.group m)
              len (.length match)]
          (recur (str (.substring s 0 start)
                      (.substring s (+ start len)))
                 (assoc res start (.substring match 1 (dec len)))))
        [s res]))))

(defn find-key [map value]
  (loop [items (seq map)]
    (when (seq items)
      (if (= (val (first items)) value)
        (key (first items))
        (recur (rest items))))))

(defn check-self-resolution [element]
  (if (and (instance? PsiPolyVariantReference element)
           (some #(= element (.getElement %))
                 (seq (.multiResolve element false))))
    (test/fail (.getText element) ":" (.getTextOffset element) " resolves to self"))
  (doseq [child (.getChildren element)]
    (check-self-resolution child)))

(defn check-resolve [text extra]
  (if (:files extra)
    (doseq [[file text] (:files extra)]
      (test/add-file file text)))
  (let [[text tags] (re-pos #"<[^ >]+>" text)
        file (test/create-file "check-resolve.clj" text)]
    (check-self-resolution file)
    (doseq [entry tags]
      (let [offset (key entry)
            item (val entry)]
        (when-not (.startsWith item "@")
          (let [ref (or (.findReferenceAt file offset)
                        (test/fail "Can't find reference at " item))
                resolve-elements (filter (complement nil?)
                                         (map #(.getElement %) (.multiResolve ref false)))
                offsets (set (map #(.getTextOffset %) resolve-elements))
                ref-text (.getText (.getElement ref))]
            (cond
              (re-matches #"[0-9]+" item)
              (let [target-offset (or (find-key tags (str "@" item))
                                      (test/fail "Can't find target for ref " item))
                    target (or (.findElementAt file target-offset)
                               (test/fail "Can't find element at @" item))]
                (if-not (offsets target-offset)
                  (test/fail ref-text
                             ":"
                             offset
                             " does not resolve to "
                             (.getText target)
                             ":"
                             target-offset)))
              (= "/" item)
              (if-not (empty? offsets)
                (test/fail ref-text
                           ":"
                           offset
                           " should not resolve but resolves to "
                           (count offsets)
                           " item(s): "
                           offsets))
              (.contains item "/")
              (if-not (= 1 (count resolve-elements))
                (test/fail ref-text
                           ":"
                           offset
                           " should uniquely resolve but resolves to "
                           (count resolve-elements)
                           " item(s): "
                           resolve-elements)
                (let [target (first resolve-elements)
                      psi-file (.getContainingFile target)
                      [nspace sym-name] (str/split item #"/" 2)]
                  (if (and (instance? PsiNamedElement target)
                           (instance? ClojureFile psi-file))
                    (do
                      (test/assert= sym-name (.getName target) "symbol")
                      (test/assert= nspace (.getNamespace psi-file) "namespace"))
                    (test/fail "Unexpected types " (class target) " " (class psi-file))))))))))))

(defmethod assert-expr 'valid-resolve? [msg form]
  `(let [text# ~(nth form 1)
         extra# ~(let [params (drop 2 form)]
                   (if (seq params)
                     (apply assoc {} params)))]
     (test/with-tmp-files
       (test/with-unique-default-definitions
         (try (check-resolve text# extra#)
              (do-report {:type     :pass,
                          :message  ~msg,
                          :expected '~form,
                          :actual   nil})
              (catch AssertionFailedError e#
                (do-report {:type     :fail,
                            :message  (str ~msg ": " (.getMessage e#)),
                            :expected '~form,
                            :actual   e#})
                e#))))))



(use-fixtures :once test/light-idea-fixture)

(deftest basic-tests
  (is (valid-resolve? "(declare <@1>x) <1>x")
      "Basic declare")
  (is (valid-resolve? "(def <@1>x 10) <1>x")
      "Basic define")
  (is (valid-resolve? "(defn <@1>f []) <1>f")
      "Basic defn")
  (is (valid-resolve? "(defn <@1>f [<@2>x] <2>x) <1>f")
      "Basic defn with parameters")
  (is (valid-resolve? "(defn <@1>f [<@2>x <@3>y] <2>x <3>y) <1>f")
      "Basic defn with multiple parameters")
  (is (valid-resolve? "(defn <@1>f [] <1>f) <1>f")
      "Recursive defn")
  (is (valid-resolve? "(fn [<@1>x] <1>x) </>x")
      "Basic fn")
  (is (valid-resolve? "(fn <@1>f [<@2>x] <1>f <2>x) </>f </>x")
      "Named fn")
  (is (valid-resolve? "(fn <@1>my-fn [] (<1>my-fn))")
      "Recursive fn")
  (is (valid-resolve? "(fn <@4>f ([] <4>f) ([<@1>x] <1>x) ([<@2>x <@3>y] (* <@2>x <@3>y)))")
      "Overloaded fn")
  (is (valid-resolve? "(fn [<@1>x <@2>y] (+ <1>x <2>y)) </>x </>y")
      "fn with multiple parameters")
  (is (valid-resolve? "(fn [<@1>x & <@2>y] (+ <1>x <2>y)) </>x </>y")
      "fn with rest parameter")
  (is (valid-resolve? "(let [<@1>x 1 <@2>y 2] (+ <1>x <2>y)) </>x </>y")
      "Basic let")
  (is (valid-resolve? "(fn [x] (let [<@1>x 1] <1>x)) </>x")
      "Let should shadow external fn")
  (is (valid-resolve? "(let [<@1>x </>y <@2>y 2] (+ <1>x <2>y)) </>x </>y")
      "Let bindings should not see later bindings")
  (is (valid-resolve? "(fn [<@2>x] (let [<@1>x <2>x] <1>x)) </>x")
      "Let bindings should not resolve to previous")
  (is (valid-resolve? "(let [x 2] (let [<@1>x 1] <1>x)) </>x")
      "Let should shadow external let")
  (is (valid-resolve? "(let [<@1>x 1 <@2>x (inc <1>x)] <2>x) </>x")
      "Let bindings should shadow earlier bindings")
  )

(deftest destructuring
  (is (valid-resolve? "(let [[<@1>a </>& <@2>b] []] <1>a <2>b) </>a </>b")
      "Let-form vector destructuring")
  (is (valid-resolve? (test/lines "(let [[[<@1>a <@2>b] [<@3>c <@4>d]] []]"
                                  "  <1>a <2>b <3>c <4>d)"
                                  "</>a </>b </>c </>d"))
      "Nested let-form vector destructuring")
  (is (valid-resolve? "(defn x [[<@1>a </>& <@2>b]] <1>a <2>b) </>a </>b")
      "Fn vector destructuring")
  (is (valid-resolve? (test/lines "(defn x [[[<@1>a <@2>b] [<@3>c <@4>d]]]"
                                  "  <1>a <2>b <3>c <4>d)"
                                  "</>a </>b </>c </>d"))
      "Nested fn vector destructuring")
  (is (valid-resolve? (test/lines "(let [{<@1>a :a, <@2>b :b, :as <@3>m :or {<1>a 2 <2>b 3}} {}]"
                                  "  [<1>a <2>b <3>m])"))
      "Let-form associative destructuring")
  (is (valid-resolve? (test/lines "(let [{:keys [<@1>a <@2>b]} {}]"
                                  "  [<1>a <2>b])"))
      "Let-form associative keyword destructuring")
  (is (valid-resolve? (test/lines "(defn x [{<@1>a :a, <@2>b :b, :as <@3>m :or {<1>a 2 <2>b 3}}]"
                                  "  [<1>a <2>b <3>m])"))
      "Fn associative destructuring")
  (is (valid-resolve? (test/lines "(defn x [{:keys [<@1>a <@2>b]}]"
                                  "  [<1>a <2>b])"))
      "Fn associative keyword destructuring")
  )

(def other-files {"other.clj"   (test/lines "(ns other)"
                                            "(defn fun [])"
                                            "(defn fun2 [])")
                  "another.clj" (test/lines "(ns another)"
                                            "(defn another-fun [])"
                                            "(defn another-fun2 [])")})

(def clojure-core {"clojure/core.clj" (load-clojure-core)})

(deftest multi-file
  (is (valid-resolve? "(<clojure.core/defn>defn x [])"
                      :files clojure-core)
      "clojure.core resolution")
  (is (valid-resolve? (test/lines "(ns test (:use other another))"
                                  "(<other/fun>fun)"
                                  "(<other/fun2>fun2)"
                                  "(<another/another-fun>another-fun)"
                                  "(<another/another-fun2>another-fun2)")
                      :files other-files)
      "ns symbol use form")
  (is (valid-resolve? (test/lines "(ns test (:use [other] [another]))"
                                  "(<other/fun>fun)"
                                  "(<other/fun2>fun2)"
                                  "(<another/another-fun>another-fun)"
                                  "(<another/another-fun2>another-fun2)")
                      :files other-files)
      "ns vector use form")
;  (is (valid-resolve? (test/lines "(ns test (:use [other :only fun]))"
;                                  "(<other/fun>fun)"
;                                  "(</>fun2)")
;                      :files other-files)
;      "test :only filter")
;  (is (valid-resolve? (test/lines "(ns test (:use [other :exclude fun]))"
;                                  "(</>fun)"
;                                  "(<other/fun2>fun2)")
;                      :files other-files)
;      "test :exclude filter")
  (is (valid-resolve? (test/lines "(ns test (:use [other :rename {fun myfun, fun2 myfun2}]))"
                                  "(<other/fun>myfun)"
                                  "(<other/fun2>myfun2)")
                      :files other-files)
      "test :rename filter")
  )
