(ns lead.graphite.connector
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
