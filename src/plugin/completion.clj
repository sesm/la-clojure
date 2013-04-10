(ns plugin.completion
  (:import (com.intellij.lang LanguageNamesValidation)
           (com.intellij.lang.refactoring NamesValidator)
           (org.jetbrains.plugins.clojure.findUsages ClojureWordsScanner))
  (:require [plugin.extension :as extension]))

(def keywords #{"nil" "true" "false"})

(defn initialise []
  (extension/remove-all LanguageNamesValidation/INSTANCE)
  (extension/register LanguageNamesValidation/INSTANCE
                      (reify NamesValidator
                        (isKeyword [this string project]
                          (boolean (keywords string)))
                        (isIdentifier [this string project]
                          (boolean
                            (let [chars (seq string)]
                              (and chars
                                   (ClojureWordsScanner/isIdentifierStart (first chars))
                                   (every? #(ClojureWordsScanner/isIdentifierPart %) (rest chars)))))))))
