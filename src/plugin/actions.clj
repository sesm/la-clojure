(ns plugin.actions
  (:import (com.intellij.openapi.actionSystem AnAction ActionManager KeyboardShortcut DefaultActionGroup AnActionEvent
                                              DataKeys Presentation CustomShortcutSet ShortcutSet Shortcut
                                              ActionToolbar)
           (com.intellij.openapi.keymap KeymapManager)
           (com.intellij.openapi.actionSystem.ex ActionManagerEx)
           (com.intellij.openapi.project DumbAwareAction)
           (com.intellij.openapi.module ModuleManager)
           (com.intellij.facet FacetManager FacetTypeId)
           (org.jetbrains.plugins.clojure.config ClojureFacetType)
           (javax.swing Icon KeyStroke)
           (com.intellij.openapi.util IconLoader SystemInfo)
           (com.intellij.openapi.editor.actionSystem EditorWriteActionHandler EditorAction)
           (org.jetbrains.plugins.clojure.utils ClojureUtils))
  (:require [clojure.string :as str]
            [plugin.util :as util]))

(defn find-action [id]
  (let [manager (ActionManager/getInstance)]
    (.getAction manager id)))

(defn register-action
  ([action id] (register-action action id nil))
  ([action id group-id] (let [manager (ActionManager/getInstance)]
                          (.registerAction manager (str id) action)
                          (if-not (nil? group-id)
                            (let [^DefaultActionGroup group (.getAction manager (str group-id))]
                              (.add group action))))))

(defn unregister-action
  ([id] (unregister-action id nil))
  ([id group-id] (let [manager (ActionManager/getInstance)]
                   (if-let [action (.getAction manager (str id))]
                     (do
                       (if-not (nil? group-id)
                         (let [^DefaultActionGroup group (.getAction manager (str group-id))]
                           (.remove group action)))
                       (.unregisterAction manager (str id)))))))

(defn remap-key-stroke [k]
  (ActionManagerEx/getKeyStroke (if (SystemInfo/isMac)
                                  (str/replace k #"control|ctrl" "meta")
                                  k)))

(defn key-stroke [k]
  (ActionManagerEx/getKeyStroke k))

(defn register-shortcut
  ([action-id first-key]
   (register-shortcut action-id first-key nil))
  ([action-id first-key second-key]
   (let [keymap (.getKeymap (KeymapManager/getInstance) "$default")
         first (key-stroke first-key)
         second (util/safely (key-stroke second-key))]
     (.addShortcut keymap (str action-id) (KeyboardShortcut. first second)))))

(defn unregister-shortcut
  ([action-id first-key]
   (register-shortcut action-id first-key nil))
  ([action-id first-key second-key]
   (let [keymap (.getKeymap (KeymapManager/getInstance) "$default")
         first (key-stroke first-key)
         second (util/safely (key-stroke second-key))]
     (.removeShortcut keymap (str action-id) (KeyboardShortcut. first second)))))

(defn unregister-all-shortcuts [action-id]
  (let [keymap (.getKeymap (KeymapManager/getInstance) "$default")]
    (doseq [shortcut (seq (.getShortcuts keymap (str action-id)))]
      (.removeShortcut keymap (str action-id) shortcut))))

(defn always-enabled [^AnActionEvent event]
  (.setEnabled (.getPresentation event) true))

(defn editor-action-always-true [editor data-context]
  true)

(def action-defaults {:update always-enabled})
(def editor-action-defaults {:enabled? editor-action-always-true
                             :in-command? editor-action-always-true})

(defn to-icon [value]
  (cond
    (nil? value) nil
    (instance? Icon value) value
    (string? value) (IconLoader/getIcon ^String value)))

(defn- config!* [^AnAction action opts]
  (let [presentation (.getTemplatePresentation action)]
    (if-let [icon (:icon opts)]
      (.setIcon presentation (to-icon icon)))
    (if-let [text (:text opts)]
      (.setText presentation text))
    (if-let [description (:description opts)]
      (.setDescription presentation description))
    (if-let [visible (:visible opts)]
      (.setVisible presentation visible))
    (if-let [from (:shortcut-from opts)]
      (if-let [other (find-action from)]
        (.copyShortcutFrom action other)))
    (if-let [key (:shortcut-set opts)]
      (if (vector? key)
        (let [shortcut (KeyboardShortcut. (remap-key-stroke (first key))
                                          (remap-key-stroke (second key)))]
          (.setShortcutSet action (CustomShortcutSet. (into-array Shortcut shortcut))))
        (let [key-stroke (remap-key-stroke key)]
          (.setShortcutSet action (CustomShortcutSet. ^KeyStroke key-stroke)))))
    action))

(defn action-group [& rest]
  (let [opts (apply assoc action-defaults rest)
        action (proxy [DefaultActionGroup] [])]
    (config!* action opts)))

(defn dumb-aware [& rest]
  (let [opts (apply assoc action-defaults rest)
        action (proxy [DumbAwareAction] []
                 (update [event]
                   (if-let [doit (:update opts)]
                     (doit event)))
                 (actionPerformed [event]
                   (if-let [doit (:action-performed opts)]
                     (doit event))))]
    (config!* action opts)))

(defn editor-write-action [& rest]
  (let [opts (apply assoc editor-action-defaults rest)
        handler (proxy [EditorWriteActionHandler] []
                  (executeWriteAction [editor data-context]
                    (if-let [doit (:execute opts)]
                      (doit editor data-context)))
                  (isEnabled [editor data-context]
                    (if-let [doit (:enabled? opts)]
                      (doit editor data-context)))
                  (executeInCommand [editor data-context]
                    (if-let [doit (:in-command? opts)]
                      (doit editor data-context))))
        action (proxy [EditorAction] [handler]
                 (update
                   ([^AnActionEvent event]
                    (proxy-super update event))
                   ([editor presentation data-context]
                    (.setEnabled ^Presentation presentation (ClojureUtils/isClojureEditor editor)))))]
    (config!* action opts)))

(defn config! [action & rest]
  (config!* action (apply assoc {} rest)))

(defn ^ActionToolbar create-toolbar [place action-group horizontal]
  (let [manager (ActionManager/getInstance)]
    (.createActionToolbar manager place action-group horizontal)))

(defn module [^AnActionEvent event]
  (if-let [module (.getData event DataKeys/MODULE)]
    module
    (if-let [project (.getData event DataKeys/PROJECT)]
      (let [module-manager (ModuleManager/getInstance project)
            modules (seq (.getModules module-manager))]
        (if-let [module (first (filter
                                 (fn [module]
                                   (let [facet-manager (FacetManager/getInstance module)
                                         facet (.getFacetByType facet-manager (.getId (ClojureFacetType/INSTANCE)))]
                                     (not (nil? facet))))
                                 modules))]
          module
          (first modules))))))
