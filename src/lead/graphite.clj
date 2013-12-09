(ns lead.graphite
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
  (query [this pattern] nil)
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