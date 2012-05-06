(ns plugin.resolve.lists
  (:import (org.jetbrains.plugins.clojure.psi.impl.list ListDeclarations)
           (org.jetbrains.plugins.clojure.psi.api ClList)
           (org.jetbrains.plugins.clojure.psi.api.defs ClDef)
           (org.jetbrains.plugins.clojure.psi.impl.defs ClDefImpl))
  (:use [plugin.resolve.core :as resolve]))

;(set! *warn-on-reflection* true)

(extend-type ClList
  resolve/Resolvable
  (process-declarations [this processor state last-parent place]
    (ListDeclarations/get processor state last-parent place this (.getHeadText this))))

(extend-type ClDef
  resolve/Resolvable
  (process-declarations [this processor state last-parent place]
    (ClDefImpl/processDeclarations this processor state last-parent place)))
