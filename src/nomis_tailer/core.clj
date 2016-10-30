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

(defn ^:private make-tailer-and-channel-impl [file delay-ms from-end?]
  (let [ch        (a/chan)
        tailer    (Tailer/create file
                                 (tailer-listener ch)
                                 delay-ms
                                 from-end?)]
    {:type     :tailer-and-channel
     ::channel ch
     ::tailer  tailer}))

;;;; ___________________________________________________________________________
;;;; ---- Generic stuff ----

(defmulti channel
  "Returns this tailer-and-channel's or multi-tailer-and-channel's channel.
  Take from this to get lines from the file(s)."
  :type)

(defmulti close!
  "Stops/closes this tailer-and-channel's Tailer and channel.
  Also works for a multi-tailer-and-channel."
  :type)

;;;; ___________________________________________________________________________
;;;; ---- tailer-and-channel ----

(defn make-tailer-and-channel
  "Returns a so-called tailer-and-channel for `file` with the supplied
  `delay-ms`.
  The tailing starts from the end of the file.
  See org.apache.commons.io.input.Tailer for details of `delay-ms`. "
  [file delay-ms]
  (make-tailer-and-channel-impl file delay-ms true))

(defmethod channel :tailer-and-channel
  [{:keys [::channel ::tailer]}]
  channel)

(defmethod close! :tailer-and-channel
  [{:keys [::channel ::tailer]}]
  (.stop tailer)
  (a/close! channel))

;;;; ___________________________________________________________________________
;;;; ---- multi-tailer-and-channel ----

(defn make-multi-tailer-and-channel
  "Like `make-tailer-and-channel`, but looks for the most recent file in `dir`
  that matches `pattern`. Looks for new files every `file-change-delay-ms`.
  The tailing starts from the end of any current file, and includes the
  full content of subsequent files.
  Returns a so-called multi-tailer-and-channel."
  [dir pattern delay-ms file-change-delay-ms]
  (let [out-ch     (a/chan)
        control-ch (a/chan)]
    (letfn [(get-most-recent-file []
              (files/most-recent-file-matching-pattern dir pattern))
            (new-t-and-c [most-recent-file first?]
              (let [t-and-c
                    (make-tailer-and-channel-impl most-recent-file
                                                  delay-ms
                                                  first?)]
                (a/go-loop []
                  (let [v (a/<! (::channel t-and-c))]
                    (when v
                      (a/>! out-ch v)
                      (recur))))
                t-and-c))]
      (a/go
        (let [first-most-recent-file (get-most-recent-file)]
          (let [first-t-and-c (when first-most-recent-file
                                (new-t-and-c first-most-recent-file
                                             true))]
            (loop [prev-most-recent-file first-most-recent-file
                   prev-t-and-c          first-t-and-c]
              (let [[v ch] (a/alts! [control-ch
                                     (a/timeout file-change-delay-ms)]
                                    :priority true)]
                (if (= v :stop)
                  (when prev-t-and-c
                    (close! prev-t-and-c))
                  (let [most-recent-file     (get-most-recent-file)
                        new-file-to-process? (and most-recent-file
                                                  (not= most-recent-file
                                                        prev-most-recent-file))]
                    (if-not new-file-to-process?
                      ;; just leave things as they are
                      (recur prev-most-recent-file
                             prev-t-and-c)
                      (do (when prev-t-and-c
                            ;; Give time to finish tailing the previous file
                            ;; before closing the channel.
                            (a/<! (a/timeout file-change-delay-ms))
                            (close! prev-t-and-c))
                          (recur most-recent-file
                                 (new-t-and-c most-recent-file
                                              false))))))))))))
    {:type        :multi-tailer-and-channel
     ::channel    out-ch
     ::control-ch control-ch}))

(defmethod channel :multi-tailer-and-channel
  [{:keys [::channel ::control-ch]}]
  channel)

(defmethod close! :multi-tailer-and-channel
  [{:keys [::channel ::control-ch]}]
  (a/>!! control-ch :stop)
  (a/close! channel))
