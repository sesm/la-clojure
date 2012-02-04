(ns plugin.test
  (:use [clojure.test :only [assert-expr do-report]])
  (:import (com.intellij.testFramework LightPlatformCodeInsightTestCase)
           (com.intellij.openapi.actionSystem ActionManager AnActionEvent)
           (com.intellij.ide DataManager)
           (com.intellij.psi PsiDocumentManager)
           (com.intellij.openapi.editor.actionSystem EditorActionManager)
           (com.intellij.psi.codeStyle CodeStyleManager)))


(defn invoke-action [action-id params]
  (let [action-manager (ActionManager/getInstance)
        action (.getAction action-manager action-id)]
    (.actionPerformed action
                      (AnActionEvent. nil
                                      (:data-context params)
                                      ""
                                      (.getTemplatePresentation action)
                                      action-manager
                                      0))))

(defn do-typing-action [character params]
  (let [action-manager (EditorActionManager/getInstance)
        typed-action (.getTypedAction action-manager)]
    (.actionPerformed typed-action
                      (:editor params)
                      character
                      (:data-context params))))

(defn reformat [params]
  (let [style-manager (CodeStyleManager/getInstance (:project params))
        psi-file (:psi-file params)
        text-range (.getTextRange psi-file)]
    (.reformatText style-manager
                   psi-file
                   (.getStartOffset text-range)
                   (.getEndOffset text-range))))

(defmethod assert-expr 'editor-action-result? [msg form]
  `(let [action# ~(nth form 1)
         before# ~(nth form 2)
         after# ~(nth form 3)]
     (try (.doTest
            (plugin.testcase.modification.
              (fn [this# params#]
                (invoke-action action# params#))
              (fn [this#]
                (.doModificationTest this#
                                     before#
                                     after#))))
          (do-report {:type     :pass,
                      :message  ~msg,
                      :expected '~form,
                      :actual   nil})
          (catch junit.framework.AssertionFailedError e#
            (do-report {:type     :fail,
                        :message  (str ~msg ": " (.getMessage e#)),
                        :expected '~form,
                        :actual   e#})
            e#))))

(defmethod assert-expr 'typing-result? [msg form]
  `(let [character# ~(nth form 1)
         before# ~(nth form 2)
         after# ~(nth form 3)]
     (try (.doTest
            (plugin.testcase.modification.
              (fn [this# params#]
                (do-typing-action character# params#))
              (fn [this#]
                (.doModificationTest this#
                                     before#
                                     after#))))
          (do-report {:type     :pass,
                      :message  ~msg,
                      :expected '~form,
                      :actual   nil})
          (catch junit.framework.AssertionFailedError e#
            (do-report {:type     :fail,
                        :message  (str ~msg ": " (.getMessage e#)),
                        :expected '~form,
                        :actual   e#})
            e#))))

(defmethod assert-expr 'reformat-result? [msg form]
  `(let [before# ~(nth form 1)
         after# ~(nth form 2)]
     (try (.doTest
            (plugin.testcase.modification.
              (fn [this# params#]
                (reformat params#))
              (fn [this#]
                (.doModificationTest this#
                                     before#
                                     after#))))
          (do-report {:type     :pass,
                      :message  ~msg,
                      :expected '~form,
                      :actual   nil})
          (catch junit.framework.AssertionFailedError e#
            (do-report {:type     :fail,
                        :message  (str ~msg ": " (.getMessage e#)),
                        :expected '~form,
                        :actual   e#})
            e#))))
