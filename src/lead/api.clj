(ns lead.api
  [:use ring.middleware.params
        lead.parser
        lead.functions
        ring.middleware.json
        compojure.core
        clojure.tools.logging]
  [:require [compojure.route :as route]
            [lead.functions :as fns]])

(defroutes handler
  (GET "/render/" [target start end]
    (let [result (run (parse target) {:start (Integer/parseInt start) :end (Integer/parseInt end)})]
      {:status 200
       :body result}))
  (GET "/parse/" [target]
    {:status 200
     :body (parse target)})
  (GET "/functions/" []
    {:status 200 :body (keys @fns/fn-registry)})
  (route/not-found "Not Found"))

(defn wrap-exception [f]
  (fn [request]
    (try (f request)
      (catch Exception e
        (let [sw (java.io.StringWriter.)]
          (warn e "Excption handling request")
          (.printStackTrace e (java.io.PrintWriter. sw))
          {:status 500
          :body {:exception (str sw)}})))))

(def app
  (->
    handler
    wrap-exception
    wrap-json-response
    wrap-params))
