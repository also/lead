(ns lead.core)

(def ^:dynamic *configuration*)

(def ^:dynamic *context*)

(defn create-context [] {:exceptions []})

(defn exception
  "Adds `exception` to the list of exceptions in [[*context*]]"
  [ex]
  (swap! *context* (fn [context]
                     (update-in context [:exceptions] #(cons ex %)))))
