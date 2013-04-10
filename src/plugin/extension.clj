(ns plugin.extension
  (:import (com.intellij.lang LanguageExtension)
           (org.jetbrains.plugins.clojure ClojureLanguage)))

(defn register [point extension]
  (if (instance? LanguageExtension point)
    (.addExplicitExtension ^LanguageExtension point
                           (ClojureLanguage/getInstance)
                           extension)))

(defn remove-all [point]
  (if (instance? LanguageExtension point)
    (doseq [extension (.allForLanguage ^LanguageExtension point (ClojureLanguage/getInstance))]
      (.removeExplicitExtension ^LanguageExtension point
                                (ClojureLanguage/getInstance)
                                extension))))
