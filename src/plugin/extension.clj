(ns plugin.extension)

(def extensions (atom {}))

(defn has-extension? [type key]
  (not (nil? (get-in @extensions [type key]))))

(defn get-extension [type key]
  (get-in @extensions [type key]))

(defn register-extension [type key extension]
  (if (sequential? key)
    (swap! extensions (fn [extensions keys]
                       (reduce #(assoc-in %1 [type %2] extension)
                               extensions
                               keys)) key)
    (swap! extensions assoc-in [type key] extension)))
