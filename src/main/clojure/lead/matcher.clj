(ns lead.matcher
  (:require [clojure.string :as string])
  (:import java.util.regex.Pattern)
  (:gen-class
    :main false
    :methods [^:static [fnmatchPatternToRegex [String] java.util.regex.Pattern]
              ^:static [patternSegmentToRegexes [String] java.util.List]]))

(defn fnmatch-pattern-to-regex
  "Implement fnmatch patterns from http://hg.python.org/cpython/file/3a1db0d2747e/Lib/fnmatch.py"
  [pattern]
  (let [n (.length pattern)
        any-char "."]
    (string/join
      (loop [res ["(?s)"] ; (?s) -> Dot matches all (including newline)
             i 0]
        (if (< i n)
          (let [c (.charAt pattern i)
                i (inc i)]
            (case c
              \* (recur (conj res any-char \*) i)
              \? (recur (conj res any-char) i)
              \[ (let [close (.indexOf pattern (int \]) i)]
                   (if (< close 0)
                     (recur (conj res "\\[") i)

                     (let [negate (= \! (.charAt pattern i))
                           inside (.replace (.substring pattern (if negate (inc i) i) close) "\\" "\\\\")
                           prefix (cond
                                    negate \^
                                    (= \^ (.charAt pattern i)) \\
                                    :else "")]
                       (recur (conj res \[ prefix inside \]) (inc close)))))
              (recur (conj res (Pattern/quote (str c))) i)))
          res)))))

(def ^:private -fnmatchPatternToRegex fnmatch-pattern-to-regex)

(defn pattern-segment-to-regexes
  "https://github.com/graphite-project/graphite-web/blob/0.9.12/webapp/graphite/storage.py#L231"
  [segment]
  (let [p1 (.indexOf segment (int \{))
        p2 (.indexOf segment (int \}))]
    (if (< -1 p1 p2)
      (let [prefix (.substring segment 0 p1)
            suffix (.substring segment (inc p2))
            variations (string/split (.substring segment (inc p1) p2) #"\,")]
        (map #(re-pattern (str \^ (fnmatch-pattern-to-regex (str prefix % suffix)) \$)) variations))
      [(re-pattern (str \^ (fnmatch-pattern-to-regex segment) \$))])))

(def ^:private -patternSegmentToRegexes pattern-segment-to-regexes)

(defn segment-matcher
  [pattern]
  (let [regexes (pattern-segment-to-regexes pattern)]
    ; TODO only match wildcard segment to non-pattern segment
    (fn [s] (or (= :* s) (some #(re-matches % s) regexes)))))

(defn segment-matches
  [segment pattern]
  (boolean ((segment-matcher pattern) segment)))

(defn pattern->matcher-path [pattern]
  (map segment-matcher (string/split pattern #"\.")))

(defprotocol TreeFinder
  (root [this])
  (children [this node])
  (child [this node name])
  (is-leaf [this node]))

; {children: {:a {:children {:a1 ()} :b ()}}
(defrecord MapTreeFinder [tree]
  TreeFinder
  (root [this] tree)
  (children [this node]
    (keys (:children node)))
  (child [this node name]
    (get (:children node) name))
  (is-leaf [this node]
    (empty? (:children node))))

(defn- path->name [path]
  (str (string/join "." (map (fn [p] (if (= :* p) "*" p)) path))))

; TODO the meaning of "name" is a little fuzzy in here
; should it be the node name or the metric name?
; see tree-seq
(defn tree-find [finder pattern]
  (let [pattern-path (string/split pattern #"\.")
        walk (fn walk [node path matcher-path]
               (lazy-seq
                 (let [node-names (children finder node)
                       nodes (map #(child finder node %) node-names)
                       matcher (first matcher-path)
                       matcher-path (rest matcher-path)]
                   (if (seq matcher-path)
                     ; if there are more segments to match, follow the branches
                     (mapcat (fn [name node]
                               (if (and (not (is-leaf finder node))
                                        (matcher name))
                                 (walk node (conj path name) matcher-path)))
                             node-names nodes)
                     ; otherwise return the matches at this level
                     (filter identity
                             (map (fn [name node]
                                    (if (matcher name)
                                      {:path         (conj path name)
                                       :matched-path (map #(if (= :* %1) %2 %1) (conj path name) pattern-path)
                                       :is-leaf      (is-leaf finder node)
                                       :node         node}))
                                  node-names nodes))))))]
    (walk (root finder) [] (pattern->matcher-path pattern))))

(defn tree-query [finder pattern]
  (map (fn [result] {:name (path->name (:matched-path result)) :is-leaf (:is-leaf result)}) (tree-find finder pattern)))

(defn tree-traverse [finder path]
  (let [walk (fn walk [node path]
               (lazy-seq
                 (cons node
                       (if-let [c (child finder node (first path))]
                         (walk c (rest path))))))]
    (walk (root finder) path)))
