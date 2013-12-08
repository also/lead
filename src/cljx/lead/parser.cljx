(ns lead.parser
  (:refer-clojure :exclude [read-string])
  (:require [instaparse.core :as insta]
            [#+clj clojure.edn #+cljs cljs.reader :refer [read-string]]
            [instaparse.failure :as fail])
  #+cljs
  (:use-macros [lead.parser :only [load-parser]]))

(def transforms
  {:string str
   :metric (fn [& args] ["load" [(apply str args)]])
   :func str
   :boolean #(= % [:true])
   :args (fn [& args] (vec args))
   :call (fn [name args] [name args])
   :number (comp read-string str)
   :target identity})

; parsing the grammar is slow in clojurescript, so we do it during compilation
#+clj
(defmacro load-parser []
  `(quote ~(-> "graphite_grammar" clojure.java.io/resource slurp insta/parser :grammar)))

(def parser (insta/parser (load-parser) :start :target))

(defn parse [s]
  (let [result (parser s)]
    (if (insta/failure? result)
      (throw (ex-info "Failed to parse" result))
      (insta/transform transforms result))))
