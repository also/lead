(ns lead.graphite.connector-test
  (:require [lead.graphite.connector :refer :all]
            [clojure.test :refer [deftest is]]))

(def A "A")
(def B "B")
(def C "C")

(def graphite-ring
  (create-ring [[A "a"]
                [B "a"]
                [C "a"]]))

(deftest test-ring
  (is (= A (first (get-node "a" graphite-ring))))
  (is (= B (first (get-node "b" graphite-ring))))
  (is (= C (first (get-node "c" graphite-ring))))
  (is (= A (first (get-node "d" graphite-ring))))
  (is (= C (first (get-node "e" graphite-ring))))
  (is (= C (first (get-node "f" graphite-ring))))
  (is (= A (first (get-node "g" graphite-ring)))))
