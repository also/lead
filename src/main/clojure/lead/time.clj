(ns lead.time
  (:import [org.joda.time Period DurationFieldType DateTime DateTimeZone Duration]))

(defn now [] (DateTime. DateTimeZone/UTC))

(defn seconds->DateTime
  [seconds]
  (DateTime. (* 1000 seconds) DateTimeZone/UTC))

(defn DateTime->seconds
  [^DateTime date-time]
  (-> date-time .getMillis (quot 1000)))

(defn Duration->seconds
  [^Duration duration]
  (-> duration .getMillis (quot 1000)))

(defn Period->seconds
  [^Period period]
  (-> period .toStandardDuration .getMillis (quot 1000)))

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
  (let [[match o unit] (re-matches #"(\d+)([a-zA-Z]+)" s)]
    (if match
      (if-let [field (duration-field-for-unit unit)]
        (.withField Period/ZERO field (Long. o))))))

(defn parse-offset
  ^Period
  [s]
  (let [[sign p] (if-let [sign (#{\+ \-} (first s))]
                        [sign (.substring s 1)]
                        [\- s])
        period (parse-period p)]
    (if period
      (if (= \- sign)
        (.negated period)
        period))))

(defn parse-time
  [s ^DateTime now]
  (cond
    (or (integer? s) (re-matches #"\d+" s)) (seconds->DateTime (Integer. s))
    (= "now" s) now
    (= "midnight" s) (.withTimeAtStartOfDay now)
    :else (if-let [offset (parse-offset s)]
            (.plus now offset)
            (throw (ex-info "invalid time string" {:type :invalid-input
                                                   :string s})))))
