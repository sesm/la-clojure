(ns plugin.initialise
  (:use [plugin.formatting :only initialise]))

(defn initialise-all []
  (plugin.formatting/initialise))
