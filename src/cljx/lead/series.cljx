(ns lead.series)

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
;
; A simple function just transforms a series list--it wil be called with any series lists already loaded.
; A complicated function is responsible calling load-series on it's arguments, so it is able to use or change the options.


(defprotocol TimeSeries
  (slice [this start end]))

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
