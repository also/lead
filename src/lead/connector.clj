(ns lead.connector)

(def ^:dynamic *connector*)

(defn init-connector [] (atom nil))

(defn set-connector
  [connector]
  (reset! *connector* connector))

(defn get-metrics
  [& args]
  (apply @*connector* args))
