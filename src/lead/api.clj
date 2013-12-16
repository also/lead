(ns lead.api
  (:use ring.middleware.params
        lead.parser
        lead.functions
        ring.middleware.json
        compojure.core
        clojure.tools.logging)
  (:import [org.joda.time LocalDateTime DateTimeZone])
  (:require [compojure.route :as route]
            [lead.functions :as fns]
            [lead.connector :as conn]
            [clojure.string :as string]))

(def ^:dynamic *routes*)
(defn create-routes [] (atom []))
(defn add-routes
  [& routes]
  (swap! *routes* concat routes))

(defn Partial->lead-time [partial]
  (-> partial (.toDateTime DateTimeZone/UTC) .getMillis (quot 1000)))

(defn parse-request [params]
  (let [now (LocalDateTime. DateTimeZone/UTC)
        start-param (params "start" (params "from"))
        end-param (params "end" (params "until"))
        start (if start-param
                (Integer/parseInt start-param)
                (-> now (.minusDays 1) Partial->lead-time))
        end (if end-param
              (Integer/parseInt end-param)
              (-> now Partial->lead-time))]
    {:now (Partial->lead-time now)
     :start start
     :end end}))

(defroutes handler
  (GET "/find" [query]
       (let [results (conn/query @conn/*connector* query)]
         {:status 200
          :body results}))
  (GET "/render" [target & params]
    (let [result (run (parse target) (parse-request params))]
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
