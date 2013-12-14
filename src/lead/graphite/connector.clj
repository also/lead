(ns lead.graphite.connector
  (:import (com.google.common.hash Hashing)
           (com.google.common.base Charsets)
           (java.util Collections))
  (:require [clj-http.client :as http]
            [lead.connector :refer [Connector]]))

(defn graphite-json->serieses [targets]
    (map (fn [target]
           (assoc (let [timestamps (map second (:datapoints target))]
                    (if (seq timestamps)
                      (assoc {:start (first timestamps) :end (last timestamps)}
                              :step (if (> (count timestamps) 1)
                                     (- (second timestamps) (first timestamps))
                                     1))))
                  :name (:target target)
                  :values (map first (:datapoints target))))
         targets))

(defrecord GraphiteConnector [url]
  Connector
  (query [this pattern]
    (let [url (str (:url this) "/metrics/find")
          response (http/get url {:as :json
                                  :query-params {"query" pattern
                                                 "format" "completer"}})]

      ; TODO "completer" is the closest format to what we need, but it tacks on
      ; "*" to the end of every query: https://github.com/graphite-project/graphite-web/blob/0.9.12/webapp/graphite/metrics/views.py#L157-L158
      ; we should filter the results on the last path segment
      (map (fn [result]
             {:name (let [name (:path result)]
                      (if (.endsWith name ".")
                        (.substring name 0 (- (.length name) 1))
                        name))
              :is-leaf (= "1" (:is_leaf result))})
           (-> response :body :metrics))))

  (load-serieses [this targets {:keys [start end]}]
    (let [url (str (:url this) "/render/")
          response (http/get url {:as :json
                                  :query-params {"target" targets
                                                 "from" start
                                                 "until" end
                                                 "format" "json"}})
          targets (:body response)]
      (graphite-json->serieses targets))))

(defn connector [url] (->GraphiteConnector url))

(def md5 (Hashing/md5))

(defn- compute-ring-position [key]
  (Integer/parseInt (.substring (str (.hashString md5 key Charsets/UTF_8)) 0 4) 16))

(defn- create-builder
  [replicas] {:replicas replicas, :entries ()})

(defn- node-string [[host instance]]
  (format "('%s', '%s')" host instance))

; node is [host instance]
(defn- add-node [builder node]
  (assoc builder :entries
                 (reduce (fn [entries i]
                           (let [key (str (node-string node) \: i)
                                 position (compute-ring-position key)]
                             (conj entries [position node])))
                         (:entries builder)
                         (range (:replicas builder)))))

(defn- build [builder]
  (-> builder :entries sort vec))

(defn create-ring
  ([nodes] (create-ring 100 nodes))
  ([replicas nodes]
   (build (reduce add-node (create-builder replicas) nodes))))

(defn get-node
  [key entries]
  (let [position (compute-ring-position key)
        entry [position nil]
        result-position (mod (- (- (Collections/binarySearch entries entry)) 1) (count entries))]
    (second (nth entries result-position))))
