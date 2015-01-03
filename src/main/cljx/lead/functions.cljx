(ns lead.functions
  #+cljs (:require [clojure.string :as string]
                   [schema.core :as s]
                   [lead.core :refer [*configuration*]])
  #+cljs (:require-macros [schema.macros :as sm])
  #+clj
  (:require [schema.core :as s]
            [schema.macros :as sm]
            [lead.series :as series]
            [lead.core :refer [*configuration*]])
  #+clj
  (:import (clojure.lang ExceptionInfo)))

; A simple function just transforms its input--it wil be called after call is called on all of its arguments.
; A complicated function is responsible for calling call on its arguments, so it is able to use or change the options.

(defmacro leadfn
  [name & args]
  `(sm/if-cljs
     (do
       (sm/defn ~name ~@args)
       (aset ~name "meta" ~(assoc (meta name) :name (str name) :leadfn true)))
     (sm/defn ~(vary-meta name assoc :leadfn true) ~@args)))

#+cljs
(defn f-meta [f] (aget f "meta"))

#+clj
(def f-meta meta)

(defn uses-opts?
  [f]
  (let [meta (f-meta f)]
    (or (:uses-opts meta)
        (:complicated meta))))

(sm/defschema
  Opts
  {:start s/Int
   :end s/Int
   :params {s/Any s/Any}
   s/Keyword s/Any})

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

  (let [args (apply apply vector args)]
    (if (:validate-arguments *configuration*)
      (try
        (if-let [input-schema (-> f f-meta :schema :input-schemas first)]
          (s/validate input-schema args))
        (catch #+clj Throwable #+cljs js/Error t
                                               (throw (ex-info "Invalid arguments to Lead function"
                                                               {:function-name name
                                                                :args          args
                                                                :error         (-> t ex-data :error pr-str)})))))
    (try
      (apply f args)
      #+clj
      (catch ExceptionInfo i (if (= :function-internal-error (:lead-exception-type i))
                               (throw i)
                               (throw (ex-info "Error in Lead function"
                                               {:lead-exception-type :function-internal-error
                                                :message (or (-> i ex-data :message) (.getMessage i))
                                                :function-name name
                                                :args          args}
                                               i))))
      (catch #+clj Throwable #+cljs js/Error t
                                             (throw (ex-info "Error in Lead function"
                                                             {:lead-exception-type :function-internal-error
                                                              :message (.getMessage t)
                                                              :function-name name
                                                              :args          args}
                                                             t))))))

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

(def ^:dynamic *fn-registry* {})
(def ^:dynamic *fn-registry-builder*)

(defn create-registry [] (atom {}))

(defn simplify-schema [schema]
  (if-let [name (s/schema-name schema)]
    name
    (if (vector? schema)
      (vec (map simplify-schema schema))
      (s/explain schema))))

(defn simplify-function-schema [f]
  (if-let [schema (:schema (f-meta f))]
    {:explain (-> schema s/explain pr-str)
      :input  (let [[input] (:input-schemas schema)
                   [first [last]] (split-with #(instance? schema.core.One %) input)
                   first (if (uses-opts? f) (rest first) first)]
               {:first (vec (map (comp simplify-schema :schema) first))
                :last  (if last (simplify-schema last))})
     :output (if-let [output (:output-schema schema)] (simplify-schema output))}))

(defn function-info []
  (into {} (map (fn [[k v]]
                  [k (let [meta (f-meta v)] (select-keys (assoc meta :schema (simplify-function-schema v))
                                   [:schema :aliases :name :file :ns :arglists :line]))])
                *fn-registry*)))

(defn fn-names [f] (cons (str (:name (f-meta f))) (:aliases (f-meta f))))

(defn register-fns
  "Registers a list of functions by it's aliases."
  [fns]
  (if (seq fns) (swap! *fn-registry-builder* (partial apply assoc) (flatten
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
  (let [goog-ns (string/replace (str namespace) \- \_)]
    (goog/require goog-ns)
    (-> goog-ns goog/getObjectByName js->clj vals)))

(defn find-fns
  "Find lead functions in a namespace."
  [namespace]
  (filter #(:leadfn (f-meta %)) (enumerate-namespace namespace)))

(def register-fns-from-namespace (comp register-fns find-fns))

(defn get-fn [name] (*fn-registry* name))

(defn function->source [name f args]
  (if (:complicated (f-meta f))
    (->ComplicatedFunctionCall name f args)
    (if (:uses-opts (f-meta f))
      (->SimpleWithOptsFunctionCall name f args)
      (->SimpleFunctionCall name f args))))

(defn function-call [name args]
  (if-let [f (get-fn name)]
    (function->source name f args)
    (throw (ex-info (str name " is not a function") {:lead-exception-type :illegal-argument :name name}))))

(defn call-function [function opts args]
  (call opts (function-call function args)))

; TODO this really means no opts
(defn call-simple-function [function loaded-args]
  (if-let [f (get-fn function)]
    (if (uses-opts? f)
      (throw (ex-info (str function " can't be used in this context") {:lead-exception-type :illegal-argument :name function}))
      (call-f function f loaded-args))
    (throw (ex-info (str function " is not a function") {:lead-exception-type :illegal-argument :name function}))))

(defn build [program]
  (if (vector? program)
    (let [[name args] program]
      (function-call name (map build args)))
    program))

(defn run [program opts]
  (call (build program) opts))
