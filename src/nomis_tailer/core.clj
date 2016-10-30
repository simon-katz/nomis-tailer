(ns nomis-tailer.core
  (:require [clojure.core.async :as a])
  (:import (org.apache.commons.io.input Tailer
                                        TailerListener)))

(defn ^:private tailer-listener [c]
  (reify TailerListener
    (init [this tailer]
      ;; no-op
      )
    (fileNotFound [this]
      ;; (println "File not found")
      )
    (fileRotated [this]
      ;; (println "Rotation detected")
      )
    (^void handle [this ^String line]
     ;; (println "Handling" line)
     (a/>!! c line))
    (^void handle [this ^Exception e]
     (throw e))))

(defn ^:private make-tailer-and-channel-impl [file delay-ms from-end?]
  (let [ch        (a/chan)
        tailer    (Tailer/create file
                                 (tailer-listener ch)
                                 delay-ms
                                 from-end?)]
    {::channel ch
     ::tailer  tailer}))

(defn make-tailer-and-channel [file delay-ms]
  (make-tailer-and-channel-impl file delay-ms true))

(defn channel [t-and-c]
  (::channel t-and-c))

(defn close! [{:keys [::channel ::tailer]}]
  (.stop tailer)
  (a/close! channel))

(defn ^:private files-in-dir-matching-pattern [dir pattern]
  "A sequence of files in dir"
  (->> dir
       .listFiles
       (filter (fn [x] (.isFile x)))
       (filter #(re-matches pattern (.getName %)))))

(defn most-recent-file-matching-pattern [dir pattern]
  (let [files-and-last-mod-times (let [fs (files-in-dir-matching-pattern dir
                                                                         pattern)]
                                   (map (fn [f] [f
                                                 (.lastModified f)])
                                        fs))
        [most-recent-file _] (if (empty? files-and-last-mod-times)
                               nil
                               (reduce (fn [[f1 m1] [f2 m2]]
                                         (if (> m1 m2)
                                           [f1 m1]
                                           [f2 m2]))
                                       files-and-last-mod-times))]
    #_(println "files-and-last-mod-times ="
             (map (fn [x] [(.getName (first x))
                           (second x)])
                  files-and-last-mod-times))
    most-recent-file))

(defn make-multi-tailer-and-channel [dir pattern delay-ms]
  (let [out-ch     (a/chan)
        control-ch (a/chan)]
    (a/go
      (letfn [(get-most-recent-file []
                (most-recent-file-matching-pattern dir pattern))
              (new-t-and-c [most-recent-file first?]
                (let [t-and-c
                      (make-tailer-and-channel-impl most-recent-file
                                                    delay-ms
                                                    first?)]
                  (a/go-loop []
                    (let [v (a/<! (channel t-and-c))]
                      (when v
                        (a/>! out-ch v)
                        (recur))))
                  t-and-c))]
        (let [outer-delay-ms         1000 ; TODO OK? Make this an arg.
              first-most-recent-file (get-most-recent-file)]
          (let [first-t-and-c (when first-most-recent-file
                                (new-t-and-c first-most-recent-file
                                             true))]
            (loop [most-recent-file first-most-recent-file
                   t-and-c          first-t-and-c]
              (a/<!! (a/timeout outer-delay-ms))
              (let [[v ch] (a/alts!! [control-ch
                                      (a/timeout 1000 ; TODO
                                                 )]
                                     :priority true)]
                (if (= v :stop)
                  (when t-and-c
                    (close! t-and-c))                  
                  (let [next-most-recent-file (get-most-recent-file)]
                    (if (or (nil? most-recent-file)
                            (= next-most-recent-file
                               most-recent-file))
                      ;; just leave things as they are
                      (recur most-recent-file
                             t-and-c)
                      (do (when t-and-c
                            (close! t-and-c))
                          (let [next-t-and-c (new-t-and-c next-most-recent-file
                                                          false)]
                            (recur next-most-recent-file
                                   next-t-and-c))))))))))))
    {::channel out-ch
     ::control-ch control-ch}))

(defn close-mt-and-c! [mt-and-c]
  (a/>!! (::control-ch mt-and-c)
         :stop)
  (a/close! (::channel mt-and-c)))
