(ns plugin.resolve.files
  (:import (org.jetbrains.plugins.clojure.psi.impl ClojureFileImpl)
           (org.jetbrains.plugins.clojure.psi.api.synthetic ClSyntheticClass)
           (org.jetbrains.plugins.clojure.psi.impl.synthetic ClSyntheticClassImpl)
           (org.jetbrains.plugins.clojure.psi.api ClojureFile))
  (:use [plugin.resolve.core :as resolve]))

;(set! *warn-on-reflection* true)

(extend-type ClojureFile
  resolve/Resolvable
  (process-declarations [this processor state last-parent place]
    (ClojureFileImpl/processDeclarations this processor state last-parent place)))

(extend-type ClSyntheticClass
  resolve/Resolvable
  (process-declarations [this processor state last-parent place]
    (ClSyntheticClassImpl/processDeclarations this processor state last-parent place)))
