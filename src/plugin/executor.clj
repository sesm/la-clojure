(ns plugin.executor
  (:import (java.util.concurrent Executors Callable ExecutorService Future TimeoutException TimeUnit)
           (clojure.lang IPending IBlockingDeref IDeref)))

(defn ^ExecutorService single-thread []
  (Executors/newSingleThreadExecutor))

(defn submit [^ExecutorService executor f]
  (let [fut (.submit executor ^Callable f)]
    (reify
            IDeref
      (deref [_] (.get fut))
      IBlockingDeref
      (deref [_ timeout-ms timeout-val]
        (try (.get fut timeout-ms TimeUnit/MILLISECONDS)
             (catch TimeoutException e
               timeout-val)))
      IPending
      (isRealized [_] (.isDone fut))
      Future
      (get [_] (.get fut))
      (get [_ timeout unit] (.get fut timeout unit))
      (isCancelled [_] (.isCancelled fut))
      (isDone [_] (.isDone fut))
      (cancel [_ interrupt?] (.cancel fut interrupt?)))))

(defn shutdown [^ExecutorService executor shutdown-time]
  (.shutdown executor)
  (if-not (.awaitTermination executor shutdown-time TimeUnit/MILLISECONDS)
    (.shutdownNow executor)))
