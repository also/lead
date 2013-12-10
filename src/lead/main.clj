(ns lead.main
  (:gen-class)
  (:require [lead.api :refer [create-routes add-routes *routes*]]
            [lead.jetty-api :as server]
            [lead.connector :refer [set-connector] :as conn]
            [lead.functions :refer [register-fns-from-namespace] :as fns]))

(defn -main [& [port config-file]]
  (binding [*ns* (the-ns 'lead.main)
            fns/*fn-registry* (fns/create-registry)
            conn/*connector* (conn/init-connector)
            *routes* (create-routes)]
    (load-file config-file)
    (server/run (Integer. port))))
