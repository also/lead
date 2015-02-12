(ns lead.connector
  (:require [lead.matcher :as matcher]
            [lead.time :refer [DateTime->seconds Duration->seconds seconds->DateTime]]
            [lead.core :as core]
            [lead.series :as series]
            [clj-http.client :as http])
  (:refer-clojure :exclude [load])
  (:import [lead LoadOptions]))

(def ^:dynamic *connector*)

(defprotocol Connector
  (query [this pattern])
  (load [this target opts]))

(defn ^:no-doc map-connectors
  [f connectors]
  (flatten (pmap (fn [connector]
                   (try
                     (f connector)
                     (catch Exception ex
                       (core/exception ex)
                       [])))
                 connectors)))

(defrecord ^:no-doc ConnectorList [connectors]
  Connector
  (query [this pattern]
    (distinct (map-connectors #(query % pattern) connectors)))

  (load [this target opts]
    (map-connectors #(load % target opts) connectors)))

(alter-meta! #'->ConnectorList assoc :no-doc true)
(alter-meta! #'map->ConnectorList assoc :no-doc true)

(defn connector-list
  "Create a connector that calls a list of connectors in parallel."
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
  (str (core/path->name path-prefix) \. path))

(defrecord ^:no-doc PrefixedConnector [path-prefix connector]
  Connector
  (query [this pattern]
    (let [pattern-path (core/name->path pattern)]
      (if (matches-prefix pattern-path path-prefix)
        (let [unprefixed-pattern (drop-prefix pattern-path path-prefix)]
          (if (seq unprefixed-pattern)
            (map #(update-in % [:name] add-prefix path-prefix)
                 (query connector (core/path->name unprefixed-pattern)))
            [{:name (core/path->name path-prefix)
              :is-leaf false}]))
        ())))

  (load [this prefixed-target opts]
    (let [target-path (core/name->path prefixed-target)
          target (if (matches-prefix target-path path-prefix)
                   (core/path->name (drop-prefix target-path path-prefix)))]
      (if target
        (map #(update-in % [:name] add-prefix path-prefix)
             (load connector target opts))
        ()))))

(alter-meta! #'->PrefixedConnector assoc :no-doc true)
(alter-meta! #'map->PrefixedConnector assoc :no-doc true)

(defn prefixed-connector
  "Wrap a connector to add a prefix to all names."
  [prefix connector]
  (->PrefixedConnector (core/name->path prefix) connector))

(defrecord ^:no-doc FilteringConnector [filter connector]
  Connector
  (query [this pattern]
    (if (filter pattern)
      (query connector pattern)
      ()))
  (load [this target opts]
    (if (filter target)
      (load connector target opts)
      ())))

(alter-meta! #'->FilteringConnector assoc :no-doc true)
(alter-meta! #'map->FilteringConnector assoc :no-doc true)

(def filtering-connector ->FilteringConnector)

(defrecord ^:no-doc LeadConnector [url opts query-opts load-opts]
  Connector
  (query [this pattern]
    (let [url (str (:url this) "/find")
          response (http/get url (merge
                                   opts
                                   query-opts
                                   {:as           :json
                                    :query-params {"query" pattern}}))]
      (:body response)))

  (load [this target {:keys [start end]}]
    (let [url (str (:url this) "/render")
          response (http/get url (merge
                                   opts
                                   load-opts
                                   {:as           :json
                                    :query-params {"target" target
                                                   "start"  start
                                                   "end"    end}}))]
      (:body response))))

(alter-meta! #'->LeadConnector assoc :no-doc true)
(alter-meta! #'map->LeadConnector assoc :no-doc true)


(defn remote
  "Create a connector that calls a remote Lead API."
  ([url {:keys [opts query-opts load-opts]}]
   (->LeadConnector url opts query-opts load-opts))
  ([url]
   (remote url {})))

(defn ^:no-doc TreeNode->map
  [tree-node]
  {:name    (.getName tree-node)
   :is-leaf (.isLeaf tree-node)})

(defn ^:no-doc map->LoadOptions
  [opts]
  (reify LoadOptions
    (getStart [this] (seconds->DateTime (:start opts)))
    (getEnd [this] (seconds->DateTime (:end opts)))))

(defn ^:no-doc Series->FixedIntervalTimeSeries
  [series]
  (series/map->FixedIntervalTimeSeries {:name   (.getName series)
                                        :start  (DateTime->seconds (.getStart series))
                                        :end    (DateTime->seconds (.getEnd series))
                                        :step   (Duration->seconds (.getStep series))
                                        :values (.getValues series)}))

(extend lead.Connector
  Connector
  {:query         (fn [connector pattern]
                    (map TreeNode->map (.find connector pattern)))
   :load (fn [connector target opts]
                    (map Series->FixedIntervalTimeSeries
                         (.load connector target (map->LoadOptions opts))))})
