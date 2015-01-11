(ns lead.main
  (:gen-class)
  (:require [lead.api :as api]
            [lead.core]
            [lead.connector :as conn]
            [lead.functions :refer [register-fns-from-namespace] :as fns]
            [ring.adapter.jetty :as jetty]))

(def ^:dynamic ^:no-doc *uri-prefix*)
(def ^:dynamic ^:no-doc *jetty-opts*)
(def ^:dynamic ^:no-doc *handler-wrapper*)
(def ^:dynamic ^:no-doc *configuration*)
(def ^:dynamic ^:no-doc *connector*)
(def ^:dynamic ^:no-doc *routes*)

(defn update-config [f & args]
  (apply swap! *configuration* f args))

(defn set-uri-prefix
  "Sets a prefix for all API paths."
  [uri-prefix]
  (reset! *uri-prefix* uri-prefix))

(defn set-jetty-opts
  "Sets options for the Jetty server.
  The options are passed to http://ring-clojure.github.io/ring/ring.adapter.jetty.html#var-run-jetty."
  [opts]
  (reset! *jetty-opts* opts))

(defn set-connector
  "Sets the connector for finding and loading data by name."
  [connector]
  (reset! *connector* connector))

(defn add-routes
  "Add extra Compujure routes to the API."
  [& routes]
  (swap! *routes* concat routes))

(defn wrap-handler
  [handler-wrapper]
  (reset! *handler-wrapper* handler-wrapper))

(defn load-config [config-file]
  (binding [*ns* (the-ns 'lead.main)]
    (load-file config-file)))

(defn binding-config [f]
  "Calls `f` with bindings for configuration."
  (binding [*uri-prefix* (atom nil)
            *jetty-opts* (atom {})
            *handler-wrapper* (atom nil)
            fns/*fn-registry-builder* (fns/create-registry)
            *connector* (atom nil)
            *routes* (atom [])
            *configuration* (atom {})]
    (f)))

(defn create-handler
  "Creates a Ring handler using the currently bound configuration."
  []
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
