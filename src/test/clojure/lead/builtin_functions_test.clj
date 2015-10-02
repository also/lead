(ns lead.builtin-functions-test
  (:require [clojure.test :refer [deftest is]]
            [lead.functions :as fns]
            [lead.builtin-functions :as bi]))

(defn fake-series [name values] {:name (str "fake." name), :step 1, :values (vec values), :start 0, :end (count values)})

(def ones (fake-series "ones" (repeat 5 1)))
(def twos (fake-series "twos" (repeat 5 2)))
(def threes (fake-series "threes" (repeat 5 3)))
(def nils (fake-series "nils" (repeat 5 nil)))

(defn values= [expected actual] (every? identity (map #(= (:values %1) (:values %2)) expected actual)))

(deftest test-min
  (is (values= [ones] (bi/min-serieses [ones threes]))))

(deftest test-max
  (is (values= [threes] (bi/max-serieses [ones threes]))))

(deftest test-avg
  (is (values= [twos] (bi/avg-serieses [ones threes]))))

(deftest test-empty-avg
  (is (= nil (bi/avg-serieses nil))))

(deftest test-scale
  (is (values= [twos] (bi/scale-serieses [ones] 2))))

(deftest test-increment
  (is (values= [twos] (bi/increment-serieses [ones] 1))))

(deftest test-alias
  (is (= "ones-alias" (-> (bi/rename-serieses [ones] "ones-alias") first :name))))

(deftest test-remove-below
  (is (values= [nils] (bi/map-values-below-to-nil [ones] 2)))
  (is (values= [ones] (bi/map-values-below-to-nil [ones] 1))))

(deftest test-remove-above
  (is (values= [nils] (bi/map-values-above-to-nil [twos] 1)))
  (is (values= [twos] (bi/map-values-above-to-nil [twos] 2))))

(def builtin-functions-registry
  (binding [fns/*fn-registry-builder* (fns/create-registry)]
    (fns/register-fns-from-namespace 'lead.builtin-functions)
    @fns/*fn-registry-builder*))

(deftest test-group-serieses-by-node
  (binding [fns/*fn-registry* builtin-functions-registry]
    (let [serieses [ones twos threes]]
      (is (values= [twos] (bi/group-serieses-by-node serieses 0 "avg")))
      (is (values= serieses (bi/group-serieses-by-node serieses 1 "avg"))))))

(deftest test-simplify-serieses-names
  (is (= ["ones" "twos" "threes"] (map :name (bi/simplify-serieses-names [ones twos threes])))))

(deftest test-series-source
  (binding [fns/*fn-registry* builtin-functions-registry]
    (let [source (fns/->ValueCallable [{:name "", :values [], :start 1, :end 2, :step 1}])
          fn-source (fns/function-call "removeBelowValue" [source 1])
          result (fns/call fn-source {})]
      (is (= {:name "removeBelowValue()", :values [], :start 1, :end 2, :step 1} (first result))))))
