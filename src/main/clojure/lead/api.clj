(ns lead.api
  (:import (com.fasterxml.jackson.core JsonGenerator))
  (:require [compojure.route :as route]
            [compojure.core :refer [defroutes routes GET POST]]
            [ring.middleware.params]
            [ring.middleware.json]
            [cheshire.core :refer [generate-string]]
            [cheshire.generate :as generate]
            [clojure.stacktrace :as stacktrace]
            [clojure.tools.logging :as log]
            [lead.functions]
            [lead.parser]
            [lead.connector :as conn]
            [lead.time :as time]
            [lead.core :as core]
            [lead.lets]
            [clojure.string :as string]))

; TODO this is only for exceptions
(generate/add-encoder clojure.lang.Var generate/encode-str)
(generate/add-encoder clojure.lang.IFn generate/encode-str)

(generate/add-encoder Class generate/encode-str)
(generate/add-encoder clojure.lang.Namespace generate/encode-str)

(defrecord SafeJSON [value])
(generate/add-encoder SafeJSON
  (fn [safe jg]
    (let [value (:value safe)
          json (try
                  (generate-string value)
                  (catch Exception ex
                    (generate-string {:exception-serializing-value (str ex)
                                      :value-pr (pr-str value)})))]
      (.writeRawValue jg json))))

(alter-meta! #'->SafeJSON assoc :no-doc true)
(alter-meta! #'map->SafeJSON assoc :no-doc true)

(def safe-json ->SafeJSON)

(defn safe-json-map [m] (into {} (map (fn [[k v]] [k (safe-json v)]) m)))

(defn parse-request [params]
  (let [now (if-let [now-seconds (params "now")]
              (time/seconds->DateTime (Integer. now-seconds))
              (time/now))
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
   :details (safe-json-map (ex-data e))
   :cause   (if-let [cause (.getCause e)]
              (transform-inner-exception cause))})

(defn transform-exception [^Throwable e]
  (assoc (transform-inner-exception e)
    :stacktrace (with-out-str (stacktrace/print-cause-trace e))))

(defn eval-targets
  [targets opts]
  (let [parsed-targets (map (juxt identity lead.parser/parse) targets)]
    (binding [core/*configuration* (core/apply-configuration (or (-> opts :params (get "configuration")) []))]
      (into [] (pmap
                (fn eval-target [[target parsed-target]]
                  {:target target
                   :result (try
                             (lead.functions/run parsed-target opts)
                             (catch Exception e
                               (core/exception e)
                               nil))})
                parsed-targets)))))

(defn execute [targets opts]
  (let [lets (lead.lets/load-lets (-> opts :params (get "let")) opts)
        results (binding [lead.lets/*lets* lets
                          conn/*connector* (lead.lets/->LetConnector conn/*connector*)]
                  (eval-targets targets opts))
        exceptions (:exceptions @core/*context*)
        exception-details (map transform-exception exceptions)]
    {:opts opts
     :results results
     :exceptions exception-details}))

(defroutes handler
  (GET "/find" [query]
       (let [results (conn/query conn/*connector* query)]
         {:status 200
          :body results}))

  (GET "/render" [target & params]
    (let [targets (if (string? target) [target] target)
          opts (parse-request params)
          result (vec (flatten (map :result (eval-targets targets opts))))]
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
    {:status 200 :body (lead.functions/function-info)}))

(def not-found (route/not-found "Not Found"))

(defn wrap-exception [f]
  (fn [request]
    (try (f request)
      (catch Exception e
        (log/warn e "Exception handling request")
        {:status 500
         :headers {"Content-Type" "application/json; charset=utf-8"}
         :body (generate-string
                 {:unhandled-exception (transform-exception e)
                  :exceptions          (map transform-exception (:exceptions @core/*context*))})}))))

; TODO i probably shouldn't be writing CORS handling code
(defn wrap-cors
  "Responds with a 200 to OPTIONS requests, and adds `Access-Control-Allow-` headers to support cross-domain requests."
  [handler]
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
  "Binds `lead.core/*context*`."
  [handler]
  (fn [request]
    (binding [core/*context* (atom (core/create-context))]
      (handler request))))

(defn create-handler
  "Creates a Ring handler with the default wrappers, default Compujure routes, and any extra routes."
  [extra-routes]
  (->
    (routes handler (apply routes extra-routes) not-found)
    ring.middleware.json/wrap-json-response
    wrap-exception
    ring.middleware.json/wrap-json-body
    wrap-cors
    ring.middleware.params/wrap-params
    wrap-context))

(defn wrap-uri-prefix
  "Adds a prefix to all routes."
  [handler prefix]
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
