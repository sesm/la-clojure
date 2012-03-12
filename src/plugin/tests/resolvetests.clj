(ns plugin.tests.resolvetests
  (:use [clojure.test :only [deftest is run-tests]])
  (:require plugin.test
            [clojure.string :as string]))

(defn lines [& strings]
  (string/join "\n" strings))

; TODO destructuring etc
(deftest basic-tests
;  (is (valid-resolve? "(declare <@1>x) <1>x")
;      "Basic declare")
  (is (valid-resolve? "(def <@1>x 10) <1>x")
      "Basic define")
  (is (valid-resolve? "(defn <@1>f []) <1>f")
      "Basic defn")
  (is (valid-resolve? "(fn [<@1>x] <1>x) </>x")
      "Basic fn")
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
;  (is (valid-resolve? "(let [<@1>x </>y <@2>y 2] (+ <1>x <2>y)) </>x </>y")
;      "Let bindings should not see later bindings")
;  (is (valid-resolve? "(fn [<@2>x] (let [<@1>x <2>x] <1>x)) </>x")
;      "Let bindings should not resolve to previous")
  (is (valid-resolve? "(let [x 2] (let [<@1>x 1] <1>x)) </>x")
      "Let should shadow external let")
  )
