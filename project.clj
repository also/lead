(defproject com.ryanberdeen/lead "0.1.0-SNAPSHOT"
  :description "Maybe an alternative to Graphite"
  :url "https://github.com/also/lead"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [
    [org.clojure/clojure "1.5.1"]
    [org.clojure/clojurescript "0.0-2030"]
    [org.clojure/math.numeric-tower "0.0.2"]
    [org.clojure/data.json "0.2.2"]
    [instaparse "1.2.8"]
    [clj-http "0.7.2" :exclusions [org.clojure/tools.reader]] ; tools.reader version conflicted with clojurescript requirement
    [ring/ring-core "1.1.8"]
    [ring/ring-jetty-adapter "1.1.8"]
    [ring/ring-json "0.2.0"]
    [compojure "1.1.5"]
    [org.clojure/tools.logging "0.2.6"]]
  :plugins  [[com.keminglabs/cljx "0.3.1"]
             [lein-cljsbuild "1.0.0"]]
  :cljx  {:builds  [{:source-paths  ["src/cljx"]
                     :output-path "target/classes"
                     :rules :clj}
                    {:source-paths  ["src/cljx"]
                     :output-path "target/generated/cljs"
                     :rules :cljs}]}
  :hooks  [cljx.hooks]
  :cljsbuild {
    :builds  [{:source-paths ["target/generated/cljs" "target/classes"]
               :compiler  {:optimizations :none
                           :pretty-print true
                           :output-dir "target/js"
                           :output-to "target/js/index.js"
                           }}]}
  ;; need this to work with leiningen 2.3.1 used on travis-ci
  ;; cljx should probably support %s or another way to reference project configuration
  :profiles {:test {:target-path "target"}})
