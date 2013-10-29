(ns lead.builtin-functions-test
  (:use clojure.test
        [lead.builtin-functions]
        [lead.functions]))

(register-fns-from-namespace 'lead.builtin-functions)

(defn fake-series [name values] {:name (str "fake." name), :step 1, :values (vec values), :start 0, :end (count values)})

(def ones (fake-series "ones" (repeat 5 1)))
(def twos (fake-series "twos" (repeat 5 2)))
(def threes (fake-series "threes" (repeat 5 3)))

(defn values= [expected actual] (every? identity (map #(= (:values %1) (:values %2)) expected actual)))

(deftest test-min
  (is (values= [ones] (min-serieses [ones threes]))))

(deftest test-max
  (is (values= [threes] (max-serieses [ones threes]))))

(deftest test-avg
  (is (values= [twos] (avg-serieses [ones threes]))))

(deftest test-empty-avg
  (is (= nil (avg-serieses nil))))

(deftest test-scale
  (is (values= [twos] (scale-serieses [ones] 2))))

(deftest test-increment
  (is (values= [twos] (increment-serieses [ones] 1))))

(deftest test-group-serieses-by-node
  (let [serieses [ones twos threes]]
    (is (values= [twos] (group-serieses-by-node serieses 0 "avg")))
    (is (values= serieses (group-serieses-by-node serieses 1 "avg")))))

(deftest test-simplify-serieses-names
  (is (= ["ones" "twos" "threes"] (map :name (simplify-serieses-names [ones twos threes])))))
