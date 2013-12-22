(ns lead.jetty-api
  (:require [ring.adapter.jetty :as jetty]
            [lead.api :as api]
            [lead.functions :as fns]))

(defn run [port handler]
  (jetty/run-jetty (bound-fn* handler) {:port port}))
