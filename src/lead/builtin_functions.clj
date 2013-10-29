(ns lead.builtin-functions
  (:require
    [clojure.math.numeric-tower :as numeric-tower]
    [clojure.string :as string]
    [lead.functions :as fns]
    [lead.connector :as connector]))

(defn name->path [name] (string/split name #"\."))
(defn path->name [path] (string/join "." path))

(defn
  same-depth?
  [paths]
  (apply = (map count paths)))

(defn filter-path-segments
  [important-segments path]
  (reduce (fn [acc [segment important?]] (if important? (conj acc segment) acc)) [] (map vector path important-segments)))

(defn varying-path-segments
  [paths]
  (apply map
         (fn
           [& segments]
           (not= 1 (count (set segments))))
         paths))

(defn
  simplify-serieses-names
  "Removes common segments from paths if all are the same length."
  [serieses]
  (let [paths (map name->path (map :name serieses))]
    (if (same-depth? paths)
      (let [important-segments (varying-path-segments paths)
            simplified-paths (map (partial filter-path-segments important-segments) paths)]
        (map (fn [series simplified-path] (assoc series :name (path->name simplified-path))) serieses simplified-paths))
      serieses)))

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
  (when (seq serieses)
    (let [[normalized-serieses, start, end, step] (normalize-serieses serieses)
           consolidated-values (map consolidate-series-values normalized-serieses)
           values (apply map (fn [& values] (f values)) consolidated-values)]
      [{:start start, :end end, :step step, :values values, :name (str name \( (string/join ", " (map :name serieses)) \))}])))

(defn map-serieses
  "Applies the map function to each value in each series"
  [serieses f name]
  (map #(assoc % :name (str name \( (:name %) \)) :values (map f (:values %))) serieses))

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
  ^{:args "T"
    :aliases ["sum", "sumSeries"]}
  sum-serieses
  [serieses]
  (sliced serieses safe-sum "sumSeries"))

(defn
  ^{:args "Tis"
    :aliases ["groupByNode"]}
  group-serieses-by-node
  [serieses node-num aggregate]
  (let [groups (group-by #(nth (name->path (:name %)) node-num) serieses)]
    (flatten (map #(fns/call-function aggregate [%]) (vals groups)))))

(defn
  ^{:args "T*"
    :aliases ["flatten" "group"]}
  flatten-serieseses
  [& serieses]
  (flatten serieses))

(defn
  ^{:args "Ti"
    :aliases ["offset"]}
  increment-serieses
  [serieses amount]
  (map-serieses serieses #(if % (+ amount %)) "offset"))

(defn
  ^{:args "Ti"}
  scale-serieses
  [serieses factor]
  (map-serieses serieses #(if % (* factor %)) "scale"))

(defn
  ^{:args "sii"
    :aliases ["load"]}
  load-serieses
  [q start end]
  (connector/get-metrics q start end))

(defn
  ^{:args "Ts"
    :aliases ["alias"]}
  rename-serieses
  [serieses name]
  (map #(assoc % :name name) serieses))
