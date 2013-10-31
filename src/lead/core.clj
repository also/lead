(ns lead.core
  (:use lead.functions)
  (:require [lead.jetty-api :as server]))

(defn -main [config-file]
  (binding [*ns* (the-ns 'lead.core)]
    (load-file config-file))
  (server/run))
