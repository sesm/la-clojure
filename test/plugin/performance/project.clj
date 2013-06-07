(ns plugin.performance.project
  (:import (com.intellij.openapi.project Project)
           (com.intellij.psi.search GlobalSearchScope FileTypeIndex GlobalSearchScopes)
           (com.intellij.psi PsiManager PsiElement PsiReference PsiPolyVariantReference PsiFile)
           (org.jetbrains.plugins.clojure.metrics Metrics)
           (java.util.concurrent TimeUnit)
           (com.intellij.openapi.fileTypes FileTypeManager)
           (com.intellij.openapi.vfs VirtualFileFilter VirtualFile)
           (org.jetbrains.plugins.clojure.file ClojureFileType)
           (com.intellij.psi.impl PsiManagerImpl))
  (:require [plugin.util :as util]))

(defn find-project [name-pattern]
  (first (filter #(re-find name-pattern (.getName ^Project %)) (util/open-projects))))

(defmacro with-timer [project name & body]
  `(let [project# ~project
         instance# (.start (Metrics/getInstance project#) ~name)]
     (try
       ~@body
       (finally
         (.stop instance#)))))

(defmacro with-stub-switch-alert [project & body]
  `(let [psi-manager# ^PsiManagerImpl (PsiManager/getInstance ~project)
         filter# (reify VirtualFileFilter
                   (accept [this# file#]
                     (= ClojureFileType/CLOJURE_FILE_TYPE
                        (.getFileType file#))))]
     (.setAssertOnFileLoadingFilter psi-manager# filter#)
     (try
       ~@body
       (finally
         (.setAssertOnFileLoadingFilter psi-manager# VirtualFileFilter/NONE)))))

(defn resolve-all-elements [^PsiElement element [total refs resolved]]
  (let [result (reduce (fn [[total refs resolved] ref]
                         (let [project (.getProject element)
                               resolved? (or (and (instance? PsiPolyVariantReference ref)
                                                  (if-let [result (with-stub-switch-alert
                                                                    project
                                                                    (with-timer
                                                                      project "total.multiResolve"
                                                                      (.multiResolve ^PsiPolyVariantReference ref false)))]
                                                    (> (alength result) 0)))
                                             (with-stub-switch-alert
                                               project
                                               (with-timer project "total.resolve"
                                                           (.resolve ^PsiReference ref))))]
                           (if resolved?
                             [total (inc refs) (inc resolved)]
                             [total (inc refs) resolved])))
                       [(inc total) refs resolved]
                       (.getReferences element))]
    (reduce (fn [counts child]
              (resolve-all-elements child counts))
            result
            (.getChildren element))))

(defn print-report [project]
  (let [factor (/ 1.0 (.toNanos TimeUnit/MILLISECONDS 1))]
    (println)
    (println (format "%-35s %6s %8s %8s %8s" "Name" "Count" "Max" "Mean" "StdDev"))
    (println (format "%-35s %6s %8s %8s %8s" "----" "-----" "---" "----" "------"))
    (doseq [snapshot (map #(.getSnapshot %)
                          (-> (Metrics/getInstance project)
                              .getTimers
                              .values))]
      (println (format "%-35s %6d %8.3f %8.3f %8.3f"
                       (.name snapshot)
                       (.count snapshot)
                       (* factor (.max snapshot))
                       (* factor (.mean snapshot))
                       (* factor (.stdDev snapshot)))))))

(defn calculate-totals [project ^PsiManager psi-manager files]
  (reduce (fn [totals i]
            (let [print? (= i 4)]
              (.reset (Metrics/getInstance project))
              (.dropResolveCaches psi-manager)
              (when print?
                (println)
                (println (format "%-45s %6s %6s %9s" "File" "Elems" "Refs" "Resolved"))
                (println (format "%-45s %6s %6s %9s" "----" "-----" "----" "--------")))
              (reduce (fn [totals file]
                        (when print?
                          (print (format "%-45s" (.getName ^PsiFile file))))
                        (let [[total refs resolved :as result] (resolve-all-elements file [0 0 0])
                              resolved-percent (/ (* 100.0 resolved) refs)]
                          (when print?
                            (println (format " %6d %6d %8.2f%%" total refs resolved-percent)))
                          ; Only update totals on last iteration
                          (if print?
                            (apply vector (map + totals result))
                            totals)))
                      totals
                      (map #(.findFile psi-manager %) files))))
          [0 0 0]
          (range 5)))

(defn resolve-all [name-pattern file-extension]
  (if-let [project (find-project name-pattern)]
    (let [scope (GlobalSearchScopes/projectProductionScope project)
          file-type (.getFileTypeByExtension (FileTypeManager/getInstance) file-extension)
          files (FileTypeIndex/getFiles file-type scope)
          num-files (count files)
          psi-manager (PsiManager/getInstance project)
          [total refs resolved] (calculate-totals project psi-manager files)
          resolved-percent (/ (* 100.0 resolved) refs)]
      (println)
      (println (format "%-45s %6d %6d %8.2f%%" "Totals:" total refs resolved-percent))
      (println)
      (println (format "%-20s %5.2f" "Elements/file:" (double (/ total num-files))))
      (println (format "%-20s %5.2f" "Refs/file:" (double (/ refs num-files))))
      (println (format "%-20s %5.2f" "Refs/element:" (double (/ refs total))))
      (print-report project))))
