(ns lead.api-test
  (:require [clojure.test :refer [deftest is]]
            [lead.api :as api]))

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
