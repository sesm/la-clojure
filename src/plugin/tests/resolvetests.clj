(ns plugin.tests.resolvetests
  (:use [clojure.test :only [deftest is run-tests]])
  (:require [plugin.test :as test]
            [clojure.string :as string]))

; TODO destructuring etc
(deftest basic-tests
  ;  (is (valid-resolve? "(declare <@1>x) <1>x")
  ;      "Basic declare")
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
  )

(def other-files {"other.clj"   (test/lines "(ns other)"
                                            "(defn fun [])"
                                            "(defn fun2 [])")
                  "another.clj" (test/lines "(ns another)"
                                            "(defn another-fun [])"
                                            "(defn another-fun2 [])")})

(deftest multi-file
  (is (valid-resolve? "(<clojure.core/defn>defn x [])"
                      :use-clojure-core true)
      "clojure.core resolution")
  (is (valid-resolve? (test/lines "(ns test (:use other another))"
                                  "(<other/fun>fun)"
                                  "(<other/fun2>fun2)"
                                  "(<another/another-fun>another-fun)"
                                  "(<another/another-fun2>another-fun2)")
                      :files other-files)
      "ns symbol use form")
  ;  (is (valid-resolve? (test/lines "(ns test (:use [other] [another]))"
  ;                             "(<other/fun>fun)"
  ;                             "(<other/fun2>fun2)"
  ;                             "(<another/another-fun>another-fun)"
  ;                             "(<another/another-fun2>another-fun2)")
  ;                      other-files)
  ;      "ns vector use form")
  ;  (is (valid-resolve? (test/lines "(ns test (:use [other :only fun]))"
  ;                             "(<other/fun>fun)"
  ;                             "(</>fun2)")
  ;                      other-files)
  ;      "test :only filter")
  ;  (is (valid-resolve? (test/lines "(ns test (:use [other :exclude fun]))"
  ;                             "(</>fun)"
  ;                             "(<other/fun2>fun2)")
  ;                      other-files)
  ;      "test :exclude filter")
  ;  (is (valid-resolve? (test/lines "(ns test (:use [other :rename {fun myfun, fun2 myfun2}]))"
  ;                             "(<other/fun>myfun)"
  ;                             "(<other/fun2>myfun2)")
  ;                      other-files)
  ;      "test :rename filter")
  )
