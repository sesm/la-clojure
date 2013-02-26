(ns plugin.actions.core
  (:import (com.intellij.openapi.actionSystem ActionManager KeyboardShortcut DefaultActionGroup)
           (com.intellij.openapi.keymap KeymapManager)
           (com.intellij.openapi.actionSystem.ex ActionManagerEx)))

(defn register-action [action id group-id]
  (let [manager (ActionManager/getInstance)]
    (.registerAction manager id action)
    (if-not (nil? group-id)
      (let [^DefaultActionGroup group (.getAction manager group-id)]
        (.add group action)))))

(defn set-text [action text]
  (do
    (-> action
        .getTemplatePresentation
        (.setText text))
    action))

(defn- get-key-stroke [k]
  (ActionManagerEx/getKeyStroke k))

(defn register-shortcut
  ([action-id first-key]
   (register-shortcut action-id first-key nil))
  ([action-id first-key second-key]
   (let [keymap (.getKeymap (KeymapManager/getInstance) "$default")
         first (get-key-stroke first-key)
         second (if-not (nil? second-key) (get-key-stroke second-key))]
     (.addShortcut keymap action-id (KeyboardShortcut. first second)))))

