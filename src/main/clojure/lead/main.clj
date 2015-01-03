(ns lead.main
  (:gen-class)
  (:require [lead.api :as api]
            [lead.core]
            [lead.connector :as conn]
            [lead.functions :refer [register-fns-from-namespace] :as fns]
            [ring.adapter.jetty :as jetty]))

(def ^:dynamic *uri-prefix*)
(def ^:dynamic *jetty-opts*)
(def ^:dynamic *handler-wrapper*)
(def ^:dynamic *configuration*)
(def ^:dynamic *connector*)
(def ^:dynamic *routes*)

(defn update-config [f & args]
  (apply swap! *configuration* f args))

(defn set-uri-prefix [uri-prefix] (reset! *uri-prefix* uri-prefix))
(defn set-jetty-opts [opts] (reset! *jetty-opts* opts))
(defn set-connector [connector] (reset! *connector* connector))
(defn add-routes [& routes] (swap! *routes* concat routes))
(defn wrap-handler [handler-wrapper] (reset! *handler-wrapper* handler-wrapper))

(defn load-config [config-file]
  (binding [*ns* (the-ns 'lead.main)]
    (load-file config-file)))

(defn binding-config [f]
  (binding [*uri-prefix* (atom nil)
            *jetty-opts* (atom {})
            *handler-wrapper* (atom nil)
            fns/*fn-registry-builder* (fns/create-registry)
            *connector* (atom nil)
            *routes* (atom [])
            *configuration* (atom {})]
    (f)))

(defn create-handler []
  (let [handler (api/create-handler @*routes*)
        wrapped-handler (if-let [wrapper @*handler-wrapper*] (wrapper handler) handler)]
    (binding [lead.core/*configuration* @*configuration*
              fns/*fn-registry* @fns/*fn-registry-builder*
              conn/*connector* @*connector*]
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
