(ns plugin.extension)

(def extensions (atom {}))
(def list-short-names (atom {}))

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

(defn register-list-key [short-names key]
  (if-not (sequential? key)
    (register-list-key short-names [key])
    (reduce (fn [short-names key]
              (let [short-name (name key)
                    keys (short-names short-name [])]
                (assoc short-names short-name (conj keys key))))
            short-names
            key)))

(defn register-list-extension [type key extension]
  (swap! list-short-names register-list-key key)
  (register-extension type key extension))

(defn list-keys-by-short-name [short-name]
  (@list-short-names short-name))
