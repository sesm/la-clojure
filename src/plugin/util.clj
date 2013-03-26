(ns plugin.util
  (:import (com.intellij.openapi.diagnostic Logger)
           (com.intellij.openapi.application ApplicationManager)
           (com.intellij.openapi.util Computable)
           (com.intellij.openapi.progress ProcessCanceledException)
           (java.lang.reflect InvocationTargetException)
           (com.intellij.openapi.project ProjectManager)
           (com.intellij.openapi.vfs VirtualFileManager)
           (com.intellij.openapi.fileEditor FileEditorManager FileEditor TextEditor FileDocumentManager)
           (com.intellij.psi PsiManager PsiFileFactory)
           (com.intellij.psi.util PsiUtilBase)
           (com.intellij.psi.impl.source.tree LeafPsiElement)
           (com.intellij.openapi.command CommandProcessor)
           (com.intellij.openapi.editor Editor)))

(def ^Logger logger (Logger/getInstance "plugin.util"))

(defmacro with-read-action
  "Runs body inside a read action."
  [& body]
  `(.runReadAction
     (ApplicationManager/getApplication)
     (reify Computable
       (compute [this] ~@body))))

(defmacro with-write-action
  "Runs body inside a write action."
  [& body]
  `(.runWriteAction
     (ApplicationManager/getApplication)
     (reify Runnable
       (run [this] ~@body))))

(defmacro with-command
  "Runs body inside a command."
  [project name group-id & body]
  `(.executeCommand
     (CommandProcessor/getInstance)
     (reify Runnable
       (run [this] ~@body))
     ~name
     ~group-id))

(defmacro invoke-later
  "Runs body asynchronously on the EDT."
  [& body]
  `(.invokeLater
     (ApplicationManager/getApplication)
     (reify Runnable
       (run [this] ~@body))))


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


(defn ^ProjectManager project-manager [] (ProjectManager/getInstance))
(defn ^VirtualFileManager file-manager [] (VirtualFileManager/getInstance))
(defn ^FileEditorManager editor-manager [project] (FileEditorManager/getInstance project))

(defn open-projects [] (seq (.getOpenProjects (project-manager))))
(defn ^PsiManager psi-manager [project] (PsiManager/getInstance project))

(defn find-file [url] (.findFileByUrl (file-manager) url))
(defn psi-file [vfile project] (.findFile (psi-manager project) vfile))

(defn all-text-editors [project] (seq (.getAllEditors (editor-manager project))))

; Mostly useful in the REPL
(defn make-file [^String text]
  (let [file-factory (PsiFileFactory/getInstance (first (open-projects)))]
    (.createFileFromText file-factory
                         "dummy-file.clj"
                         text)))

(defn get-file-name [^Editor editor]
  (safely (.getName (.getFile (FileDocumentManager/getInstance)
                              (.getDocument editor)))))

(defn find-editor [name-pattern]
  (let [text-editors (flatten (map all-text-editors (open-projects)))
        editors (map #(.getEditor ^TextEditor %) text-editors)]
    (first (filter #(let [name (get-file-name %)]
                      (if-not (nil? name)
                        (boolean (re-find name-pattern name))
                        false))
                   editors))))

(defn element [name-pattern]
  (if-let [editor (find-editor name-pattern)]
    (let [element (PsiUtilBase/getElementAtCaret editor)]
      (if (instance? LeafPsiElement element)
        (.getParent element)
        element))))
