(ns lead.functions-test
  (:use clojure.test
        [lead.functions]))

(defn fake-series [values] {:step 1, :values (vec values), :start 0, :end (count values)})

(def ones (fake-series (repeat 5 1)))
(def twos (fake-series (repeat 5 2)))
(def threes (fake-series (repeat 5 3)))

(deftest test-min
  (is (= ones (min-serieses [ones threes]))))

(deftest test-max
  (is (= threes (max-serieses [ones threes]))))

(deftest test-avg
  (is (= twos (avg-serieses [ones threes]))))

(deftest test-empty-avg
  (is (= nil (avg-serieses nil))))
