(ns plugin.repl.actions
  (:import (com.intellij.openapi.actionSystem AnActionEvent PlatformDataKeys)
           (com.intellij.psi PsiDocumentManager)
           (com.intellij.openapi.project Project)
           (com.intellij.openapi.wm ToolWindowManager)
           (org.jetbrains.plugins.clojure.repl.toolwindow REPLToolWindowFactory)
           (org.jetbrains.plugins.clojure.repl REPL ClojureConsole Printing)
           (org.jetbrains.plugins.clojure.psi.api ClojureFile)
           (com.intellij.testFramework LightVirtualFile)
           (org.jetbrains.plugins.clojure ClojureIcons)
           (com.intellij.ui.content Content)
           (com.intellij.openapi.fileEditor FileDocumentManager)
           (com.intellij.openapi.diagnostic Logger)
           (com.intellij.openapi.editor Editor)
           (org.jetbrains.plugins.clojure.psi.util ClojurePsiUtil ClojurePsiElementFactoryImpl ClojurePsiFactory)
           (com.intellij.openapi.ui Messages))
  (:require [plugin.actions :as actions]
            [clojure.string :as str]
            [plugin.editor :as editor]
            [plugin.repl :as repl]))

(defn insert-history-before-current [state ^String command]
  (let [{:keys [history-index history-entries history-offsets]} @state]
    (swap! state assoc
           :history-index (inc history-index)
           :history-entries (conj (assoc history-entries history-index command)
                                  (get history-entries history-index))
           :history-offsets (conj (assoc history-offsets history-index (.length command))
                                  (get history-offsets history-index)))))

(defn execute-command [state command]
  (let [{:keys [repl history-viewer]} @state]
    (when-not (str/blank? command)
      (Printing/printToHistory history-viewer (str command "\n") Printing/NORMAL_TEXT)
      (editor/scroll-down history-viewer)
      (insert-history-before-current state command)
      (repl/execute repl state command))))

(defn execute-text-range [state editor text-range]
  (let [{:keys [repl history-viewer]} @state
        command (editor/text-from editor text-range)]
    (when-not (str/blank? command)
      (ClojureConsole/addTextRangeToHistory editor history-viewer text-range)
      (editor/scroll-down history-viewer)
      (insert-history-before-current state command)
      (repl/execute repl state command))))

(defn active-repl-state [^Project project]
  (let [manager (ToolWindowManager/getInstance project)
        tool-window (.getToolWindow manager REPLToolWindowFactory/TOOL_WINDOW_ID)]
    (if-let [^Content content (-> tool-window .getContentManager .getSelectedContent)]
      (.getUserData content REPL/STATE_KEY))))

(defn repl-action-state [^AnActionEvent event]
  (if-let [editor ^Editor (.getData event PlatformDataKeys/EDITOR)]
    (if-let [project (.getProject editor)]
      (if-let [document (.getDocument editor)]
        (if-let [psi-file (.getPsiFile (PsiDocumentManager/getInstance project)
                                       document)]
          (if-let [virtual-file (.getVirtualFile psi-file)]
            (if-let [file-path (.getPath virtual-file)]
              (if-let [state (active-repl-state project)]
                (let [{:keys [active? console]} @state]
                  (if (and (instance? ClojureFile psi-file)
                           (not (instance? LightVirtualFile virtual-file))
                           active?
                           (instance? ClojureConsole console))
                    {:editor       editor
                     :project      project
                     :document     document
                     :psi-file     psi-file
                     :virtual-file virtual-file
                     :file-path    file-path
                     :state        state}))))))))))

(defn update-repl-action [^AnActionEvent event]
  (let [presentation (.getPresentation event)]
    (.setEnabled presentation (not (nil? (repl-action-state event))))))

(defn run-sexp [^AnActionEvent event finder]
  (if-let [action-state (repl-action-state event)]
    (let [{:keys [state editor project]} action-state
          psi-factory (ClojurePsiElementFactoryImpl/getInstance project)]
      (if-let [sexp (finder editor)]
        (if (.hasSyntacticalErrors psi-factory sexp)
          (Messages/showErrorDialog project
                                    "S-expression contains syntax errors"
                                    "Cannot evaluate")
          (execute-text-range state editor (.getTextRange sexp)))))))

(defn run-top-sexp [^AnActionEvent event]
  (run-sexp event (fn [editor]
                    (ClojurePsiUtil/findTopSexpAroundCaret editor))))

(defn run-last-sexp [^AnActionEvent event]
  (run-sexp event (fn [editor]
                    (ClojurePsiUtil/findSexpAtCaret editor true))))

(defn run-selected [^AnActionEvent event]
  (if-let [action-state (repl-action-state event)]
    (let [{:keys [state editor project]} action-state
          psi-factory (ClojurePsiFactory/getInstance project)
          command (editor/selected-text editor)]
      (if-not (str/blank? command)
        (if-let [msg (.getErrorMessage psi-factory command)]
          (Messages/showErrorDialog project
                                    "Selected code fragment contains syntax errors"
                                    "Cannot evaluate")
          (execute-text-range state editor (editor/selected-text-range editor)))))))

(defn load-file [^AnActionEvent event]
  (if-let [action-state (repl-action-state event)]
    (let [{:keys [state file-path project]} action-state
          command (str "(load-file \"" file-path "\")")]
      (-> (PsiDocumentManager/getInstance project) .commitAllDocuments)
      (-> (FileDocumentManager/getInstance) .saveAllDocuments)
      (execute-command state command))))

(defn initialise []
  (actions/unregister-action ::clojure-repl-group "ToolsMenu")
  (let [group (actions/action-group :text "Clojure REPL")]
    (actions/register-action group ::clojure-repl-group "ToolsMenu"))

  (actions/unregister-action ::load-file ::clojure-repl-group)
  (let [action (actions/dumb-aware :action-performed load-file
                                   :update update-repl-action
                                   :text "Load file in REPL"
                                   :icon ClojureIcons/REPL_LOAD)]
    (actions/register-action action ::load-file ::clojure-repl-group)
    (actions/register-shortcut ::load-file "ctrl shift L"))

  (actions/unregister-action ::run-last-sexp ::clojure-repl-group)
  (let [action (actions/dumb-aware :action-performed run-last-sexp
                                   :update update-repl-action
                                   :text "Run sexp before cursor in REPL"
                                   :icon ClojureIcons/REPL_EVAL)]
    (actions/register-action action ::run-last-sexp ::clojure-repl-group)
    (actions/register-shortcut ::run-last-sexp "ctrl shift H"))

  (actions/unregister-action ::run-top-sexp ::clojure-repl-group)
  (let [action (actions/dumb-aware :action-performed run-top-sexp
                                   :update update-repl-action
                                   :text "Run top sexp in REPL"
                                   :icon ClojureIcons/REPL_EVAL)]
    (actions/register-action action ::run-top-sexp ::clojure-repl-group)
    (actions/register-shortcut ::run-top-sexp "ctrl shift G"))

  (actions/unregister-action ::run-selected ::clojure-repl-group)
  (let [action (actions/dumb-aware :action-performed run-selected
                                   :update update-repl-action
                                   :text "Run selected text in REPL"
                                   :icon ClojureIcons/REPL_EVAL)]
    (actions/register-action action ::run-selected ::clojure-repl-group)
    (actions/register-shortcut ::run-selected "ctrl shift J")))
