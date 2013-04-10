(ns plugin.logging
  (:import (java.lang.reflect InvocationTargetException)
           (com.intellij.openapi.progress ProcessCanceledException)
           (com.intellij.openapi.diagnostic Logger))
  (:require [clj-stacktrace.repl :as trace]))

(def loggers (atom {}))

(defn cache-logger [loggers ^String key]
  (if-let [logger (loggers key)]
    loggers
    (assoc loggers key (Logger/getInstance key))))

(defn ^Logger get-logger [key]
  (let [loggers# (swap! loggers cache-logger key)]
    (loggers# key)))

(defmacro with-logging
  "Calls body and logs any exceptions caught. Logs targets of InvocationTargetExceptions."
  [& body]
  `(let [logger# (get-logger (str *ns*))]
     (try
       ~@body
       (catch ProcessCanceledException e#
         (throw e#))
       (catch InvocationTargetException e#
         (.error logger# (str "Invocation target exception:\n" (trace/pst-str (.getTargetException e#))))
         (throw e#))
       (catch Exception e#
         (.error logger# (trace/pst-str e#))
         (throw e#)))))

(defn do-log
  "Logging implementation function. Accepts key, a description for the logger,
   and log, a function accepting a logger and string message. Args will be
   formatted using str and the first argument treated as an exception if it
   is Throwable."
  [key log & args]
  (let [logger (get-logger key)]
    (if (instance? Throwable (first args))
      (log logger (str (apply str (rest args)) "\n" (trace/pst-str (first args))))
      (log logger (apply str args)))))

(defn do-debug [^Logger logger ^String message]
  (.debug logger message))

(defn do-info [^Logger logger ^String message]
  (.info logger message))

(defn do-warn [^Logger logger ^String message]
  (.warn logger message))

(defn do-error [^Logger logger ^String message]
  (.error logger message))

(defmacro debug [& args]
  `(do-log ~(str *ns*) do-debug ~@args))

(defmacro info [& args]
  `(do-log ~(str *ns*) do-info ~@args))

(defmacro warn [& args]
  `(do-log ~(str *ns*) do-warn ~@args))

(defmacro error [& args]
  `(do-log ~(str *ns*) do-error ~@args))
