(ns lead.integration-test
  #+clj
  (:require [clojure.test :refer [deftest is]]
            [lead.functions :refer [leadfn]])
  #+cljs
  (:require-macros [cemerick.cljs.test :refer [deftest is]]
                   [lead.functions :refer [leadfn]])
  (:require [lead.functions :as fns]
            [lead.parser :as parser]))

(leadfn ^{:args "" :aliases ["test"]} test-f [] true)

(deftest test-registry
  (let [registry (fns/create-registry)]
    (binding [fns/*fn-registry-builder* registry]
      (fns/register-fns [#+clj (var test-f) #+cljs test-f])
      (binding [fns/*fn-registry* @fns/*fn-registry-builder*]
        (fns/run (parser/parse "test()") {})))
    (is (= 2 (count @registry)))))
