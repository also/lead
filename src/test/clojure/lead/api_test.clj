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

(defn bad-seq [] (lazy-seq (throw (ex-info "bad-seq" {}))))

(fns/leadfn ^{:aliases ["return1"]} return-1 []
            1)

(fns/leadfn ^{:aliases ["valueSeqException"]} value-seq-exception []
            [{:name "valueSeqException" :values (bad-seq)}])

(fns/leadfn ^{:aliases ["functionException"]} function-exception []
            (throw (ex-info "bad-function" {})))

(def default-registry
  (binding [fns/*fn-registry-builder* (fns/create-registry)]
    (fns/register-fns-from-namespace 'lead.api-test)
    @fns/*fn-registry-builder*))

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
  (binding [core/*context* (atom (core/create-context))]
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

(def default-handler
  (binding [fns/*fn-registry* default-registry]
    (bound-fn* (api/create-handler []))))

(deftype AlwaysEqual []
  Object
  (equals [_ _] true))

(def ignore-equality (->AlwaysEqual))

(def default-response-opts
  {"params" {"now" "1420088400"}
   "now" 1420088400
   "start" 1420002000
   "end" 1420088400})

(deftest test-execute-no-targets
  (let [req {:request-method :get :uri "/execute" :query-string "now=1420088400"}
        res (default-handler req)
        body (-> res :body json/parse-string)]
    (is (= 200 (:status res)))
    (is (= {"opts" default-response-opts
            "results" []
            "exceptions" []}
           body))))

(deftest test-execute-return-1
  (let [req {:request-method :get :uri "/execute" :query-string "now=1420088400&target=return1()"}
        res (default-handler req)
        body (-> res :body json/parse-string)]
    (is (= 200 (:status res)))
    (is (= {"opts"       default-response-opts
            "results"    [{"target" "return1()", "result" 1}]
            "exceptions" []}
           body))))

(deftest test-execute-return-1-too-many-args
  (let [req {:request-method :get :uri "/execute" :query-string "now=1420088400&target=return1(1)"}
        res (default-handler req)
        body (-> res :body json/parse-string)]
    (is (= 200 (:status res)))
    (is (= {"opts"       default-response-opts
            "results"    [{"target" "return1(1)", "result" nil}]
            "exceptions" [{"stacktrace" ignore-equality
                           "message" "Error in Lead function"
                           "details" {"lead-exception-type" "function-internal-error",
                                      "message" ignore-equality, ;"Wrong number of args (1) passed to: api-test/eval6765/return-1--6766",
                                      "function-name" "return1",
                                      "args" [1]}
                           "cause" {"message" ignore-equality, ;"clojure.lang.ArityException: Wrong number of args (1) passed to: api-test/eval6765/return-1--6766",
                                    "details" {},
                                    "cause" nil}}]}
           body))))

(deftest test-execute-missing-function
  (let [req {:request-method :get :uri "/execute" :query-string "now=1420088400&target=missingFunction()"}
        res (default-handler req)
        body (-> res :body json/parse-string)]
    (is (= 200 (:status res)))
    (is (= {"opts" default-response-opts
            "results" [{"target" "missingFunction()", "result" nil}]
            "exceptions" [{"stacktrace" ignore-equality
                           "message" "missingFunction is not a function"
                           "details" {"lead-exception-type" "illegal-argument"
                                      "name" "missingFunction"}
                           "cause" nil}]}
           body))))

(deftest test-execute-function-exception
  (let [req {:request-method :get :uri "/execute" :query-string "now=1420088400&target=functionException()"}
        res (default-handler req)
        body (-> res :body json/parse-string)]
    (is (= 200 (:status res)))
    (is (= {"opts" default-response-opts
            "results" [{"target" "functionException()", "result" nil}]
            "exceptions" [{"stacktrace" ignore-equality,
                           "message" "Error in Lead function",
                           "details" {"lead-exception-type" "function-internal-error",
                                      "message" "bad-function",
                                      "function-name" "functionException",
                                      "args" []},
                           "cause" {"message" "bad-function",
                                    "details" {},
                                    "cause" nil}}]}
           body))))

(deftest test-execute-values-seq-exception
  (let [req {:request-method :get :uri "/execute" :query-string "target=valueSeqException()"}
        res (default-handler req)
        body (-> res :body json/parse-string)]
    (is (= 500 (:status res)))
    (is (= {"unhandled-exception" {"stacktrace" ignore-equality
                                   "message" "bad-seq"
                                   "details" {}
                                   "cause" nil}
            "exceptions" []}
           body))))
