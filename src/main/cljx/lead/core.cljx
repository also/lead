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
