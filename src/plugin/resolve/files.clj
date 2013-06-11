(ns plugin.resolve.files
  (:import (org.jetbrains.plugins.clojure.psi.impl ClojureFileImpl)
           (org.jetbrains.plugins.clojure.psi.api.synthetic ClSyntheticClass)
           (org.jetbrains.plugins.clojure.psi.impl.synthetic ClSyntheticClassImpl)
           (org.jetbrains.plugins.clojure.psi.api ClojureFile))
  (:use [plugin.resolve :as resolve]))

;(set! *warn-on-reflection* true)

(defn process-file-declarations [this processor state last-parent place]
  (not (ClojureFileImpl/processDeclarations this processor state last-parent place)))

(defn process-synthetic-class-decls [this processor state last-parent place]
  (not (ClSyntheticClassImpl/processDeclarations this processor state last-parent place)))
