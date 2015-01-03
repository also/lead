(ns lead.api-test
  (:require [clojure.test :refer [deftest is]]
            [lead.api :as api]
            [lead.core :as core]
            [lead.functions :as fns]
            [cheshire.core :as json]
            [cheshire.generate :as generate]))

(def opts {:now 1420088400
           :start 1420002000
           :end 1420088400
           :params {}})

(deftest test-parse-request
  (let [now 1420088400
        yesterday 1420002000
        req {"now" now}
        opts (api/parse-request req)]
    (is (= now (:now opts)))
    (is (= yesterday (:start opts)))
    (is (= now (:end opts)))))

(deftest test-eval-targets-no-targets
  (let [result (api/eval-targets [] opts)]
    (is (= [] result))))

(deftest test-eval-targets-missing-function
  (binding [core/*context* (atom (core/create-context))
            fns/*fn-registry* {}]
    (let [result (api/eval-targets ["missingFunction()"] opts)
          exceptions (:exceptions @core/*context*)]
      (is (= [{:target "missingFunction()", :result nil}] result))
      (is (= 1 (count exceptions)))
      (is (= "missingFunction" (-> exceptions first ex-data :name))))))

(defrecord UnsafeJSON [])
(generate/add-encoder UnsafeJSON
                      (fn [_ _] (throw (ex-info "unsafe json" {}))))

(deftest test-safe-json
  (let [json-string (json/generate-string (api/safe-json (->UnsafeJSON)))
        result (json/parse-string json-string)]
    (is (= {"exception-serializing-value" "clojure.lang.ExceptionInfo: unsafe json {}", "value-pr" "#lead.api_test.UnsafeJSON{}"}
           result))))
