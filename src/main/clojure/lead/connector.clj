(ns lead.connector
  (:require [lead.functions :as functions]
            [lead.matcher :as matcher]
            [lead.series :as series]
            [lead.time :refer [DateTime->seconds Duration->seconds seconds->DateTime]])
  (:import [lead LoadOptions Series TreeNode]))

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
    (distinct (flatten (pmap #(query % pattern) connectors))))

  (load-serieses [this targets opts]
    (flatten (pmap (fn [connector]
                     (try
                       (load-serieses connector targets opts)
                       (catch Exception ex
                         (functions/exception ex)
                         [])))
                   connectors))))

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
                 (query connector (series/path->name unprefixed-pattern)))
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
      (if (seq targets)
        (map #(update-in % [:name] add-prefix path-prefix)
             (load-serieses connector targets opts))
        ()))))

(defn prefixed-connector
  [prefix connector]
  (->PrefixedConnector (series/name->path prefix) connector))

(defn TreeNode->map
  [tree-node]
  {:name    (.getName tree-node)
   :is-leaf (.isLeaf tree-node)})

(defn map->LoadOptions
  [opts]
  (reify LoadOptions
    (getStart [this] (seconds->DateTime (:start opts)))
    (getEnd [this] (seconds->DateTime (:end opts)))))

(defn Series->FixedIntervalTimeSeries
  [series]
  (series/map->FixedIntervalTimeSeries {:name   (.getName series)
                                        :start  (DateTime->seconds (.getStart series))
                                        :end    (DateTime->seconds (.getEnd series))
                                        :step   (Duration->seconds (.getStep series))
                                        :values (.getValues series)}))

(extend lead.Connector
  Connector
  {:query         (fn [connector pattern]
                    (map TreeNode->map (.query connector pattern)))
   :load-serieses (fn [connector targets opts]
                    (map Series->FixedIntervalTimeSeries
                         (.load connector targets (map->LoadOptions opts))))})
