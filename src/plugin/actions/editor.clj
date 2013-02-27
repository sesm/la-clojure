(ns plugin.actions.editor
  (:import (com.intellij.openapi.editor.actionSystem EditorWriteActionHandler)
           (org.jetbrains.plugins.clojure.actions.editor ClojureEditorAction))
  (:require [plugin.actions.core :as action]))

(defn create-handler [f]
  (proxy [EditorWriteActionHandler] []
    (executeWriteAction [editor context]
      (f editor context))))

(defn create-editor-action [f]
  (proxy [ClojureEditorAction] [(create-handler f)]))

(defn defeditor-action
  ([id text shortcut handler]
   (defeditor-action id text shortcut handler nil))
  ([id text shortcut handler group]
   (do
     (-> (create-editor-action handler)
         (action/set-text text)
         (action/register-action id group))
     (if (vector? shortcut)
       (let [[f s] shortcut]
         (action/register-shortcut id f s))
       (action/register-shortcut id shortcut)))))
