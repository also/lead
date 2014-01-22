(ns lead.api
  (:use ring.middleware.params
        lead.parser
        lead.functions
        ring.middleware.json
        compojure.core
        clojure.tools.logging)
  (:require [compojure.route :as route]
            [lead.functions :as fns]
            [lead.connector :as conn]
            [lead.time :as time]
            [clojure.string :as string]))

(def ^:dynamic *routes*)
(defn create-routes [] (atom []))
(defn add-routes
  [& routes]
  (swap! *routes* concat routes))

(defn parse-request [params]
  (let [now (time/now)
        start-param (params "start" (params "from"))
        end-param (params "end" (params "until"))
        start (if start-param
                (time/parse-time start-param now)
                (.minusDays now 1))
        end (if end-param
              (time/parse-time end-param now)
              now)]
    {:now   (time/DateTime->seconds now)
     :start (time/DateTime->seconds start)
     :end   (time/DateTime->seconds end)}))

(defroutes handler
  (GET "/find" [query]
       (let [results (conn/query @conn/*connector* query)]
         {:status 200
          :body results}))
  (GET "/render" [target & params]
    (let [targets (if (string? target) [target] target)
          result (flatten (pmap #(run (parse %) (parse-request params)) targets))]
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

(defn wrap-cors [handler]
  (fn [request]
    (let [response (handler request)]
      (update-in response [:headers]
                 (fn [headers]
                   (assoc headers
                          "Access-Control-Allow-Origin" "*"
                          "Access-Control-Allow-Headers" (get (:headers request) "access-control-request-headers" "")))))))

(defn create-handler
  []
  (->
    (routes handler (apply routes @*routes*) not-found)
    wrap-exception
    wrap-json-response
    wrap-cors
    wrap-params))

(defn wrap-uri-prefix [handler prefix]
  (fn [request]
    (let [response (handler (assoc request
                              :uri (string/replace-first (:uri request)
                                                         (re-pattern (str "^" prefix "/?"))
                                                         "/")))]
      (if (<= 300 (:status response) 308)
        (assoc response
          :headers (assoc (:headers response)
                     "Location" (string/replace-first (get-in response [:headers "Location"])
                                                      #"^/"
                                                      (str prefix "/"))))
        response))))
