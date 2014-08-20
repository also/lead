(ns lead.core
  (:require [lead.core]
            [lead.parser]
            [lead.functions]))

(def ^:dynamic *configuration*)

(def ^:dynamic *context*)

(defn create-context [] {:exceptions []})

(defn exception
  [ex]
  (swap! *context* (fn [context]
                     (update-in context [:exceptions] #(cons ex %)))))

(defn eval-targets
  [targets opts]
  (let [parsed-targets (map (juxt identity lead.parser/parse) targets)]
    (into {} (pmap
               (fn eval-target [[target parsed-target]]
                 [target
                  (try
                    (lead.functions/run parsed-target opts)
                    (catch Exception e
                      (exception e)
                      nil))])
               parsed-targets))))
