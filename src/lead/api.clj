(ns lead.api
  [:use ring.middleware.params
        lead.parser
        lead.functions
        ring.middleware.json
        compojure.core]
  [:require [compojure.route :as route]
            [lead.functions :as fns]])

(defroutes handler
  (GET "/render/" [target]
    (let [result (run (parse target))]
      {:status 200
       :body result}))
  (GET "/functions/" []
    {:status 200 :body (keys @fns/fn-registry)})
  (route/not-found "Not Found"))


(def app
  (->
    handler
    wrap-json-response
    wrap-params))
