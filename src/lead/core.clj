(ns lead.core
  (:use lead.functions)
  (:require [lead.jetty-api :as server]))

(defn -main [& [port config-file]]
  (binding [*ns* (the-ns 'lead.core)
            *fn-registry* (create-registry)]
    (load-file config-file)
    (server/run (Integer. port))))
