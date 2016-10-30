(defproject com.nomistech/nomis-tailer "0.1.0"
  :description "A Clojure implementation of tail -F and more"
  :url "https://github.com/simon-katz/nomis-tailer"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.395"]
                 [commons-io/commons-io "2.5"]]
  :repl-options {:init-ns user}
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                                  [midje "1.8.3"]]
                   :source-paths ["dev"]
                   :plugins [[lein-midje "3.2.1"]]}})
