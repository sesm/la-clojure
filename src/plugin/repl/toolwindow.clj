(ns plugin.repl.toolwindow
  (:import (com.intellij.openapi.wm ToolWindowManager IdeFocusManager)
           (org.jetbrains.plugins.clojure.repl.toolwindow REPLToolWindowFactory)
           (org.jetbrains.plugins.clojure.repl ClojureConsoleView TerminateREPLDialog
                                               ClojureConsole)
           (com.intellij.openapi.actionSystem DefaultActionGroup AnAction AnActionEvent IdeActions)
           (javax.swing JPanel JLabel SwingConstants JComponent)
           (java.awt BorderLayout Color)
           (com.intellij.ui.content ContentFactory ContentManager Content ContentManagerAdapter ContentManagerEvent
                                    ContentFactory$SERVICE)
           (com.intellij.openapi.project ProjectManagerListener ProjectManager DumbAware Project)
           (com.intellij.openapi.ui DialogWrapper)
           (com.intellij.util.ui UIUtil)
           (org.jetbrains.plugins.clojure ClojureIcons)
           (org.jetbrains.plugins.clojure.utils Actions Editors)
           (com.intellij.openapi.util IconLoader TextRange Disposer)
           (com.intellij.openapi.editor Editor EditorFactory)
           (com.intellij.openapi.editor.ex EditorEx)
           (com.intellij.codeInsight.lookup LookupManager)
           (org.jetbrains.plugins.clojure.psi.util ClojurePsiUtil)
           (com.intellij.openapi.editor.event DocumentListener DocumentEvent)
           (com.intellij.openapi Disposable)
           (java.util Collection))
  (:require [plugin.actions :as actions]
            [plugin.util :as util]
            [plugin.repl :as repl]
            [plugin.editor :as editor]
            [clojure.string :as str]
            [plugin.executor :as executor]
            [clj-stacktrace.core :as trace]
            [plugin.logging :as log]))

(defn set-title! [state title]
  (let [{:keys [^Content content]} @state]
    (util/invoke-later
      (.setDisplayName content title))))

(defn get-title [state]
  (let [{:keys [^Content content]} @state]
    (.getDisplayName content)))

(defn terminate-dialog [state]
  (let [{:keys [project]} @state
        title (get-title state)
        dialog (TerminateREPLDialog. project
                                     (str "REPL " title " is active")
                                     (str "Do you want to close the REPL " title "?")
                                     "Close")]
    (.show dialog)
    (= (.getExitCode dialog)
       DialogWrapper/OK_EXIT_CODE)))

(defn enabled! [state enabled]
  (let [{:keys [^EditorEx console-editor]} @state]
    (.setRendererMode console-editor (not enabled))
    (util/invoke-later
      (-> console-editor .getComponent .updateUI))))

(defn hide-editor [state]
  (let [{:keys [^ClojureConsoleView console-view console ^Editor history-viewer]} @state]
    (util/invoke-later
      (let [component (.getComponent console-view)
            parent (.getParent component)]
        (if (instance? JPanel parent)
          (do
            (.add parent (.getComponent history-viewer))
            (.remove parent component)
            (editor/scroll-down history-viewer)
            (.updateUI ^JComponent parent)))))))

