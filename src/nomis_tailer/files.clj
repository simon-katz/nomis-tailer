(ns nomis-tailer.files)

(defn ^:private files-in-dir-matching-pattern
  "A sequence of files in `dir` matching `pattern`."
  [dir pattern]
  (->> dir
       .listFiles
       (filter (fn [x] (.isFile x)))
       (filter #(re-matches pattern (.getName %)))))

(defn most-recent-file-matching-pattern
  "The most recent file in `dir` matching `pattern`, or nil if no such file."
  [dir pattern]
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
