(ns nomis-tailer.core
  (:require [clojure.core.async :as a]
            [nomis-tailer.files :as files])
  (:import (org.apache.commons.io.input Tailer
                                        TailerListener)))

;;;; ___________________________________________________________________________
;;;; ---- Implementation ----

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

(defn ^:private make-single-file-tailer-impl [file delay-ms from-end?]
  (let [ch        (a/chan)
        tailer    (Tailer/create file
                                 (tailer-listener ch)
                                 delay-ms
                                 from-end?)]
    {:type     :single-file-tailer
     ::channel ch
     ::tailer  tailer}))

;;;; ___________________________________________________________________________
;;;; ---- Generic stuff ----


;;;; FIXME Need to fix doc strings. Maybe the README also.


(defmulti channel
  "Returns this tailer's channel.
  Take from this to get lines from the file(s)."
  :type)

(defmulti close!
  "Closes this tailer's channel, and stops/closes implementation resources."
  :type)

;;;; ___________________________________________________________________________
;;;; ---- single-file-tailer ----

(defn make-single-file-tailer
  "Returns a single-file-tailer for `file`.
  The tailing starts from the end of the file.
  `delay-ms` is the delay between checks of the file for new content."
  [file delay-ms]
  (make-single-file-tailer-impl file delay-ms true))

(defmethod channel :single-file-tailer
  [{:keys [::channel ::tailer]}]
  channel)

(defmethod close! :single-file-tailer
  [{:keys [::channel ::tailer]}]
  (.stop tailer)
  (a/close! channel))

;;;; ___________________________________________________________________________
;;;; ---- multi-file-tailer ----

(defn make-multi-file-tailer
  "Like `make-single-file-tailer`, but looks for the most recent file in `dir`
  that matches `pattern`. Looks for new files every `new-file-check-frequency-ms`.
  The tailing starts from the end of any current file, and includes the
  full content of subsequent files.
  Returns a multi-file-tailer."
  ([dir pattern delay-ms new-file-check-frequency-ms]
   (make-multi-file-tailer dir
                           pattern
                           delay-ms
                           new-file-check-frequency-ms
                           1000))
  ([dir pattern delay-ms new-file-check-frequency-ms delay-ms-to-finish-old-file]
   (let [out-ch     (a/chan)
         control-ch (a/chan)]
     (letfn [(get-most-recent-file []
               (files/most-recent-file-matching-pattern dir pattern))
             (new-tailer [most-recent-file first?]
               (let [tailer (make-single-file-tailer-impl most-recent-file
                                                          delay-ms
                                                          first?)]
                 (a/go-loop []
                   (let [v (a/<! (::channel tailer))]
                     (when v
                       (a/>! out-ch v)
                       (recur))))
                 tailer))]
       (a/go
         (let [first-most-recent-file (get-most-recent-file)]
           (let [first-tailer (when first-most-recent-file
                                (new-tailer first-most-recent-file
                                            true))]
             (loop [prev-most-recent-file first-most-recent-file
                    prev-tailer           first-tailer]
               (let [[v ch] (a/alts! [control-ch
                                      (a/timeout new-file-check-frequency-ms)]
                                     :priority true)]
                 (if (= v :stop)
                   (when prev-tailer
                     (close! prev-tailer))
                   (let [most-recent-file     (get-most-recent-file)
                         new-file-to-process? (and most-recent-file
                                                   (not= most-recent-file
                                                         prev-most-recent-file))]
                     (if-not new-file-to-process?
                       ;; just leave things as they are
                       (recur prev-most-recent-file
                              prev-tailer)
                       (do (when prev-tailer
                             ;; Give time to finish tailing the previous file
                             ;; before closing the channel.
                             (a/<! (a/timeout  delay-ms-to-finish-old-file))
                             (close! prev-tailer))
                           (recur most-recent-file
                                  (new-tailer most-recent-file
                                              false))))))))))))
     {:type        :multi-file-tailer
      ::channel    out-ch
      ::control-ch control-ch})))

(defmethod channel :multi-file-tailer
  [{:keys [::channel ::control-ch]}]
  channel)

(defmethod close! :multi-file-tailer
  [{:keys [::channel ::control-ch]}]
  (a/>!! control-ch :stop)
  (a/close! channel))
