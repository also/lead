(defproject com.ryanberdeen/lead "0.1.0-SNAPSHOT"
  :description "Maybe an alternative to Graphite"
  :url "https://github.com/also/lead"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [
    [org.clojure/clojure "1.5.1"]
    [org.clojure/clojurescript "0.0-2080"]
    [org.clojure/math.numeric-tower "0.0.2"]
    [org.clojure/data.json "0.2.3"]
    [clj-http "0.7.2" :exclusions [org.clojure/tools.reader]] ; tools.reader version conflicted with clojurescript requirement
    ; TODO i have absolutely no idea why the excelusion below is ncessary. tools.reader
    ; isn't shown as a dependency of ring-core, but without it, the old version of tools.reader shows up on the classpath
    ; when using lein-cljsbuild. https://github.com/emezeske/lein-cljsbuild/issues/268 might be related
    [ring/ring-core "1.2.1" :exclusions [org.clojure/tools.reader]]
    [ring/ring-jetty-adapter "1.2.1"]
    [ring/ring-json "0.2.0"]
    [compojure "1.1.5"]
    [instaparse "1.2.8"]
    [org.clojure/tools.logging "0.2.6"]
    [com.cemerick/clojurescript.test "0.2.1"]
    [joda-time/joda-time "2.3"]
    [com.google.guava/guava "15.0"]]
  :main ^:skip-aot lead.main
  :plugins [[com.keminglabs/cljx "0.3.1"]
            [codox "0.6.6"]]
  :source-paths ["src" "src/main/clojure"]
  :java-source-paths ["src/main/java"]
  :test-paths ["test" "src/test/clojure"]
  :cljx {:builds [{:source-paths ["src/main/cljx"]
                   :output-path "target/classes"
                   :rules {:filetype "clj"
                           :features #{"clj-macro" "clj"}
                           :transforms []}}
                  {:source-paths ["src/main/cljx"]
                   :output-path "target/generated/cljs"
                   :rules :cljs}
                  {:source-paths ["src/main/cljx"]
                   :output-path "target/generated/clj"
                   :rules {:filetype "clj"
                           :features #{"cljs-macro" "clj"}
                           :transforms []}}
                  {:source-paths ["src/test/cljx"]
                   :output-path "target/test-classes"
                   :rules :clj}
                  {:source-paths ["src/test/cljx"]
                   :output-path "target/generated/test-cljs"
                   :rules :cljs}]}
  :hooks [cljx.hooks]
  ;; need this to work with leiningen 2.3.1 used on travis-ci
  ;; cljx should probably support %s or another way to reference project configuration
  :profiles {:test {:target-path "target"
                    :test-paths ["target/test-classes"]}
             :cljs {:cljsbuild {:builds [{:source-paths ["target/generated/cljs" "target/generated/clj"]
                                          :compiler {:optimizations :none
                                                     :pretty-print true
                                                     :output-dir "target/js"
                                                     :output-to "target/js/index.js" }}
                                         {:source-paths ["target/generated/cljs" "target/generated/clj" "target/generated/test-cljs" "target/test-classes"]
                                          :compiler {:optimizations :none
                                                     :pretty-print true
                                                     :output-dir "target/test-js"
                                                     :output-to "target/test-js/index.js"}}]
                                :test-commands {"node" ["./node_modules/coffee-script/bin/coffee" "test/coffee/run_clojure_tests.coffee"]}}
                    ; TODO this should replace instaparse dependency
                    :dependencies [[instaparse-cljs "1.2.2-SNAPSHOT"]]
                    :plugins [[lein-cljsbuild "1.0.0"]]}})
