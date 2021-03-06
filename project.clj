(defproject com.ryanberdeen/lead "0.1.1-SNAPSHOT"
  :description "Maybe an alternative to Graphite"
  :url "https://github.com/also/lead"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [
    [org.clojure/clojure "1.7.0"]
    [clj-http "1.1.1"]
    [ring/ring-core "1.2.1"]
    [ring/ring-jetty-adapter "1.2.1"]
    [ring/ring-json "0.2.0"]
    [compojure "1.1.5"]
    [instaparse "1.3.6"]
    [org.clojure/tools.logging "0.3.1"]
    [joda-time/joda-time "2.3"]
    [com.google.guava/guava "15.0"]
    [prismatic/schema "0.2.6"]
    [org.apache.commons/commons-math3 "3.2"]]
  :aot [lead.matcher]
  :plugins [[codox "0.8.10"]]
  :auto-clean false
  :source-paths ["src/main/clojure"]
  :java-source-paths ["src/main/java"]
  :test-paths ["src/test/clojure"]
  :javac-options ["-target" "1.6" "-source" "1.6"]
  :codox {:defaults {:doc/format :markdown}
          :sources ["src/main/clojure"]
          :src-dir-uri "https://github.com/also/lead/blob/master/"
          :src-linenum-anchor-prefix "L"})
