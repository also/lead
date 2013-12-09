(ns lead.connector)

(def ^:dynamic *connector*)

(defn init-connector [] (atom nil))

(defprotocol Connector
  (query [this pattern])
  (load-serieses [this targets opts]))

(defn set-connector
  [connector]
  (reset! *connector* connector))