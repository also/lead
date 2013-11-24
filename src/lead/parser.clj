(ns lead.parser
  (:require [instaparse.core :as insta]
            [clojure.edn :as edn]
            [instaparse.failure :as fail]))

(def transforms
  {:string str
   :metric (fn [& args] ["load" [(apply str args)]])
   :func str
   :boolean #(= % [:true])
   :args (fn [& args] (vec args))
   :call (fn [name args] [name args])
   :number (comp edn/read-string str)
   :target identity})

(def parser (insta/parser (str (clojure.java.io/resource "graphite_grammar"))))

(defn parse [s]
  (let [result (parser s)]
    (if (insta/failure? result)
      (throw (RuntimeException. (with-out-str (fail/pprint-failure result))))
      (insta/transform transforms result))))
