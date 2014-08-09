(ns lead.servlet-handler
  (:require [ring.util.servlet :as servlet]
            [lead.main :as lead]))

(defn create-handler [config-file]
  (lead/binding-config
    (fn []
      (lead/load-config config-file)
      (bound-fn* (lead/create-handler)))))

(defn handle [handler req res]
  (servlet/update-servlet-response res (handler (servlet/build-request-map req))))
