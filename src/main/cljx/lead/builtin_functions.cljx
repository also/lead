(ns lead.builtin-functions
  (:require
    [lead.math :as math]
    [clojure.string :as string]
    [lead.functions :as fns]
    [lead.series :refer [consolidate-series-values
                         normalize-serieses
                         safe-average
                         safe-sum
                         safe-min
                         safe-max
                         name->path
                         path->name]]
    #+clj [lead.connector :as connector]
    #+clj [cheshire.core :as cheshire]
    #+clj [clojure.walk])
  #+cljs (:require-macros [lead.functions :refer [leadfn]])
  #+clj (:require [lead.functions :refer [leadfn]])
  #+clj (:import [org.apache.commons.math3.stat.descriptive DescriptiveStatistics]))

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

(defn sliced
  "Creates a new series by calling f for each time-slice of serieses"
  [serieses f name]
  (when (seq serieses)
    (let [[normalized-serieses start end step] (normalize-serieses serieses)
           consolidated-values (map consolidate-series-values normalized-serieses)
           values (apply map (fn [& values] (f values)) consolidated-values)]
      [{:start start, :end end, :step step, :values values, :name (str name \( (string/join ", " (map :name serieses)) \))}])))

(defn map-serieses
  "Applies the map function to each value in each series"
  [serieses f name]
  (map #(assoc % :name (str name \( (:name %) \)) :values (map f (:values %))) serieses))

(leadfn
  ^{:args "s"
    :complicated true
    :aliases ["param"]}
  param-value
  [opts name]
  (get (:params opts) name))

#+clj
(leadfn
  ^{:args    "s"
    :aliases ["parseJson"]}
  parse-json
  [string]
  (cheshire/parse-string string))

(leadfn
  ^{:args "?"}
  serieses
  [o]
  (if (map? o)
    (clojure.walk/keywordize-keys o)
    (map clojure.walk/keywordize-keys o)))

(leadfn
  ^{:args "T"
    :aliases ["avg" "averageSeries"]}
  avg-serieses
  [serieses]
  (sliced serieses safe-average "averageSeries"))

(leadfn
  ^{:args "T"
    :aliases ["min" "minSeries"]}
  min-serieses
  [serieses]
  (sliced serieses safe-min "minSeries"))

(leadfn
  ^{:args "T"
    :aliases ["max" "maxSeries"]}
  max-serieses
  [serieses]
  (sliced serieses safe-max "maxSeries"))

(leadfn
  ^{:args "T"
    :aliases ["sum" "sumSeries"]}
  sum-serieses
  [serieses]
  (sliced serieses safe-sum "sumSeries"))

(leadfn
  ^{:args "Tis"
    :aliases ["groupByNode"]}
  group-serieses-by-node
  [serieses node-num aggregate]
  (let [groups (group-by #(nth (name->path (:name %)) node-num) serieses)]
    (flatten (map #(fns/call-simple-function aggregate [%]) (vals groups)))))

(leadfn
  ^{:args "T*"
    :aliases ["flatten" "group"]}
  flatten-serieseses
  [& serieses]
  (flatten serieses))

(leadfn
  ^{:args "Ti"
    :aliases ["offset"]}
  increment-serieses
  [serieses amount]
  (map-serieses serieses #(if % (+ amount %)) "offset"))

(leadfn
  ^{:args "Ti"
    :aliases ["scale"]}
  scale-serieses
  [serieses factor]
  (map-serieses serieses #(if % (* factor %)) "scale"))

#+clj
(leadfn
  ^{:args "s"
    :aliases ["load"]
    :complicated true}
  load-from-connector
  [opts target]
  (connector/load @connector/*connector* target opts))

(leadfn
  ^{:args "Ts"
    :aliases ["alias"]}
  rename-serieses
  [serieses name]
  (map #(assoc % :name name) serieses))

(defn replace-serieses-values-with-nil [f serieses name]
  (map-serieses serieses #(if (f %) %) name))

(leadfn
  ^{:args "Ti"
    :aliases ["removeBelowValue"]}
  map-values-below-to-nil
  [serieses value]
  (replace-serieses-values-with-nil #(>= % value) serieses "removeBelowValue"))

(leadfn
  ^{:args "Ti"
    :aliases ["removeAboveValue"]}
  map-values-above-to-nil
  [serieses value]
  (replace-serieses-values-with-nil #(<= % value) serieses "removeBelowValue"))

#+clj
(def statfns
  {:min    (fn min [^DescriptiveStatistics stats] (.getMin stats))
   :max    (fn max [^DescriptiveStatistics stats] (.getMax stats))
   :first  (fn stats-first [^DescriptiveStatistics stats] (-> stats .getElement 0))
   :last   (fn stats-last [^DescriptiveStatistics stats] (-> stats .getElement (- (.getN stats) 1)))
   :sum    (fn sum [^DescriptiveStatistics stats] (.getSum stats))
   :mean   (fn mean [^DescriptiveStatistics stats] (.getMean stats))
   :stddev (fn stdddev [^DescriptiveStatistics stats] (.getStandardDeviation stats))
   :50th   (fn pct50th [^DescriptiveStatistics stats] (.getPercentile stats 0.5))
   :75th   (fn pct75th [^DescriptiveStatistics stats] (.getPercentile stats 0.75))
   :95th   (fn pct95th [^DescriptiveStatistics stats] (.getPercentile stats 0.95))
   :99th   (fn pct99th [^DescriptiveStatistics stats] (.getPercentile stats 0.99))
   :999th  (fn pct999th [^DescriptiveStatistics stats] (.getPercentile stats 0.999))})

#+clj
(defn stat-fn [name]
  [name ((keyword name) statfns)])

#+clj
(defn apply-desc-stats-r-fns [fns n series]
  (let [n-slices (Math/ceil (/ (count (:values series)) n))
        bucketses (vec (map (fn [_] (make-array Number n-slices)) fns))]
    (dorun (map-indexed (fn [i slice]
                    (let [stats (DescriptiveStatistics. (double-array slice))]
                      (dorun
                        (map #(aset %1 i (%2 stats))
                             bucketses (map second fns)))))
                  (partition-all n (:values series))))
    (map
      (fn [buckets [name _]]
        {:name (str "descriptiveStatsR(" (:name series) ", " n ", '" name "')")
         :start (:start series)
         :end (:end series)
         :step (* n (:step series))
         :values (seq buckets)})
      bucketses fns)))

#+clj
(leadfn
  ^{:args "Tis*"
    :aliases ["descriptiveStatsR"]}
  descriptive-stats-regular
  [serieses n & names]
  (let [fns (map stat-fn names)]
    (flatten (map (partial apply-desc-stats-r-fns fns n) serieses))))

#+clj
(defn apply-desc-stats-i-fns [fns interval series]
  (let [start (:start series)
        n-slices (quot (- (:end series) start) interval)
        bucketses (vec (map (fn [_] (make-array Number n-slices)) fns))]
    (doseq [slice (partition-by (fn [[ts _]] (quot (- ts start) interval)) (:values series))]
      (let [[[ts _] & _] slice
            i (quot (- ts start) interval)]
        (if (and (>= i 0)
                 (< i n-slices))
          (let [stats (DescriptiveStatistics. (double-array (map second slice)))]
            (dorun
              (map #(aset %1 i (%2 stats))
                   bucketses (map second fns)))))))
    (map
      (fn [buckets [name _]]
        {:name (str "descriptiveStatsI(" (:name series) ", " interval ", '" name "')")
         :start (:start series)
         :end (:end series)
         :step interval
         :values (seq buckets)})
      bucketses fns)))

#+clj
(leadfn
  ^{:args "Tis*"
    :aliases ["descriptiveStatsI"]}
  descriptive-stats-irregular
  [serieses interval & names]
  (let [fns (map stat-fn names)]
    (flatten (map (partial apply-desc-stats-i-fns fns interval) serieses))))
