(ns plugin.util
  (:import (com.intellij.openapi.diagnostic Logger)
           (com.intellij.openapi.application ApplicationManager)
           (com.intellij.openapi.util Computable)
           (com.intellij.openapi.progress ProcessCanceledException)
           (java.lang.reflect InvocationTargetException)
           (com.intellij.openapi.project ProjectManager)
           (com.intellij.openapi.vfs VirtualFileManager)
           (com.intellij.openapi.fileEditor FileEditorManager)
           (com.intellij.psi PsiManager PsiFileFactory)
           (com.intellij.psi.util PsiUtilBase)
           (com.intellij.psi.impl.source.tree LeafPsiElement)
           (com.intellij.openapi.command CommandProcessor)))

(def ^Logger logger (Logger/getInstance "plugin.util"))

(defmacro with-read-action
  "Runs body inside a read action."
  [& body]
  `(.runReadAction
     (ApplicationManager/getApplication)
     (reify Computable
       (compute [this] ~@body))))

(defmacro with-command
  "Runs body inside a command."
  [project name group-id & body]
  `(.executeCommand
     (CommandProcessor/getInstance)
     (reify Runnable
       (run [this] ~@body))
     ~name
     ~group-id))

(defmacro safely
  "Allows safe Java method calls. If the target is nil returns nil, otherwise returns
  method call result."
  [form]
  `(if-not (nil? ~(second form)) ~form))

(defmacro with-logging
  "Calls body and logs any exceptions caught. Logs targets of InvocationTargetExceptions."
  [& body]
  `(try
     ~@body
     (catch ProcessCanceledException e#
       (throw e#))
     (catch InvocationTargetException e#
       (.error logger "Invocation target exception:" (.getTargetException e#))
       (throw e#))
     (catch Exception e#
       (.error logger e#)
       (throw e#))))


(defn project-manager [] (ProjectManager/getInstance))
(defn file-manager [] (VirtualFileManager/getInstance))
(defn editor-manager [project] (FileEditorManager/getInstance project))

(defn open-projects [] (seq (.getOpenProjects (project-manager))))
(defn psi-manager [project] (PsiManager/getInstance project))

(defn find-file [url] (.findFileByUrl (file-manager) url))
(defn psi-file [vfile project] (.findFile (psi-manager project) vfile))

(defn all-editors [project] (.getAllEditors (editor-manager project)))

; Mostly useful in the REPL
(defn make-file [text]
  (.createFileFromText (PsiFileFactory/getInstance (first (open-projects)))
                       "dummy-file.clj"
                       text))

(defn element []
  (let [editor (.getEditor (first (all-editors (first (open-projects)))))]
    (let [element (PsiUtilBase/getElementAtCaret editor)]
      (if (instance? LeafPsiElement element)
        (.getParent element)
        element))))
