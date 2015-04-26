(ns lead.integration-test
  (:require [clojure.test :refer [deftest is]]
            [lead.functions :as fns :refer [leadfn]]
            [lead.parser :as parser]))

(leadfn ^{:args "" :aliases ["test"]} test-f [] true)

(deftest test-registry
  (let [registry (fns/create-registry)]
    (binding [fns/*fn-registry-builder* registry]
      (fns/register-fns [(var test-f)])
      (binding [fns/*fn-registry* @fns/*fn-registry-builder*]
        (fns/run (parser/parse "test()") {})))
    (is (= 2 (count @registry)))))
