(ns lead.parser
  (:refer-clojure :exclude [read-string])
  (:require [instaparse.core :as insta]
            [clojure.edn :refer [read-string]]
            [instaparse.failure :as fail]))

(def transforms
  {:string str
   :metric (fn [& args] ["load" [(apply str args)]])
   :func str
   :boolean #(= % [:true])
   :args (fn [& args] (vec args))
   :call (fn [name args] [name args])
   :number (comp read-string str)
   :target identity})

(defmacro ^:no-doc load-parser []
  `(quote ~(-> "graphite_grammar" clojure.java.io/resource slurp insta/parser :grammar)))

(def parser (insta/parser (load-parser) :start :target))

(defn parse
  "Parses and transforms a string into a program that can be built.

  ```
  f(1) -> [\"f\" [1]]
  x    -> [\"load\" [\"x\"]]
  ```
  "
  [s]
  (let [result (parser s)]
    (if (insta/failure? result)
      (throw (ex-info "Failed to parse" {:message (with-out-str  (fail/pprint-failure result))
                                         :index (:index result)
                                         :column (:column result)
                                         :line (:line result)}))
      (insta/transform transforms result))))
