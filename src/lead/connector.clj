(ns lead.connector
  (:require [lead.functions :as functions]))

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
    (distinct (flatten (pmap #(query % pattern) (:connectors this)))))

  (load-serieses [this targets opts]
    (flatten (pmap (fn [connector]
                     (try
                       (load-serieses connector targets opts)
                       (catch Exception ex
                         (functions/exception ex)
                         [])))
                   (:connectors this)))))

(defn connector-list
  [& connectors]
  (->ConnectorList (flatten connectors)))
