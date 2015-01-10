(ns ^:no-doc lead.lets
  (:require [lead.connector :as connector]
            [lead.parser]
            [lead.functions]))

; the current let being loaded
(def ^:dynamic *let* nil)
(def ^:dynamic *lets* {})

(defrecord LetConnector [wrapped-connector]
  connector/Connector
  (query [_ pattern]
    (if (*lets* pattern)
      [{:name pattern :is-leaf true}]
      (connector/query wrapped-connector pattern)))

  (load [_ target opts]
    (if-let [future (*lets* target)]
      ; TODO check for cycle
      @future
      (connector/load wrapped-connector target opts))))

(defn let-future [name target opts]
  (let [parsed (lead.parser/parse target)]
    (future
     (binding [*let* name]
       (lead.functions/run parsed opts)))))

(defn load-lets [lets opts]
  (into {} (map (fn [[name target]]
                  [name (let-future name target opts)]) lets)))
