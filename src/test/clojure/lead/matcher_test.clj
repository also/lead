(ns lead.matcher-test
  (:require [clojure.test :refer [deftest is]]
            [lead.matcher :refer :all]))

(defn fnmatch [s pattern]
  (re-matches (re-pattern (str \^ (fnmatch-pattern-to-regex pattern) \$)) s))

; http://hg.python.org/cpython/file/3a1db0d2747e/Lib/test/test_fnmatch.py
(deftest test-fnmatch
  (is (fnmatch "abc" "abc"))
  (is (fnmatch "abc" "?*?"))
  (is (fnmatch "abc" "???*"))
  (is (fnmatch "abc" "*???"))
  (is (fnmatch "abc" "???"))
  (is (fnmatch "abc" "*"))
  (is (fnmatch "abc" "ab[cd]"))
  (is (fnmatch "abc" "ab[!de]"))
  (is (not (fnmatch "abc" "ab[de]")))
  (is (not (fnmatch "a" "??")))
  (is (not (fnmatch "a" "b")))
  (is (fnmatch "\\" "[\\]"))
  (is (fnmatch "a" "[!\\]"))
  (is (not (fnmatch "\\" "[!\\]")))
  (is (fnmatch "foo\nbar" "foo*"))
  (is (fnmatch "foo\nbar\n" "foo*"))
  (is (not (fnmatch "\nfoo" "foo*")))
  (is (fnmatch "\n" "*")))

(deftest test-segment-matches
  (is (segment-matches "a" "a"))
  (is (segment-matches "a{" "a{"))
  (is (segment-matches "a}" "a}"))
  (is (not (segment-matches "a" "ab")))
  (is (not (segment-matches "ab" "a")))
  (is (segment-matches "a" "a{}"))
  (is (segment-matches "a" "{a}"))
  (is (segment-matches "a" "{}a"))
  (is (segment-matches "{a" "{a"))
  (is (segment-matches "}a" "}a"))
  (is (segment-matches "ba" "{b}a"))
  (is (segment-matches "ba" "{b,c}a"))
  ; (is (segment-matches "a" "{b,}a")) ; TODO ?
  (is (segment-matches "ab" "a{b}"))
  (is (segment-matches "ab" "a{b,c}"))
  (is (segment-matches "ace" "a{b,c}[de]"))
  ; (is (segment-matches "ab" "a{b,c}[]")) ; TODO wat
  (is (segment-matches "ab{d}" "a{b,c}{d}"))
  (is (segment-matches "adeg" "a{b,[cd]}[ef]g"))
  (is (segment-matches "ace" "a[{b,c}]e"))
  (is (segment-matches "a{}b" "a{{}}b"))
  (is (segment-matches "a{b}c" "a{{}b}c")))

(def wildcard-tree
  (->MapTreeFinder
    {:children
      {"a"
        {:children
          {:*
            {:children
              {"b" {}}}}}}}))

(deftest test-wildcard-tree
  (let [[result] (tree-find wildcard-tree "a.x.b")]
    (is (= :* (second (:path result))))
    (is (= "x" (second (:matched-path result))))))
