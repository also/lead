(ns lead.functions
  #+cljs (:require [clojure.string :as str]))

; A simple function just transforms it's input--it wil be called after call is called on all of it's arguments.
; A complicated function is responsible calling call on it's arguments, so it is able to use or change the options.

#+cljs-macro
(defmacro leadfn
  [name & body]
  `(do (def ~name (fn ~@body))
       (aset ~name "meta" ~(assoc (meta name) :name (str name)))))

#+clj-macro
(defmacro leadfn
  [& args]
  `(defn ~@args))

(defprotocol LeadCallable
  (call [this opts]))

(defrecord ValueCallable [value]
  LeadCallable
  (call [this opts] value))

(defn lead-callable? [x]
  (satisfies? LeadCallable x))

(defn call-args [opts args]
  (vec (map (fn [arg]
          (if (lead-callable? arg)
            (call arg opts)
            arg))
        args)))

(defn call-f
  [name f & args]
  (try
    (apply apply f args)
    (catch #+clj Throwable #+cljs js/Error t
      (throw (ex-info "Error in Lead function"
                      {:function-name name
                       :args args}
                      t)))))

(defrecord ComplicatedFunctionCall [name f args]
  LeadCallable
  (call [this opts]
    (call-f name f opts args)))

(defrecord SimpleFunctionCall [name f args]
  LeadCallable
  (call [this opts]
    (let [called-args (call-args opts args)]
      (call-f name f called-args))))

(defrecord SimpleWithOptsFunctionCall [name f args]
  LeadCallable
  (call [this opts]
    (let [called-args (call-args opts args)]
      (call-f name f opts called-args))))

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
    (->ComplicatedFunctionCall name f args)
    (if (:uses-opts (f-meta f))
      (->SimpleWithOptsFunctionCall name f args)
      (->SimpleFunctionCall name f args))))

(defn function-call [name args]
  (if-let [f (get-fn name)]
    (function->source name f args)
    (throw (ex-info (str name " is not a function") {:name name}))))

(defn call-function [function opts args]
  (call opts (function-call function args)))

(defn uses-opts?
  [f]
  (let [meta (f-meta f)]
    (or (:uses-opts meta)
        (:complicated meta))))

; TODO this really means no opts
(defn call-simple-function [function loaded-args]
  (if-let [f (get-fn function)]
    (if (uses-opts? f)
      (throw (ex-info (str function " can't be used in this context") {:name function}))
      (call-f function f loaded-args))
    (throw (ex-info (str function " is not a function") {:name function}))))

(defn build [program]
  (if (vector? program)
    (let [[name args] program]
      (function-call name (map build args)))
    program))

(defn run [program opts]
  (call (build program) opts))
