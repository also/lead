(ns lead.connector
  [:require [clj-http.client :as http] [clojure.data.json :as json]]
  (:use lead.graphite))

(defn get-metrics [target from until]
  (let [url (str "https://graph.hubteam.com/render/?target=" target "&from=" from "&until=" until "&format=json")
        response (http/get url {:as :json})]
    (graphite-json->serieses (:body response))))
