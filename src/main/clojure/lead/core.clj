(ns lead.core
  (:require [lead.core]
            [lead.parser]))

(def ^:dynamic *context*)

(defn create-context [] {:exceptions []})

(defn exception
  [ex]
  (swap! *context* (fn [context]
                     (update-in context [:exceptions] #(cons exception %)))))

(defn eval-targets
  [targets opts]
  (flatten (pmap #(lead.functions/run (lead.parser/parse %) opts) targets)))
