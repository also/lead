(ns lead.core
  (:require [lead.core]
            [lead.parser]))

(defn eval-targets
  [targets opts]
  (flatten (pmap #(lead.functions/run (lead.parser/parse %) opts) targets)))
