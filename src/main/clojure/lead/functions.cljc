(ns lead.functions
  "Registering and calling functions.

  A \"Lead function\" is simply a Clojure function. It can take several forms:

  * A simple function just transforms its input--it wil be called after `call` is called on all of its arguments.
  * A complicated function is responsible for calling `call` on its arguments, so it is able to use or change the options."
  (:require [clojure.string :as string]
            [schema.core :as s]
            [lead.core :refer [*configuration*]])
  #?@(:clj [(:require [schema.macros :as sm])
            (:import (clojure.lang ExceptionInfo))]
      :cljs [(:require-macros
               [schema.macros :as sm]
               lead.functions)]))

(defn- leadfn-meta [m]
  (assoc m
    :leadfn true
    :doc (if (:aliases m)
           (str "Lead function names: "
                (string/join ", " (:aliases m))
                (if (:doc m) (str "\n\n" (:doc m)) ""))
           (:doc m))))

#?(:clj (defmacro leadfn
   "Defines a Lead function.

   Several metadata keys can be added:

   * `:uses-opts`: Takes `opts` as the first argument.
   * `:complicated`: Takes `opts` as the first argument and the rest of the arguments without being called.
   * `:aliases`: A vector of names used to register the function."
   [name & args]
   `(sm/if-cljs
      (do
        (sm/defn ~name ~@args)
        (aset ~name "meta" ~(assoc (meta name) :name (str name) :leadfn true)))
      (sm/defn ~(vary-meta name leadfn-meta) ~@args))))

#?(:cljs (defn f-meta [f] (aget f "meta")))

#?(:clj (def f-meta meta))

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
  "A Lead function bound to its arguments that can be called."
  (call [this opts]))

(defrecord ValueCallable [value]
  LeadCallable
  (call [this opts] value))

(alter-meta! #'->ValueCallable assoc :no-doc true)
(alter-meta! #'map->ValueCallable assoc :no-doc true)

(defn lead-callable?
  "Returns true if x implements [[LeadCallable]]."
  [x]
  (satisfies? LeadCallable x))

(defn call-args
  "Evaluates the arguments to a Lead function. [[LeadCallable]]s are called; everything else is left as-is."
  [opts args]
  (vec (map (fn [arg]
          (if (lead-callable? arg)
            (call arg opts)
            arg))
        args)))

(defn call-f
  "Calls a Lead function, validating its arguments and wrapping any exceptions."
  [name f & args]

  (let [args (apply apply vector args)]
    (if (:validate-arguments *configuration*)
      (try
        (if-let [input-schema (-> f f-meta :schema :input-schemas first)]
          (s/validate input-schema args))
        (catch #?(:clj Throwable :cljs js/Error) t
                                               (throw (ex-info "Invalid arguments to Lead function"
                                                               {:function-name name
                                                                :args          args
                                                                :error         (-> t ex-data :error pr-str)})))))
    (try
      (apply f args)
      #?(:clj (catch ExceptionInfo i (if (= :function-internal-error (:lead-exception-type i))
                                (throw i)
                                (throw (ex-info "Error in Lead function"
                                                {:lead-exception-type :function-internal-error
                                                 :message             (or (-> i ex-data :message) (.getMessage i))
                                                 :function-name       name
                                                 :args                args}
                                                i)))))
      (catch #?(:clj Throwable :cljs js/Error) t
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

(alter-meta! #'->ComplicatedFunctionCall assoc :no-doc true)
(alter-meta! #'map->ComplicatedFunctionCall assoc :no-doc true)

(defrecord SimpleFunctionCall [name f args]
  LeadCallable
  (call [this opts]
    (let [called-args (call-args opts args)]
      (call-f name f called-args))))

(alter-meta! #'->SimpleFunctionCall assoc :no-doc true)
(alter-meta! #'map->SimpleFunctionCall assoc :no-doc true)

(defrecord SimpleWithOptsFunctionCall [name f args]
  LeadCallable
  (call [this opts]
    (let [called-args (call-args opts args)]
      (call-f name f opts called-args))))

(alter-meta! #'->SimpleWithOptsFunctionCall assoc :no-doc true)
(alter-meta! #'map->SimpleWithOptsFunctionCall assoc :no-doc true)

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

(defn function-info
  "Returns information about all registered functions."
  []
  (into {} (map (fn [[k v]]
                  [k (let [meta (f-meta v)] (select-keys (assoc meta :schema (simplify-function-schema v))
                                   [:schema :aliases :name :file :ns :arglists :line]))])
                *fn-registry*)))

(defn- fn-names
  "Return all the names of the function."
  [f opts]
  (let [meta (f-meta f)]
    (mapcat (fn [name]
              (let [full-name (str (or (:namespace opts) (:ns meta)) \. name)]
                (if (:import opts)
                  [full-name name]
                  [full-name])))
            (cons (str (:name meta)) (:aliases meta)))))

(defn register-fns
  "Registers a list of functions by it's aliases."
  [fns opts]
  (if (seq fns)
    (swap! *fn-registry-builder*
           (partial apply assoc)
           (flatten (map (fn [f] (map (fn [n] [n f]) (fn-names f opts))) fns)))))

(defn- enumerate-namespace
  [namespace]
  #?@(:clj [(require namespace)
            (vals (ns-publics namespace))]
      :cljs [; TODO maybe replace other chars?
            (let [goog-ns (string/replace (str namespace) \- \_)]
              (goog/require goog-ns)
              (-> goog-ns goog/getObjectByName js->clj vals))]))

(defn find-fns
  "Find all Lead function vars in a namespace."
  [namespace]
  (filter #(:leadfn (f-meta %)) (enumerate-namespace namespace)))

(defn register-fns-from-namespace
  "Registers all Lead functions in a namespace."
  ([ns] (register-fns-from-namespace ns {}))
  ([ns opts] (register-fns (find-fns ns) (merge {:import true} opts))))

(defn get-fn [name] (*fn-registry* name))

(defn- function->source
  "Binds a function var `f` to arguments `args` in a [[LeadCallable]]."
  [name f args]
  (if (:complicated (f-meta f))
    (->ComplicatedFunctionCall name f args)
    (if (:uses-opts (f-meta f))
      (->SimpleWithOptsFunctionCall name f args)
      (->SimpleFunctionCall name f args))))

(defn function-call
  "Looks up a function by `name` and binds it to arguments `args` in a [[LeadCallable]].

  Throws an exception if no function with `name` exists."
  [name args]
  (if-let [f (get-fn name)]
    (function->source name f args)
    (throw (ex-info (str name " is not a function") {:lead-exception-type :illegal-argument :name name}))))

(defn ^:deprecated ^:no-doc call-function [name opts args]
  (call opts (function-call name args)))

; TODO this really means no opts
(defn call-simple-function [function loaded-args]
  (if-let [f (get-fn function)]
    (if (uses-opts? f)
      (throw (ex-info (str function " can't be used in this context") {:lead-exception-type :illegal-argument :name function}))
      (call-f function f loaded-args))
    (throw (ex-info (str function " is not a function") {:lead-exception-type :illegal-argument :name function}))))

(defn build
  "Builds a parsed program into a LeadCallable."
  [program]
  (if (vector? program)
    (let [[name args] program]
      (function-call name (map build args)))
    program))

(defn run
  "Builds and runs a parsed program."
  [program opts]
  (call (build program) opts))
