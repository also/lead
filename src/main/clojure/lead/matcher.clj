(ns lead.matcher
  (:require [clojure.string :as string]
            [lead.core :refer [name->path path->name]])
  (:refer-clojure :exclude [meta])
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

(defn pattern? [s] (boolean (re-find #"[\[?*{]" s)))

(defn segment-matcher
  [pattern]
  (let [regexes (pattern-segment-to-regexes pattern)]
    ; TODO only match wildcard segment to non-pattern segment
    (fn [s] (or (= :* s) (some #(re-matches % s) regexes)))))

(defn segment-matches
  [segment pattern]
  (boolean ((segment-matcher pattern) segment)))

(defn pattern->matcher-path [pattern]
  (map segment-matcher (name->path pattern)))

(defprotocol TreeFinder
  (root [this]
        "Return the root node of the tree.")
  (children [this node]
            "Return a sequence of the name-segments of the node's children.")
  (child [this node segment]
         "Return the child of the node with the given name-segment.")
  (is-leaf [this node]
           "Return true if the node is a leaf."))

(defprotocol TreeMeta
  (meta [this node]))

; {:children {"a" {:children {"a1" {}} "b" {}}}
(defrecord MapTreeFinder [tree]
  TreeFinder
  (root [this] tree)
  (children [this node]
    (keys (:children node)))
  (child [this node segment]
    (get (:children node) segment))
  (is-leaf [this node]
    (empty? (:children node)))

  TreeMeta
  (meta [this node] (:meta node)))

(alter-meta! #'->MapTreeFinder assoc :no-doc true)
(alter-meta! #'map->MapTreeFinder assoc :no-doc true)

; see tree-seq
(defn tree-find
  "Returns a lazy sequence of matches of `pattern` in `finder`.

  Eeach match will have these keys:

  * `:path`: The path to the node. Non-enumerable segments will be `:*`.
  * `:matched-path`: The path to the node, as matched. Non-enumerable segments will be the segment from the pattern.
  * `:is-leaf`: `true` if the node is a leaf.
  * `:meta`: Map of metadata.
  * `:node`: The tree node."
  [finder pattern]
  (let [pattern-path (name->path pattern)
        walk (fn walk [node path matcher-path]
               (lazy-seq
                 (let [child-segments (children finder node)
                       nodes (map #(child finder node %) child-segments)
                       matcher (first matcher-path)
                       matcher-path (rest matcher-path)]
                   (if (seq matcher-path)
                     ; if there are more segments to match, follow the branches
                     (mapcat (fn [segment node]
                               (if (and (not (is-leaf finder node))
                                        (matcher segment))
                                 (walk node (conj path segment) matcher-path)))
                             child-segments nodes)
                     ; otherwise return the matches at this level
                     (filter identity
                             (map (fn [segment node]
                                    (if (matcher segment)
                                      {:path         (conj path segment)
                                       :matched-path (map #(if (= :* %1) %2 %1) (conj path segment) pattern-path)
                                       :is-leaf      (is-leaf finder node)
                                       :meta         (if (satisfies? TreeMeta finder) (meta finder node))
                                       :node         node}))
                                  child-segments nodes))))))]
    (walk (root finder) [] (pattern->matcher-path pattern))))

(defn tree-find-leaves [finder pattern]
  (filter :is-leaf (tree-find finder pattern)))

(defn tree-query [finder pattern]
  (map (fn [result] {:name (path->name (:matched-path result))
                     :is-leaf (:is-leaf result)
                     :meta (:meta result)})
       (tree-find finder pattern)))

(defn tree-traverse [finder path]
  (let [walk (fn walk [node path]
               (lazy-seq
                 (cons node
                       (if-let [c (child finder node (first path))]
                         (walk c (rest path))))))]
    (walk (root finder) path)))
