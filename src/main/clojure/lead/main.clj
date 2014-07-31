(ns lead.main
  (:gen-class)
  (:require [lead.api :refer [create-routes add-routes *routes*] :as api]
            [lead.jetty-api :as server]
            [lead.connector :refer [set-connector] :as conn]
            [lead.functions :refer [register-fns-from-namespace] :as fns]))

(def ^:dynamic *uri-prefix*)
(def ^:dynamic *handler-wrapper*)

(defn set-uri-prefix [uri-prefix] (reset! *uri-prefix* uri-prefix))
(defn wrap-handler [handler-wrapper] (reset! *handler-wrapper* handler-wrapper))

(defn load-config [config-file]
  (binding [*ns* (the-ns 'lead.main)]
    (load-file config-file)))

(defn binding-config [f]
  (binding [*uri-prefix* (atom nil)
            *handler-wrapper* (atom nil)
            fns/*fn-registry* (fns/create-registry)
            conn/*connector* (conn/init-connector)
            *routes* (create-routes)]
    (f)))

(defn start-server [port]
  (server/run (Integer. port) (let [handler (api/create-handler)
                                    wrapped-handler (if-let [wrapper @*handler-wrapper*] (wrapper handler) handler)]
                                (if-let [uri-prefix @*uri-prefix*]
                                  (api/wrap-uri-prefix wrapped-handler uri-prefix)
                                  wrapped-handler))))

(defn -main [& [port config-file]]
  (binding-config
    (fn []
      (load-config config-file)
      (start-server port))))
