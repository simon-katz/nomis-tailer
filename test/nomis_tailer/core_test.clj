(ns nomis-tailer.core-test
  (:require [clojure.core.async :as a]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [midje.sweet :refer :all]
            [nomis-tailer.core :as subject])
  (:import (java.io File)))

(defn do-pretend-logging-with-rotation
  "Send lines to `f` in a manner that is similar to the way logging happens."
  ;; When making changes here, think about `sleep-ms` and how it relates to
  ;; the underlying TailerListener's delay-ms.
  [f lines-s sleep-ms]
  ;; (println "do-pretend-logging-with-rotation" (.getName f))
  (Thread/sleep sleep-ms)
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

(defn make-and-initialise-log-file [filename]
  (let [f (File. filename)]
    ;; Setting up some initial content makes things work as I expect;
    ;; without this my first line is lost.
    ;; jsk-2016-10-29
    (io/make-parents f)
    (spit f (str (str/join "\n" (repeat 100 "this will be ignored"))
                 "\n"))
    f))

(fact "`make-tailer-and-channel` works"
  (let [delay-ms  50
        sleep-ms  100
        lines-s   [["1-1" "2-1" "3-1" "4-1" "5-1"]
                   ["1-2" "2-2" "3-2" "4-2" "5-2"]
                   ["1-3" "2-3" "3-3" "4-3" "5-3"]]
        file      (make-and-initialise-log-file "test/_work-dir/plop.log")
        t-and-c   (subject/make-tailer-and-channel file
                                                   delay-ms)
        result-ch (a/thread (doall (-> t-and-c
                                       subject/channel
                                       chan->seq)))]
    (do-pretend-logging-with-rotation file lines-s sleep-ms)
    (subject/close! t-and-c)
    (a/<!! result-ch))
  => ["1-1" "2-1" "3-1" "4-1" "5-1"
      "1-2" "2-2" "3-2" "4-2" "5-2"
      "1-3" "2-3" "3-3" "4-3" "5-3"])

(fact "`make-multi-tailer-and-channel` works"
  (let [delay-ms             50
        sleep-ms             100
        file-change-delay-ms 300
        rollover-delay-ms    500
        basic-lines-s        [["1-1" "2-1" "3-1" "4-1" "5-1"]
                              ["1-2" "2-2" "3-2" "4-2" "5-2"]
                              ["1-3" "2-3" "3-3" "4-3" "5-3"]]
        modify-lines-s       (fn [prefix]
                               (map (fn [lines]
                                      (map #(str prefix %)
                                           lines))
                                    basic-lines-s))
        file-1               (make-and-initialise-log-file
                              "test/_work-dir/plopplop-1.log")
        dir                  (File. "test/_work-dir")
        pattern              #"plopplop-.\.log"
        mt-and-c             (subject/make-multi-tailer-and-channel
                              dir
                              pattern
                              delay-ms
                              file-change-delay-ms)
        result-ch            (a/thread (doall (-> mt-and-c
                                                  subject/channel
                                                  chan->seq)))]
    (doseq [i ["a" "b" "c"]]
      (let [file (make-and-initialise-log-file
                  (str "test/_work-dir/plopplop-" i ".log"))]
        (do-pretend-logging-with-rotation file
                                          (modify-lines-s (str i "-"))
                                          sleep-ms)
        (Thread/sleep rollover-delay-ms)))
    (subject/close-mt-and-c! mt-and-c)
    (a/<!! result-ch))
  => ["a-1-1" "a-2-1" "a-3-1" "a-4-1" "a-5-1"
      "a-1-2" "a-2-2" "a-3-2" "a-4-2" "a-5-2"
      "a-1-3" "a-2-3" "a-3-3" "a-4-3" "a-5-3"
      "b-1-1" "b-2-1" "b-3-1" "b-4-1" "b-5-1"
      "b-1-2" "b-2-2" "b-3-2" "b-4-2" "b-5-2"
      "b-1-3" "b-2-3" "b-3-3" "b-4-3" "b-5-3"
      "c-1-1" "c-2-1" "c-3-1" "c-4-1" "c-5-1"
      "c-1-2" "c-2-2" "c-3-2" "c-4-2" "c-5-2"
      "c-1-3" "c-2-3" "c-3-3" "c-4-3" "c-5-3"])
