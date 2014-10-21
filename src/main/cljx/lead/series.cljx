(ns lead.series
  (:require
    [lead.math :as math]
    [clojure.string :as string]
    [schema.core :as s]
    #+clj
    [schema.macros :as sm])
  #+cljs
  (:require-macros [schema.macros :as sm]))

; A series has these attributes:
;  :values           a list of values
;  :start            the timestamp of the first value
;  :end              the timestamp of the last value
;  :step             the number of seconds per value
;
;  :consolidation-fn the function used to consolidate the values for aggregation with other series or display
;  :values-per-point the number of values for each consolidated point
;
; Options
;  :start
;  :end


(defprotocol TimeSeries
  (slice [this start end]))

(sm/defschema RegularSeriesValues
              s/Any)

(sm/defschema IrregularSeriesValues
              s/Any)

(sm/defschema RegularSeries
  {:start s/Int
   :end s/Int
   :step s/Int
   :values RegularSeriesValues
   s/Keyword s/Any})

(sm/defschema RegularSeriesList [RegularSeries])

(sm/defschema
  IrregularSeries
    {:start  s/Int
     :end    s/Int
     :values IrregularSeriesValues
     s/Keyword s/Any})

(sm/defschema IrregularSeriesList [IrregularSeries])

(defn regular? [series] (boolean (:step series)))

; TODO these are probably wrong
(defn slice-series-start
  [series start]
  (if (> start (:end series))
    (assoc series :start start :end start :values ())
    (let [offset-seconds (- start (:start series))
        offset-dps (quot offset-seconds (:step series))]
    (if (> offset-seconds 0)
      (assoc series :start (+ (:start series) (* offset-dps (:step series)))
                    :values (drop offset-dps (:values series)))
      series))))

(defn slice-series-end
  [series end]
  (if (< end (:start series))
    (assoc series :start end :end end :values ())
    (let [offset-seconds (- (:end series) end)
        offset-dps (quot offset-seconds (:step series))]
    (if (> offset-seconds 0)
      (assoc series :end (- (:end series) (* offset-dps (:step series)))
                    :values (drop-last offset-dps (:values series)))
      series))))

(defrecord FixedIntervalTimeSeries [step start end values]
  TimeSeries
  (slice
    [this start end]
    (-> this (slice-series-start start) (slice-series-end end))))

(defn name->path [name] (string/split name #"\."))
(defn path->name [path] (string/join "." path))

(defn non-nil [values] (keep identity values))

(defn safe
  "Calls f with the non-nil entries from values, if any. Returns nil otherwise."
  [f values]
  (if-let [values (seq (non-nil values))]
    (f values)))

(defn safe-apply [f] (partial safe (fn [values] (apply f values))))

(defn sum [values]
  (reduce + values))

(defn average [values]
  (/ (sum values) (count values)))

(def safe-average (partial safe average))
(def safe-min (safe-apply min))
(def safe-max (safe-apply max))
(def safe-sum (safe-apply +))

(defn range-serieses [serieses]
  "Calculate the range covered by all series"
  (let [start (apply min (map :start serieses))
        end   (apply max (map :end serieses))]
    [start end]))

(defn normalize-serieses [serieses]
  (let [step                (reduce math/lcm (map :step serieses))
        normalized-serieses (map #(assoc % :values-per-point (/ step (:step %))) serieses)
        [start max-end]     (range-serieses serieses)
        delta               (- max-end start)
        end                 (if (= 0 delta) 0 (- max-end (mod delta step)))]
    [normalized-serieses start end step]))

; could we call this resampling?
(defn consolidate-values
  "Consolidates groups of values-per-point values using consolidation-fn"
  [values consolidation-fn values-per-point]
  (map consolidation-fn (partition-all values-per-point values)))

(defn consolidate-series-values [series]
  (let [values-per-point (get series :values-per-point 1)]
    (if (= 1 values-per-point)
      (:values series)
      (let [consolidation-fn (get series :consolidation-fn safe-average)]
        (consolidate-values (:values series) consolidation-fn values-per-point)))))
