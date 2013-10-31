(ns lead.functions
  [:require
   [clojure.java.io :as io]
   [clojure.data.json :as json]]
  [:use
   [lead.parser :only (parse)] ])

; A series has these attributes:
;  :values           a list of values
;  :start            the timestamp of the first value
;  :end              the timestamp of the last value
;  :step             the number of seconds per value
;
;  :consolidation-fn the function used to consolidate the values for aggregation with other series or display
;  :values-per-point the number of values for each consolidated point

(declare call-function)

(def fn-registry (atom {}))

(defn fn-names [f] (cons (str (:name (meta f))) (:aliases (meta f))))

(defn register-fns
  "Registers a list of functions by it's aliases."
  [fns]
  (if (seq fns) (swap! fn-registry (partial apply assoc) (flatten
    (map (fn [f] (map (fn [n] [n f]) (fn-names f))) fns)))))

(defn find-fns
  "Find lead functions in a namespace."
  [namespace]
  (require namespace)
  (filter #(:args (meta %)) (vals (ns-publics namespace))))

(def register-fns-from-namespace (comp register-fns find-fns))

(defn call-function [function args]
  (if-let [f (@fn-registry function)]
    (try
      (apply f args)
      (catch Throwable t
        (throw (RuntimeException. (str "Error calling " function ": " (.getMessage t)) t))))
    (throw (RuntimeException. (str function " is not a function")))))

(defn run [program] (binding [*ns* (the-ns 'lead.functions)] (eval program)))
