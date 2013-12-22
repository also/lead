(ns lead.math)

(def minus #+clj -' #+cljs -)
(def mult #+clj *' #+cljs *)

; TODO figure out exceptions
(defn abs "(abs n) is the absolute value of n" [n]
  (cond
   (not (number? n)) (throw (ex-info "abs requires a number" {:illegal-argument n}))
   (neg? n) (minus n)
   :else n))

(defn gcd "(gcd a b) returns the greatest common divisor of a and b" [a b]
  (when (not (integer? a)) (throw (ex-info "gcd requires two integers" {:illegal-argument a})))
  (when (not (integer? b)) (throw (ex-info "gcd requires two integers" {:illegal-argument b})))
  (loop [a (abs a) b (abs b)]
    (if (zero? b) a,
        (recur b (mod a b)))))

(defn lcm
  "(lcm a b) returns the least common multiple of a and b"
  [a b]
  (when (not (integer? a)) (throw (ex-info "lcm requires two integers" {:illegal-argument a})))
  (when (not (integer? b)) (throw (ex-info "lcm requires two integers" {:illegal-argument b})))
  (cond (zero? a) 0
        (zero? b) 0
        :else (abs (mult b (quot a (gcd a b))))))
