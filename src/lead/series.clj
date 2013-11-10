(ns lead.series)

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
