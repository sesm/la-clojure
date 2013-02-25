(ns plugin.actions.editor
  (:import (com.intellij.openapi.actionSystem ActionManager KeyboardShortcut)
           (com.intellij.openapi.actionSystem.ex ActionManagerEx)
           (com.intellij.openapi.keymap KeymapManager)
           (com.intellij.openapi.editor.actionSystem EditorWriteActionHandler)
           (org.jetbrains.plugins.clojure.actions.editor ClojureEditorAction)))

(defn register-action [action id group-id]
  (let [manager (ActionManager/getInstance)]
    (.registerAction manager id action)
    (if-not (nil? group-id)
      (let [group (.getAction manager group-id)]
        (.add group action)))))

(defn create-handler [f]
  (proxy [EditorWriteActionHandler] []
    (executeWriteAction [editor context]
      (f editor context))))

(defn create-editor-action [f]
  (proxy [ClojureEditorAction] [(create-handler f)]))

(defn set-text [action text]
  (do
    (.. action getTemplatePresentation (setText text))
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

(defn defeditor-action [id text shortcut handler group]
  (do
    (-> (create-editor-action handler)
        (set-text text)
        (register-action id group))
    (if (vector? shortcut)
      (let [[f s] shortcut]
        (register-shortcut id f s))
      (register-shortcut id shortcut))))
