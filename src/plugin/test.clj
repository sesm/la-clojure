(ns plugin.test
  (:use [clojure.test :only [assert-expr do-report]])
  (:require [clojure.string :as str])
  (:import (com.intellij.testFramework LightPlatformCodeInsightTestCase)
           (com.intellij.openapi.actionSystem ActionManager AnActionEvent)
           (com.intellij.ide DataManager)
           (com.intellij.psi PsiDocumentManager PsiReference PsiPolyVariantReference PsiNamedElement)
           (com.intellij.openapi.editor.actionSystem EditorActionManager)
           (com.intellij.psi.codeStyle CodeStyleManager)
           (junit.framework Assert AssertionFailedError)
           (org.jetbrains.plugins.clojure.psi.api.symbols ClSymbol)
           (org.jetbrains.plugins.clojure.psi.api ClojureFile)
           (com.intellij.psi.stubs StubTree)))


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

(defn re-pos [re string]
  (loop [s string
         res {}]
    (let [m (re-matcher re s)]
      (if (.find m)
        (let [start (.start m)
              match (.group m)
              len (.length match)]
          (recur (str (.substring s 0 start)
                      (.substring s (+ start len)))
                 (assoc res start (.substring match 1 (dec len)))))
        [s res]))))

(defn find-key [map value]
  (loop [items (seq map)]
    (when (seq items)
      (if (= (val (first items)) value)
        (key (first items))
        (recur (rest items))))))

(defn fail [& messages]
  (throw (AssertionFailedError. (apply str messages))))

(defn assert= [expected actual message]
  (Assert/assertEquals message expected actual))

(defn check-self-resolution [element]
  (if (and (instance? PsiPolyVariantReference element)
           (some #(= element (.getElement %))
                 (seq (.multiResolve element false))))
    (fail (.getText element) ":" (.getTextOffset element) " resolves to self"))
  (doseq [child (.getChildren element)]
    (check-self-resolution child)))

(defn check-resolve [text params extra]
  (let [[text tags] (re-pos #"<[^ >]+>" text)
        file ((:create-file params) "check-resolve.clj" text)]
    (if (:use-clojure-core extra)
      ((:create-file params) "clojure-core.clj" ((:clojure-core params))))
    (if (:files extra)
      (doseq [[file text] (:files extra)]
        ((:create-file params) file text)))
    (check-self-resolution file)
    (doseq [entry tags]
      (let [offset (key entry)
            item (val entry)]
        (when (not (.startsWith item "@"))
          (let [ref (or (.findReferenceAt file offset)
                        (fail "Can't find reference at " item))
                resolve-elements (map #(.getElement %)
                                      (filter #(not (nil? (.getElement %)))
                                              (seq (.multiResolve ref false))))
                offsets (set (map #(.getTextOffset %) resolve-elements))
                ref-text (.getText (.getElement ref))]
            (cond
              (re-matches #"[0-9]+" item)
              (let [target-offset (or (find-key tags (str "@" item))
                                      (fail "Can't find target for ref " item))
                    target (or (.findElementAt file target-offset)
                               (fail "Can't find element at @" item))]
                (if-not (offsets target-offset)
                  (fail ref-text
                        ":"
                        offset
                        " does not resolve to "
                        (.getText target)
                        ":"
                        target-offset)))
              (= "/" item)
              (if-not (empty? offsets)
                (fail ref-text
                      ":"
                      offset
                      " should not resolve but resolves to "
                      (count offsets)
                      " item(s): "
                      offsets))
              (.contains item "/")
              (if-not (= 1 (count resolve-elements))
                (fail ref-text
                      ":"
                      offset
                      " should uniquely resolve but resolves to "
                      (count resolve-elements)
                      " item(s): "
                      resolve-elements)
                (let [target (first resolve-elements)
                      psi-file (.getContainingFile target)
                      [nspace sym-name] (str/split item #"/" 2)]
                  (if (and (instance? PsiNamedElement target)
                           (instance? ClojureFile psi-file))
                    (do
                      (assert= sym-name (.getName target) "symbol")
                      (assert= nspace (.getNamespace psi-file) "namespace"))
                    (fail "Unexpected types " (class target) " " (class psi-file))))))))))))

(defmethod assert-expr 'editor-action-result? [msg form]
  `(let [action# ~(nth form 1)
         before# ~(nth form 2)
         after# ~(nth form 3)]
     (try (.doTest
            (plugin.testcase.EditorModification.
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
          (catch AssertionFailedError e#
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
            (plugin.testcase.EditorModification.
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
          (catch AssertionFailedError e#
            (do-report {:type     :fail,
                        :message  (str ~msg ": " (.getMessage e#)),
                        :expected '~form,
                        :actual   e#})
            e#))))

(defmethod assert-expr 'reformat-result? [msg form]
  `(let [before# ~(nth form 1)
         after# ~(nth form 2)]
     (try (.doTest
            (plugin.testcase.EditorModification.
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
          (catch AssertionFailedError e#
            (do-report {:type     :fail,
                        :message  (str ~msg ": " (.getMessage e#)),
                        :expected '~form,
                        :actual   e#})
            e#))))

(defmethod assert-expr 'valid-resolve? [msg form]
  `(let [text# ~(nth form 1)
         extra# ~(let [params (drop 2 form)]
                   (if (seq params)
                     (apply assoc {} params)))]
     (try (.doTest
            (plugin.testcase.PsiFile.
              (fn [this# params#]
                (check-resolve text# params# extra#))))
          (do-report {:type     :pass,
                      :message  ~msg,
                      :expected '~form,
                      :actual   nil})
          (catch AssertionFailedError e#
            (do-report {:type     :fail,
                        :message  (str ~msg ": " (.getMessage e#)),
                        :expected '~form,
                        :actual   e#})
            e#))))
