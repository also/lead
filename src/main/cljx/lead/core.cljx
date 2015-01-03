(ns lead.core)

(def ^:dynamic *configuration*)

(def ^:dynamic *context*)

(defn create-context [] {:exceptions []})

(defn exception
  [ex]
  (swap! *context* (fn [context]
                     (update-in context [:exceptions] #(cons ex %)))))
