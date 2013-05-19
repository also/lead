(ns lead.connector
  [:require [clj-http.client :as http] [clojure.data.json :as json]])

(defn get-metrics [target from until]
  (let [url (str "http://localhost:5000/render/?target=" target "&from=" from "&until=" until)
        response (http/get url {:as :json})]
    (:body response)))
