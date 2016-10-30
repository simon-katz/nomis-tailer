(ns nomis-tailer.core-test
  (:require [clojure.core.async :as a]
            [clojure.java.io :as io]
            [midje.sweet :refer :all]
            [nomis-tailer.core :as subject])
  (:import (java.io File)))

(defn do-pretend-logging-with-rotation
  "Send lines to `f` in a manner that is similar to the way logging happens."
  ;; When making changes here, think about `sleep-ms` and how it relates to
  ;; the underlying TailerListener's delay-ms.
  [f lines-s sleep-ms]
  (doseq [lines lines-s]
    (spit f "") ; rotate
    (doseq [line lines]
      (spit f
            (str line "\n")
            :append true)
      (Thread/sleep sleep-ms))))

(defn chan->seq [c]
  (lazy-seq
   (when-let [v (a/<!! c)]
     (cons v (chan->seq c)))))

(fact (let [delay-ms  50
            sleep-ms  100
            lines-s   [["I met" "her" "in a" "pool room"]
                       ["her name" "I didn't" "catch"]
                       ["she" "looked" "like" "something special"]]
            file      (let [f (File. "test/_work-dir/plop.log")]
                        ;; Setting up some initial content makes things
                        ;; work as I expect; without this my first line
                        ;; is lost.
                        ;; jsk-2016-10-29
                        (io/make-parents f)
                        (spit f "this will be ignored this will be ignored this will be ignored this will be ignored\n")
                        f)               
            t-and-c   (subject/make-tailer-and-channel file delay-ms)
            result-ch (a/thread (doall (-> t-and-c
                                           subject/channel
                                           chan->seq)))]
        (do-pretend-logging-with-rotation file lines-s sleep-ms)
        (subject/close! t-and-c)
        (a/<!! result-ch)
        => ["I met" "her" "in a" "pool room" "her name" "I didn't" "catch"
            "she" "looked" "like" "something special"]))
