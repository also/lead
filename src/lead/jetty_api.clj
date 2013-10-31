(ns lead.jetty-api
  (:require [ring.adapter.jetty :as jetty]
            [lead.api :as api]
            [lead.functions :as fns]))

(defn run []
  (jetty/run-jetty api/app {:port 3000}))
