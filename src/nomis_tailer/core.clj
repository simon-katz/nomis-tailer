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
     (a/>!! c line))
    (^void handle [this ^Exception e]
     (throw e))))

(defn make-tailer-and-channel [file delay-ms]
  (let [ch     (a/chan)
        tailer (Tailer/create file
                              (tailer-listener ch)
                              delay-ms
                              true)]
    {::channel ch
     ::tailer  tailer}))

(defn channel [t-and-c]
  (::channel t-and-c))

(defn stop-tailer-and-channel! [{:keys [::channel ::tailer]}]
  (.stop tailer)
  (a/close! channel))
