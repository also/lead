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

