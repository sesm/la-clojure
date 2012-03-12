(ns plugin.tests.alltests
  (:use [clojure.test :only [run-tests]])
  (:require plugin.tests.typingtests
            plugin.tests.editoractiontests
            plugin.tests.formattingtests
            plugin.tests.resolvetests))

(run-tests 'plugin.tests.typingtests
           'plugin.tests.editoractiontests
           'plugin.tests.formattingtests
           'plugin.tests.resolvetests)

; TODO investigate why we need this
(System/exit 0)
