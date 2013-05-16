(ns lead.api
  [:use ring.middleware.params
        lead.parser
        lead.functions
        ring.middleware.json])

(defn handler [{params :params, uri :uri}]
  (case uri
    "/render/"
      (let [result (run (parse (params "target")))]
        {:status 200
         :body result})
    {:status 404
     :body "Not Found"}))


(def app
  (->
    handler
    wrap-json-response
    wrap-params))
