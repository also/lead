(ns lead.opentsdb.functions
  (:require [lead.main]
            [lead.core :refer [*configuration*]]
            [lead.functions :refer [leadfn]]
            [clj-http.client :as http]))

(defn set-base-url [base-url]
  (lead.main/update-config assoc-in [:opentsdb :base-url] base-url))

; query in the sense of http://opentsdb.net/docs/build/html/api_http/query/index.html, net the lead sense
(defn set-query-opts [query-opts]
  (lead.main/update-config assoc-in [:opentsdb :query-opts] query-opts))

(leadfn
  ^{:args "s"
    :complicated true}
  opentsdb
  [opts metric]
  (let [config (:opentsdb *configuration*)]
    (:body (http/get (str (:base-url config) "/api/query") (merge (:query-opts config)
                                                            {:as           :json
                                                             :query-params {"start"  (:start opts)
                                                                            "end"    (:end opts)
                                                                            "m"      metric
                                                                            "arrays" true}}))))
  )

