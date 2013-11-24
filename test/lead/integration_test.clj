(ns lead.integration-test
  (:use clojure.test)
  (:require [lead.functions :as fns]
            [lead.parser :as parser]))

(defn ^{:args "" :aliases ["test"]} test-f [] true)

(deftest test-registry
  (let [registry (fns/create-registry)]
    (binding [fns/*fn-registry* registry]
      (fns/register-fns [(var test-f)])
      (fns/run (parser/parse "test()") {}))
    (is (= 2 (count @registry)))))
