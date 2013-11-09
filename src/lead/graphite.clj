(ns lead.graphite
  (:require [clj-http.client :as http]))

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

(defn connector [url]
  (fn [target from until]
    (let [url (str url "/render/")
          response (http/get url {:as :json
                                  :query-params {"target" target
                                                 "from" from
                                                 "until" until
                                                 "format" "json"}})
          targets (:body response)]
      (graphite-json->serieses targets))))
