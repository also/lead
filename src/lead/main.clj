(ns lead.main
  (:gen-class)
  (:use lead.functions)
  (:require [lead.api :refer [create-routes add-routes *routes*]]
            [lead.jetty-api :as server]
            [lead.connector :refer [set-connector] :as conn]))

(defn -main [& [port config-file]]
  (binding [*ns* (the-ns 'lead.main)
            *fn-registry* (create-registry)
            conn/*connector* (conn/init-connector)
            *routes* (create-routes)]
    (load-file config-file)
    (server/run (Integer. port))))
