(ns lead.time
  (:import [org.joda.time Period DurationFieldType DateTime DateTimeZone]))

(defn now [] (DateTime. DateTimeZone/UTC))

(defn seconds->DateTime
  [seconds]
  (DateTime. (* 1000 seconds) DateTimeZone/UTC))

(defn DateTime->seconds
  [^DateTime date-time]
  (-> date-time .getMillis (quot 1000)))

(def period-prefixes
  {"s" (DurationFieldType/seconds)
   "min" (DurationFieldType/minutes)
   "h" (DurationFieldType/hours)
   "d" (DurationFieldType/days)
   "w" (DurationFieldType/weeks)
   "mon" (DurationFieldType/months)
   "y" (DurationFieldType/years)})

(defn duration-field-for-unit
  ^DurationFieldType
  [^String unit]
  (some (fn [[prefix period-type]]
          (if (.startsWith unit prefix) period-type))
          period-prefixes))

(defn parse-period
  ^Period
  [s]
  (let [[match sign o unit] (re-matches #"([+-])?(\d+)([a-zA-Z]+)" s)]
    (if match
      (let [offset (* (Integer. o) (if (= "+" sign) 1 -1))]
        (if-let [field (duration-field-for-unit unit)]
          (.withField Period/ZERO field (long offset)))))))

(defn parse-time
  [s ^DateTime now]
  (cond
    (re-matches #"\d+" s) (seconds->DateTime (* 1000 (Integer. s)))
    :else (if-let [period (parse-period s)]
            (.plus now period))))