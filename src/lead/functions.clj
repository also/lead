(ns lead.functions)

; A series has these attributes:
;  :values           a list of values
;  :start            the timestamp of the first value
;  :end              the timestamp of the last value
;  :step             the number of seconds per value
;
;  :consolidation-fn the function used to consolidate the values for aggregation with other series or display
;  :values-per-point the number of values for each consolidated point
;
; Options
;  :start
;  :end
;
; A simple function just transforms a series list--it wil be called with any series lists already loaded.
; A complicated function is responsible calling load-series on it's arguments, so it is able to use or change the options.

(defprotocol SeriesSource
  (load-serieses [this, opts]))

(defn series-source? [x]
  (satisfies? SeriesSource x))

(defn load-args [opts args]
  (map (fn [arg] (if (series-source? arg)
                   (load-serieses arg opts)
                   arg))
       args))

(defrecord ComplicatedFunctionCall [name f args]
  SeriesSource
  (load-serieses [this opts]
    (try
      (apply f opts args)
      (catch Throwable t
        (throw (RuntimeException. (str "Error calling " name ": " (.getMessage t)) t))))))

(defrecord SimpleFunctionCall [name, f, args]
  SeriesSource
  (load-serieses [this, opts]
    (let [loaded-args (load-args opts (:args this))]
      (try
        (apply f loaded-args)
        (catch Throwable t
          (throw (RuntimeException. (str "Error calling " name ": " (.getMessage t)) t)))))))

(declare function-call)

(def ^:dynamic *fn-registry*)

(defn create-registry [] (atom {}))

(defn fn-names [f] (cons (str (:name (meta f))) (:aliases (meta f))))

(defn register-fns
  "Registers a list of functions by it's aliases."
  [fns]
  (if (seq fns) (swap! *fn-registry* (partial apply assoc) (flatten
    (map (fn [f] (map (fn [n] [n f]) (fn-names f))) fns)))))

(defn find-fns
  "Find lead functions in a namespace."
  [namespace]
  (require namespace)
  (filter #(:args (meta %)) (vals (ns-publics namespace))))

(def register-fns-from-namespace (comp register-fns find-fns))

(defn get-fn [name] (@*fn-registry* name))

(defn function->source [name f args]
  (if (:complicated (meta f))
    (ComplicatedFunctionCall. name f args)
    (SimpleFunctionCall. name f args)))

(defn function-call [name args]
  (if-let [f (get-fn name)]
    (function->source name f args)
    (throw (RuntimeException. (str name " is not a function")))))

(defn call-function [function opts args]
  (load-serieses opts (function-call function args)))

(defn call-simple-function [function loaded-args]
  (if-let [f (get-fn function)]
    (if (:complicated (meta f))
      (throw (RuntimeException. (str function " can't be used in this context")))
      (try
        (apply f loaded-args)
        (catch Throwable t
          (throw (RuntimeException. (str "Error calling " name ": " (.getMessage t)) t)))))
    (throw (RuntimeException. (str function "is not a function")))))


(defn build [program] (binding [*ns* (the-ns 'lead.functions)] (eval program)))
(defn run [program opts] (load-serieses (build program) opts))
