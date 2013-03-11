(ns plugin.resolve.namespaces
  (:import (org.jetbrains.plugins.clojure.psi.api.ns ClNs)
           (org.jetbrains.plugins.clojure.psi.impl.ns ClNsImpl NamespaceUtil)
           (org.jetbrains.plugins.clojure.psi.impl ClojureFileImpl ImportOwner))
  (:require [plugin.resolve.core :as resolve]))

;(set! *warn-on-reflection* true)

(extend-type org.jetbrains.plugins.clojure.psi.impl.ns.NamespaceUtil$MyClSyntheticNamespace
  resolve/Resolvable
  (process-declarations [this processor state last-parent place]
    (not (org.jetbrains.plugins.clojure.psi.impl.ns.NamespaceUtil$MyClSyntheticNamespace/processDeclarations this processor state last-parent place))))

(extend-type org.jetbrains.plugins.clojure.psi.impl.ClojureFileImpl$CompletionSyntheticNamespace
  resolve/Resolvable
  (process-declarations [this processor state last-parent place]
    (not (org.jetbrains.plugins.clojure.psi.impl.ClojureFileImpl$CompletionSyntheticNamespace/processDeclarations this processor state last-parent place))))
