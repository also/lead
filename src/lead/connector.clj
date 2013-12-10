(ns lead.connector)

(def ^:dynamic *connector*)

(defn init-connector [] (atom nil))

(defprotocol Connector
  (query [this pattern])
  (load-serieses [this targets opts]))

(defn set-connector
  [connector]
  (reset! *connector* connector))

(defrecord ConnectorList [connectors]
  Connector
  (query [this pattern]
    (set (flatten (pmap #(query % pattern) (:connectors this)))))

  (load-serieses [this targets opts]
    (flatten (pmap #(load-serieses % targets opts) (:connectors this)))))

(defn connector-list
  [& connectors]
  (->ConnectorList (flatten connectors)))
