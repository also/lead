(ns lead.core
  (:require [clojure.string :as string]))

(def ^:dynamic *configuration*)

(def ^:dynamic *context*)

(defn create-context [] {:exceptions []})

(defn exception
  "Adds `exception` to the list of exceptions in [[*context*]]"
  [ex]
  (swap! *context* (fn [context]
                     (update-in context [:exceptions] #(cons ex %)))))

(defn path->name [path]
  "Converts a path (vector of segments) to a name. The segment `:*` is converted to the string `*`"
  (str (string/join "." (map (fn [p] (if (= :* p) "*" p)) path))))

(defn name->path
  "Converts a name (or pattern) to a vector of segments."
  [name]
  (string/split name #"\." -1))

(defn apply-configuration [names]
  (let [names (if (string? names) [names] names)]
    (reduce (fn [configuration name]
              (let [configuration-fn (-> configuration :configurations ((keyword name)))]
                (if-not configuration-fn (throw (ex-info (str name " is not a valid configuration name") {:name name})))
                (if-let [result (configuration-fn configuration)]
                  result
                  ; TODO this is really an internal error-the configuration function returned falsey
                  (throw (ex-info (str name " is not a valid configuration name") {:name name})))))
            *configuration* names)))
