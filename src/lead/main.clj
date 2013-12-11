(ns lead.main
  (:gen-class)
  (:require [lead.api :refer [create-routes add-routes *routes*] :as api]
            [lead.jetty-api :as server]
            [lead.connector :refer [set-connector] :as conn]
            [lead.functions :refer [register-fns-from-namespace] :as fns]))

(def ^:dynamic *uri-prefix*)

(defn set-uri-prefix [uri-prefix] (reset! *uri-prefix* uri-prefix))

(defn -main [& [port config-file]]
  (binding [*uri-prefix* (atom nil)
            *ns* (the-ns 'lead.main)
            fns/*fn-registry* (fns/create-registry)
            conn/*connector* (conn/init-connector)
            *routes* (create-routes)]
    (load-file config-file)

    (server/run (Integer. port) (let [handler (api/create-handler)]
                                  (if-let [uri-prefix @*uri-prefix*]
                                    (api/wrap-uri-prefix handler uri-prefix)
                                    handler)))))