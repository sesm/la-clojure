(ns plugin.tests.alltests
  (:use [clojure.test :only [run-tests]])
  (:require plugin.tests.typingtests
            plugin.tests.editoractiontests
            plugin.tests.formattingtests
            plugin.tests.resolvetests
            plugin.tests.wordsscanner))

(run-tests 'plugin.tests.typingtests
           'plugin.tests.editoractiontests
           'plugin.tests.formattingtests
           'plugin.tests.resolvetests
           'plugin.tests.wordsscanner)

; TODO investigate why we need this
(System/exit 0)
