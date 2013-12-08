(ns lead.api
  (:use ring.middleware.params
        lead.parser
        lead.functions
        ring.middleware.json
        compojure.core
        clojure.tools.logging)
  (:require [compojure.route :as route]
            [lead.functions :as fns]))

(def ^:dynamic *routes*)
(defn create-routes [] (atom []))
(defn add-routes
  [& routes]
  (swap! *routes* concat routes))

(defroutes handler
  (GET "/render" [target start end]
    (let [result (run (parse target) {:start (Integer/parseInt start) :end (Integer/parseInt end)})]
      {:status 200
       :body result}))
  (GET "/parse" [target]
    {:status 200
     :body (parse target)})
  (GET "/functions" []
    {:status 200 :body (keys @fns/*fn-registry*)}))

(def not-found (route/not-found "Not Found"))
(defn wrap-exception [f]
  (fn [request]
    (try (f request)
      (catch Exception e
        (warn e "Excption handling request")
        {:status 500
         :body {:exception (.getMessage e) :details (ex-data e)}}))))

(defn create-handler
  []
  (->
    (routes handler (apply routes @*routes*) not-found)
    wrap-exception
    wrap-json-response
    wrap-params))