(defn repl-submit [state command]
  (let [{:keys [repl-executor]} @state]
    (if repl-executor
      (executor/submit
        repl-executor
        (fn []
          (try
            (command)
            (catch Exception e#
              (let [ex (trace/parse-exception e#)]
                (repl/print-error state (str (:class ex) ": " (:message ex) "\n"))
                (if-let [cause (:cause ex)]
                  (repl/print-error state (str "Caused by:" (:class cause) ": " (:message cause) "\n")))))))))))

(defn do-execute [state immediately?]
  (boolean
    (let [{:keys [repl active? console-editor history-viewer project
                  history-index history-entries history-offsets]} @state]
      (when (active? state)
        (let [offset (editor/offset console-editor)
              text (editor/text-from console-editor)
              candidate (str/trim text)]
          (if (or immediately?
                  (str/blank? (.substring text offset)))
            (if (or (ClojurePsiUtil/isValidClojureExpression candidate project)
                    (str/blank? candidate))
              (do
                (when (not (str/blank? candidate))
                  (ClojureConsole/addTextRangeToHistory console-editor
                                                        history-viewer
                                                        (TextRange. 0
                                                                    (editor/text-length console-editor)))
                  (let [last-index (dec (count history-entries))
                        offset (editor/offset console-editor)
                        text (editor/text-from console-editor)]
                    (if (or (= history-index last-index)
                            (str/blank? (get history-entries last-index)))
                      (swap! state assoc
                             :history-index (count history-entries)
                             :history-entries (conj (assoc history-entries last-index text) "")
                             :history-offsets (conj (assoc history-offsets last-index offset) 0))
                      (swap! state assoc
                             :history-index (inc (count history-entries))
                             :history-entries (conj history-entries text "")
                             :history-offsets (conj history-offsets offset 0))))
                  (repl-submit state #(repl/execute repl state candidate)))
                (util/with-write-action
                  (editor/set-text console-editor "")
                  (editor/scroll-down history-viewer))
                true))))))))

(defn stop [state]
  (let [{:keys [repl active? on-stop repl-executor]} @state]
    (when (active? state)
      (if on-stop (on-stop state))
      (repl-submit state #(repl/stop repl state))
      (hide-editor state))))

(defn repl-listener [state]
  (proxy [ContentManagerAdapter ProjectManagerListener] []
    (contentRemoveQuery [^ContentManagerEvent event]
      (let [{:keys [content]} @state]
        (if (= content (.getContent event))
          (if (terminate-dialog state)
            (stop state)
            (.consume event)))))
    (projectOpened [project])
    (canCloseProject [closing-project]
      (let [{:keys [content ^ContentManager content-manager project]} @state]
        (if (= project closing-project)
          (let [ret (terminate-dialog state)]
            (when ret
              (stop state)
              (.removeContent content-manager content true))
            ret)
          true)))
    (projectClosed [project])
    (projectClosing [project])))

(defn focus-editor [state]
  (let [{:keys [project ^Editor console-editor]} @state
        focus-manager (IdeFocusManager/getInstance project)
        content-component (.getContentComponent console-editor)]
    (util/invoke-later
      (.requestFocus focus-manager content-component true))))

(defn init-content [state]
  (let [{:keys [active? content ^ContentManager content-manager project]} @state]
    (.addContent content-manager content)
    (.setSelectedContent content-manager content)
    (let [project-manager (ProjectManager/getInstance)
          listener (repl-listener state)]
      (.addProjectManagerListener project-manager ^Project project ^ProjectManagerListener listener)
      (.addContentManagerListener content-manager listener)
      (swap! state assoc
             :listener listener
             :on-stop (fn [state]
                        (.removeProjectManagerListener project-manager project listener)
                        (.removeContentManagerListener content-manager listener)
                        (swap! state dissoc :listener :on-stop)))
      (if (active? state)
        (focus-editor state)))))

(defn update-active? [state]
  (fn [^AnActionEvent event]
    (let [active? (:active? @state)]
      (.setEnabled (.getPresentation event) (boolean (active? state))))))

(defn execute-immediately [state]
  (actions/dumb-aware :action-performed (fn [event]
                                          (do-execute state true))
                      :update (update-active? state)
                      :shortcut-set "control ENTER"
                      :icon "/actions/execute.png"
                      :text "Execute Current Statement"))

(defn stop-action [state]
  (actions/dumb-aware :action-performed (fn [event]
                                          (stop state))
                      :update (update-active? state)
                      :shortcut-from IdeActions/ACTION_STOP_PROGRAM
                      :icon "/actions/suspend.png"
                      :text "Stop REPL"))

(defn close-action [state]
  (actions/dumb-aware :action-performed (fn [event]
                                          (let [{:keys [content ^ContentManager content-manager]} @state]
                                            (stop state)
                                            (.removeContent content-manager content true)))
                      :shortcut-from IdeActions/ACTION_CLOSE
                      :icon "/actions/cancel.png"
                      :text "Close REPL tab"))

(defn history-move [state next]
  (fn [^AnActionEvent event]
    (let [{:keys [history-index history-entries history-offsets console-editor]} @state]
      (util/with-write-action
        (let [previous (editor/text-from console-editor)
              previous-offset (editor/offset console-editor)
              next-index (next history-index)]
          (editor/set-text console-editor (get history-entries next-index))
          (editor/move-to console-editor (get history-offsets next-index))
          (swap! state assoc
                 :history-index next-index
                 :history-entries (assoc history-entries history-index previous)
                 :history-offsets (assoc history-offsets history-index previous-offset)))))))

(defn history-previous [state]
  (actions/dumb-aware :action-performed (history-move state dec)
                      :update (fn [^AnActionEvent event]
                                (let [{:keys [history-index active?]} @state
                                      presentation (.getPresentation event)]
                                  (.setEnabled presentation (boolean (and (active? state)
                                                                          (> history-index 0))))))
                      :shortcut-set "control UP"
                      :icon "/actions/previousOccurence.png"
                      :text "Select Previous History Item"))

(defn history-next [state]
  (actions/dumb-aware :action-performed (history-move state inc)
                      :update (fn [^AnActionEvent event]
                                (let [{:keys [history-index history-entries active?]} @state
                                      presentation (.getPresentation event)]
                                  (.setEnabled presentation (boolean (and (active? state)
                                                                          (< history-index (dec (count history-entries))))))))
                      :shortcut-set "control DOWN"
                      :icon "/actions/nextOccurence.png"
                      :text "Select Next History Item"))

(defn can-move-up-in-editor [state]
  (let [{:keys [console-editor]} @state]
    (if (LookupManager/getActiveLookup console-editor)
      false
      (= 0 (editor/line-number console-editor)))))

(defn can-move-down-in-editor [state]
  (let [{:keys [console-editor]} @state]
    (if (LookupManager/getActiveLookup console-editor)
      false
      (or (= 0 (editor/line-count console-editor))
          (= (editor/line-number console-editor)
             (dec (editor/line-count console-editor)))))))

(defn history-up [state]
  (actions/dumb-aware :action-performed (history-move state dec)
                      :update (fn [^AnActionEvent event]
                                (let [{:keys [history-index active?]} @state
                                      presentation (.getPresentation event)]
                                  (.setEnabled presentation (boolean (and (active? state)
                                                                          (> history-index 0)
                                                                          (can-move-up-in-editor state))))))
                      :shortcut-set "UP"
                      :visible false))

(defn history-down [state]
  (actions/dumb-aware :action-performed (history-move state inc)
                      :update (fn [^AnActionEvent event]
                                (let [{:keys [history-index history-entries active?]} @state
                                      presentation (.getPresentation event)]
                                  (.setEnabled presentation (boolean (and (active? state)
                                                                          (< history-index (dec (count history-entries)))
                                                                          (can-move-down-in-editor state))))))
                      :shortcut-set "DOWN"
                      :visible false))

(defn register-shortcuts [actions component]
  (doseq [^AnAction action actions]
    (if-let [shortcut-set (.getShortcutSet action)]
      (.registerCustomShortcutSet action shortcut-set component))))

(defn before-document-change [state ^DocumentEvent event]
  (let [{:keys [console-editor history-index history-entries history-offsets]} @state
        editor-document (.getDocument ^Editor console-editor)
        event-document (.getDocument event)]
    (if (and (= editor-document event-document)
             (< history-index (dec (count history-offsets))))
      (if (str/blank? (get history-entries (dec (count history-entries))))
        (swap! state assoc
               :history-index (dec (count history-offsets))
               :history-offsets (assoc history-offsets history-index (editor/offset console-editor))
               :history-entries (assoc history-entries history-index (editor/text-from console-editor)))
        (swap! state assoc
               :history-index (count history-offsets)
               :history-offsets (conj history-offsets (editor/offset console-editor))
               :history-entries (conj history-entries (editor/text-from console-editor)))))))

(defn create-repl
  "Creates a tool window for a REPL. The editor of the tool window is
  initially disabled, and must be enabled after the REPL completes its
  initialisation."
  [state title]
  (if-let [tool-window (.getToolWindow (ToolWindowManager/getInstance (:project @state))
                                       REPLToolWindowFactory/TOOL_WINDOW_ID)]
    (let [project (:project @state)
          console-view (ClojureConsoleView. project "REPL")
          console (.getConsole console-view)
          console-editor (.getConsoleEditor console)
          history-viewer (.getHistoryViewer console)
          toolbar-actions (DefaultActionGroup.)
          toolbar (actions/create-toolbar "unknown" toolbar-actions true)
          panel (JPanel. (BorderLayout.))
          actions [(execute-immediately state)
                   (stop-action state)
                   (close-action state)
                   (history-previous state)
                   (history-next state)
                   (history-up state)
                   (history-down state)]]
      (.setRendererMode console-editor true)
      (.addAll toolbar-actions ^Collection actions)
      (register-shortcuts actions (.getComponent console-editor))
      (register-shortcuts actions panel)

      (doto panel
        (.setBackground Color/WHITE)
        (.add (.getComponent toolbar) "North")
        (.add (.getComponent console-view) "Center")
        (.updateUI))

      (.putUserData console-editor ClojureConsole/STATE_KEY state)
      (.putCopyableUserData (.getFile console) ClojureConsole/STATE_KEY state)

      (let [content-factory (ContentFactory$SERVICE/getInstance)
            content (.createContent content-factory panel title false)
            manager (.getContentManager tool-window)
            editor-factory (EditorFactory/getInstance)
            multicaster (.getEventMulticaster editor-factory)
            repl-executor (executor/single-thread)]
        (.addContent manager content)
        (.putUserData content ClojureConsole/STATE_KEY state)
        (.addDocumentListener multicaster
                              (reify DocumentListener
                                (beforeDocumentChange [this event]
                                  (before-document-change state event))
                                (documentChanged [this event]))
                              content)
        (Disposer/register content
                           (reify Disposable
                             (dispose [this]
                               (log/info "Shutting down REPL executor")
                               (executor/shutdown repl-executor 30000))))

        (swap! state assoc
               :console console
               :console-view console-view
               :console-editor console-editor
               :history-viewer history-viewer
               :tool-window tool-window
               :content content
               :content-manager manager
               :repl-executor repl-executor
               :history-entries [""]
               :history-offsets [0]
               :history-index 0)
        (if (.isActive tool-window)
          (init-content state)
          (.activate tool-window #(init-content state)))
        (.show tool-window nil)))))
