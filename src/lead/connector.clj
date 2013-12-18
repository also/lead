(ns lead.connector
  (:require [lead.functions :as functions]
            [lead.matcher :as matcher]
            [lead.series :as series]))

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

(defn- matches-prefix
  [pattern-path path-prefix]
  (every? identity (map matcher/segment-matches path-prefix pattern-path)))

(defn- drop-prefix
  [path path-prefix]
  (drop (count path-prefix) path))

(defn- add-prefix
  [path path-prefix]
  (str (series/path->name path-prefix) \. path))

(defrecord PrefixedConnector [path-prefix connector]
  Connector
  (query [this pattern]
    (let [pattern-path (series/name->path pattern)]
      (if (matches-prefix pattern-path path-prefix)
        (let [unprefixed-pattern (drop-prefix pattern-path path-prefix)]
          (if (seq unprefixed-pattern)
            (map #(update-in % [:name] add-prefix path-prefix)
                 (query (:connector this) (series/path->name unprefixed-pattern)))
            [{:name (series/path->name path-prefix)
              :is-leaf false}]))
        ())))

  (load-serieses [this prefixed-targets opts]
    (let [targets (keep identity
                        (map (fn [target]
                               (let [target-path (series/name->path target)]
                                 (if (matches-prefix target-path path-prefix)
                                   (series/path->name (drop-prefix target-path path-prefix)))))
                             prefixed-targets))]
      (map #(update-in % [:name] add-prefix path-prefix)
           (load-serieses (:connector this) targets opts)))))

(defn prefixed-connector
  [prefix connector]
  (->PrefixedConnector (series/name->path prefix) connector))