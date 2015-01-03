(ns lead.main
  (:gen-class)
  (:require [lead.api :refer [create-routes add-routes *routes*] :as api]
            [lead.core]
            [lead.connector :refer [set-connector] :as conn]
            [lead.functions :refer [register-fns-from-namespace] :as fns]
            [ring.adapter.jetty :as jetty]))

(def ^:dynamic *uri-prefix*)
(def ^:dynamic *jetty-opts*)
(def ^:dynamic *handler-wrapper*)
(def ^:dynamic *configuration*)

(defn update-config [f & args]
  (apply swap! *configuration* f args))

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
            fns/*fn-registry-builder* (fns/create-registry)
            conn/*connector* (conn/init-connector)
            *routes* (create-routes)
            *configuration* (atom {})]
    (f)))

(defn create-handler []
  (let [handler (api/create-handler)
        wrapped-handler (if-let [wrapper @*handler-wrapper*] (wrapper handler) handler)]
    (binding [lead.core/*configuration* @*configuration*
              fns/*fn-registry* @fns/*fn-registry-builder*]
      (bound-fn*
        (if-let [uri-prefix @*uri-prefix*]
          (api/wrap-uri-prefix wrapped-handler uri-prefix)
          wrapped-handler)))))

(defn start-server [port]
  (jetty/run-jetty (create-handler) (merge @*jetty-opts* {:port (Integer. port)})))

(defn -main [& [port config-file]]
  (binding-config
    (fn []
      (load-config config-file)
      (start-server port))))
