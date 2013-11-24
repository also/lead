(ns lead.functions
  #+cljs (:require [clojure.string :as str]))

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


(defmacro leadfn
  [& args]
  `(defn ~@args))

(defprotocol SeriesSource
  (load-serieses [this, opts]))

(defn series-source? [x]
  (satisfies? SeriesSource x))

(defn load-args [opts args]
  (map (fn [arg] (if (series-source? arg)
                   (load-serieses arg opts)
                   arg))
       args))

(defn call-f
  [name f & args]
  (try
    (apply apply f args)
    (catch #+clj Throwable #+cljs js/Error t
      (throw (ex-info (str "Error calling " name ": " (.getMessage t)) {:name name :args args} t)))))

(defrecord ComplicatedFunctionCall [name f args]
  SeriesSource
  (load-serieses [this opts]
    (call-f name f opts args)))

(defrecord SimpleFunctionCall [name, f, args]
  SeriesSource
  (load-serieses [this, opts]
    (let [loaded-args (load-args opts (:args this))]
      (call-f name f args))))

(declare function-call)

(def ^:dynamic *fn-registry*)

(defn create-registry [] (atom {}))

#+cljs
(defn f-meta [f] (aget f "meta"))

#+clj
(def f-meta meta)

(defn fn-names [f] (cons (str (:name (f-meta f))) (:aliases (f-meta f))))

(defn register-fns
  "Registers a list of functions by it's aliases."
  [fns]
  (if (seq fns) (swap! *fn-registry* (partial apply assoc) (flatten
    (map (fn [f] (map (fn [n] [n f]) (fn-names f))) fns)))))

#+clj
(defn enumerate-namespace
  [namespace]
  (require namespace)
  (vals (ns-publics namespace)))

#+cljs
(defn enumerate-namespace
  [namespace]
  ; TODO maybe replace other chars?
  (let [goog-ns (str/replace (str namespace) \- \_)]
    (goog/require goog-ns)
    (-> goog-ns goog/getObjectByName js->clj vals)))

(defn find-fns
  "Find lead functions in a namespace."
  [namespace]
  (filter #(:args (f-meta %)) (enumerate-namespace namespace)))

(def register-fns-from-namespace (comp register-fns find-fns))

(defn get-fn [name] (@*fn-registry* name))

(defn function->source [name f args]
  (if (:complicated (f-meta f))
    (ComplicatedFunctionCall. name f args)
    (SimpleFunctionCall. name f args)))

(defn function-call [name args]
  (if-let [f (get-fn name)]
    (function->source name f args)
    (throw (ex-info (str name " is not a function") {:name name}))))

(defn call-function [function opts args]
  (load-serieses opts (function-call function args)))

(defn call-simple-function [function loaded-args]
  (if-let [f (get-fn function)]
    (if (:complicated (f-meta f))
      (throw (ex-info (str function " can't be used in this context") {:name function}))
      (call-f function f loaded-args))
    (throw (ex-info (str function " is not a function") {:name function}))))

(defn build [program]
  (if (vector? program)
    (let [[name args] program]
      (function-call name (map build args)))
    program))

(defn run [program opts] (load-serieses (build program) opts))
