(defproject com.ryanberdeen/lead "0.1.0-SNAPSHOT"
  :description "Maybe an alternative to Graphite"
  :url "https://github.com/also/lead"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [
    [org.clojure/clojure "1.5.1"]
    [org.clojure/math.numeric-tower "0.0.2"]
    [org.clojure/data.json "0.2.2"]
    [the/parsatron "0.0.3"]
    [clj-http "0.7.2"]
    [ring/ring-core "1.1.8"]
    [ring/ring-jetty-adapter "1.1.8"]
    [ring/ring-json "0.2.0"]
    [compojure "1.1.5"]
    [org.clojure/tools.logging "0.2.6"]]
:aliases {"jetty" ["run" "-m" "lead.jetty-api/run"]})
