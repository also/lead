(ns lead.core
  (:use lead.functions
        lead.connector
        [lead.api :only [create-routes add-routes *routes*]])
  (:require [lead.jetty-api :as server]))

(defn -main [& [port config-file]]
  (binding [*ns* (the-ns 'lead.core)
            *fn-registry* (create-registry)
            *connector* (init-connector)
            *routes* (create-routes)]
    (load-file config-file)
    (server/run (Integer. port))))
