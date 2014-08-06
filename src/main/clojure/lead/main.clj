(ns lead.main
  (:gen-class)
  (:require [lead.api :refer [create-routes add-routes *routes*] :as api]
            [lead.connector :refer [set-connector] :as conn]
            [lead.functions :refer [register-fns-from-namespace] :as fns]
            [ring.adapter.jetty :as jetty]))

(def ^:dynamic *uri-prefix*)
(def ^:dynamic *jetty-opts*)
(def ^:dynamic *handler-wrapper*)

(defn set-uri-prefix [uri-prefix] (reset! *uri-prefix* uri-prefix))
(defn set-jetty-opts [opts] (reset! *jetty-opts* opts))
(defn wrap-handler [handler-wrapper] (reset! *handler-wrapper* handler-wrapper))

(defn load-config [config-file]
  (binding [*ns* (the-ns 'lead.main)]
    (load-file config-file)))

(defn binding-config [f]
  (binding [*uri-prefix* (atom nil)
            *jetty-opts* (atom {})
            *handler-wrapper* (atom nil)
            fns/*fn-registry* (fns/create-registry)
            conn/*connector* (conn/init-connector)
            *routes* (create-routes)]
    (f)))

(defn create-handler []
  (let [handler (api/create-handler)
        wrapped-handler (if-let [wrapper @*handler-wrapper*] (wrapper handler) handler)]
    (if-let [uri-prefix @*uri-prefix*]
      (api/wrap-uri-prefix wrapped-handler uri-prefix)
      wrapped-handler)))

(defn start-server [port]
  (jetty/run-jetty (bound-fn* (create-handler)) (merge @*jetty-opts* {:port (Integer. port)})))

(defn -main [& [port config-file]]
  (binding-config
    (fn []
      (load-config config-file)
      (start-server port))))
