(ns lead.opentsdb.functions
  (:require [lead.main]
            [lead.core :refer [*configuration*]]
            [lead.functions :refer [leadfn]]
            [clj-http.client :as http]
            [clojure.string :as string]
            [lead.builtin-functions :refer [map-serieses]]))

(defn set-base-url [base-url]
  (lead.main/update-config assoc-in [:opentsdb :base-url] base-url))

; query in the sense of http://opentsdb.net/docs/build/html/api_http/query/index.html, net the lead sense
(defn set-query-opts [query-opts]
  (lead.main/update-config assoc-in [:opentsdb :query-opts] query-opts))

(defn opentsdb-result->series
  [{:keys [start end]} opentsdb-result]
  {:name (str (:metric opentsdb-result)
              (if-let [tags (seq (:tags opentsdb-result))]
                (str \{
                     (string/join \, (map (fn [[k v]] (str (name k) \= v)) tags))
                     \})))
   :start start
   :end end
   :values (:dps opentsdb-result)
   :meta {:opentsdb (dissoc opentsdb-result :dps)}})

(leadfn
  ^{:args "s"
    :uses-opts true}
  opentsdb
  [opts metric]
  (let [config (:opentsdb *configuration*)]
    (map (partial opentsdb-result->series opts)
         (:body (http/get (str (:base-url config) "/api/query")
                          (merge (:query-opts config)
                                 {:as           :json
                                  :query-params {"start"  (:start opts)
                                                 "end"    (:end opts)
                                                 "m"      metric
                                                 "arrays" true}}))))))

(leadfn
  ^{:args "Ii"
    :aliases ["forceInterval"]}
  force-interval [serieses interval]
  (map
    (fn [series]
      (let [start (:start series)
            bucket-count (quot (- (:end series) start) interval)
            buckets (make-array Number bucket-count)]
        (doseq [[timestamp value] (:values series)]
          (let [bucket-index (quot (- timestamp start) interval)]
            (if (and (>= bucket-index 0)
                     (< bucket-index bucket-count))
              (aset buckets bucket-index value))))
        (assoc series :step interval :values (seq buckets))))
    serieses))
