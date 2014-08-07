(ns lead.core
  (:require [lead.core]
            [lead.parser]
            [lead.functions]))

(def ^:dynamic *context*)

(defn create-context [] {:exceptions []})

(defn exception
  [ex]
  (swap! *context* (fn [context]
                     (update-in context [:exceptions] #(cons ex %)))))

(defn eval-targets
  [targets ,opts]
  (into {} (pmap (fn eval-target [target] [target (lead.functions/run (lead.parser/parse target) opts)]) targets)))
