(ns lead.api
  (:use ring.middleware.params
        ring.middleware.json
        compojure.core
        clojure.tools.logging)
  (:require [compojure.route :as route]
            [cheshire.generate :refer [add-encoder]]
            [clojure.stacktrace :as stacktrace]
            [lead.functions]
            [lead.parser]
            [lead.connector :as conn]
            [lead.time :as time]
            [lead.core :as core]
            [clojure.string :as string]))

; TODO this is only for exceptions
(add-encoder clojure.lang.Var #(.writeString %2 (str %1)))

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
    {:params params
     :now    (time/DateTime->seconds now)
     :start  (time/DateTime->seconds start)
     :end    (time/DateTime->seconds end)}))

(defn ex-message [^Throwable e]
  (if (ex-data e)
     (if-let [message (.getMessage e)]
       message
       (str e))
     (str e)))

(defn- transform-inner-exception [^Throwable e]
  {:message (ex-message e)
   :details (ex-data e)
   :cause   (if-let [cause (.getCause e)]
              (transform-inner-exception cause))})

(defn transform-exception [^Throwable e]
  (assoc (transform-inner-exception e)
    :stacktrace (with-out-str (stacktrace/print-cause-trace e))))

(defn execute [targets opts]
  (let [results (core/eval-targets targets opts)
        exceptions (:exceptions @core/*context*)
        exception-details (map transform-exception exceptions)]
    {:opts opts
     :results results
     :exceptions exception-details}))

(defroutes handler
  (GET "/find" [query]
       (let [results (conn/query @conn/*connector* query)]
         {:status 200
          :body results}))

  (GET "/render" [target & params]
    (let [targets (if (string? target) [target] target)
          opts (parse-request params)
          result (flatten (vals (core/eval-targets targets opts)))]
      {:status 200
       :body result}))

  (GET "/execute" [target & params]
       (let [targets (if (string? target) [target] target)
             opts (parse-request params)]
         {:status 200
          :body (execute targets opts)}))

  (POST "/execute" [:as request]
        (let [body (:body request)
              target (get body "target")
              targets (if (string? target) [target] target)
              opts (parse-request body)]
          {:status 200
          :body (execute targets opts)}))

  (GET "/parse" [target]
    {:status 200
     :body (lead.parser/parse target)})

  (GET "/functions" []
    {:status 200 :body (keys @lead.functions/*fn-registry*)}))

(def not-found (route/not-found "Not Found"))

(defn wrap-exception [f]
  (fn [request]
    (try (f request)
      (catch Exception e
        (warn e "Exception handling request")
        {:status 500
         :body   {:unhandled-exception (transform-exception e)
                  :exceptions (map transform-exception (:exceptions @core/*context*))}}))))

; TODO i probably shouldn't be writing CORS handling code
(defn wrap-cors [handler]
  (fn [request]
    (let [response (if (= :options (:request-method request))
                     {:status 200}
                     (handler request))]
      (update-in response [:headers]
                 (fn [headers]
                   (assoc headers
                          "Access-Control-Allow-Origin" "*"
                          "Access-Control-Allow-Headers" (get (:headers request) "access-control-request-headers" "")))))))

(defn wrap-context
  [handler]
  (fn [request]
    (binding [core/*context* (atom (core/create-context))]
      (handler request))))

(defn create-handler
  []
  (->
    (routes handler (apply routes @*routes*) not-found)
    wrap-json-response
    wrap-exception
    wrap-json-response
    wrap-json-body
    wrap-cors
    wrap-params
    wrap-context))

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
