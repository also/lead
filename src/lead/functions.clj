(ns lead.functions
  [:require [clojure.math.numeric-tower :as numeric-tower] [clojure.java.io :as io] [clojure.data.json :as json]]
  [:use [lead.parser :only (parse)] lead.connector])

; A series has these attributes:
;  :values           a list of values
;  :start            the timestamp of the first value
;  :end              the timestamp of the last value
;  :step             the number of seconds per value
;
;  :consolidation-fn the function used to consolidate the values for aggregation with other series or display
;  :values-per-point the number of values for each consolidated point

(defn range-serieses [serieses]
  (let [start (apply min (map :start serieses)),
        end   (apply max (map :end serieses))]
    [start, end]))

(defn normalize-serieses [serieses]
  (let [step                (reduce numeric-tower/lcm (map :step serieses)),
        normalized-serieses (map #(assoc % :values-per-point (/ step (:step %))) serieses),
        [start, max-end]    (range-serieses serieses),
        end                 (- max-end (mod (- max-end start) step))]
    [normalized-serieses, start, end, step]))

; TODO the name is kind of a lie, it filters out all falsey values
(defn non-nil [values] (filter identity values))

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

; could we call this resampling?
(defn consolidate-values
  "Consolidates groups of values-per-point values using consolidation-fn"
  [values, consolidation-fn, values-per-point]
  (map consolidation-fn (partition-all values-per-point values)))

(defn consolidate-series-values [series]
  (let [values-per-point (get series :values-per-point 1)]
    (if (= 1 values-per-point)
      (:values series)
      (let [consolidation-fn (get series :consolidation-fn safe-average)]
        (consolidate-values (:values series) consolidation-fn, values-per-point)))))

(defn sliced
  "Creates a new series by calling f for each time-slice of serieses"
  [serieses f, name]
  (if (seq serieses)
    (let [[normalized-serieses, start, end, step] (normalize-serieses serieses)
           consolidated-values (map consolidate-series-values normalized-serieses)
           values (apply map (fn [& values] (f values)) consolidated-values)]
      [{:start start, :end end, :step step, :values values, :name name}])
    nil))

(defn
  ^{:args "T"
    :aliases ["avg" "averageSeries"]}
  avg-serieses
  [serieses]
  (sliced serieses safe-average, "averageSeries"))

(defn 
  ^{:args "T"
    :aliases ["min" "minSeries"]}
  min-serieses
  [serieses]
  (sliced serieses safe-min, "minSeries"))

(defn 
  ^{:args "T"
    :aliases ["max" "maxSeries"]}
  max-serieses
  [serieses]
  (sliced serieses safe-max, "maxSeries"))

(defn
  ^{:args "T*"
    :aliases ["flatten"]}
  flatten-serieseses
  [& serieses]
  (flatten serieses))

(defn
  ^{:args "sii"
    :aliases ["load"]}
  load-serieses
  [q start end]
  (get-metrics q start end))

(def fn-registry (atom {}))

(defn fn-names [f] (cons (str (:name (meta f))) (:aliases (meta f))))

(defn register-fns
  "Registers a list of functions by it's aliases."
  [fns]
  (swap! fn-registry (partial apply assoc) (flatten
    (map (fn [f] (map (fn [n] [n f]) (fn-names f))) fns))))

(defn find-fns
  "Find lead functions in a namespace."
  [namespace]
  (filter #(:args (meta %)) (vals (ns-publics namespace))))

(register-fns (find-fns 'lead.functions))

(defn call-function [function args]
  (if-let [f (@fn-registry function)]
    (try
      (apply f args)
      (catch Throwable t
        (throw (RuntimeException. (str "Error calling " function ": " (.getMessage t)) t))))
    (throw (RuntimeException. (str function " is not a function")))))

(defn run [program] (binding [*ns* (the-ns 'lead.functions)] (eval program)))
