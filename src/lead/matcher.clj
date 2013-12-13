(ns lead.matcher
  (:require [clojure.string :as string])
  (:import java.util.regex.Pattern))

(defn fnmatch-pattern-to-regex
  "Implement fnmatch patterns from http://hg.python.org/cpython/file/3a1db0d2747e/Lib/fnmatch.py"
  [pattern]
  (let [n (.length pattern)
        any-char "."]
    (string/join
      (loop [res []
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
