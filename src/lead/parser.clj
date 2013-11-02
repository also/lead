(ns lead.parser
  [:refer-clojure :exclude [char]]
  [:use the.parsatron]
  [:require [clojure.string :as string]])

(declare p-expr)

(defparser opt [parser default-val]
  (either
    parser
    (always default-val)))

(defparser p-boolean []
  (let->> [v (either (string "true") (string "false"))]
    (always (read-string v))))

(defparser p-string []
  (let->> [open  (either (char \') (char \"))
           chars (many (token #(not= open %)))
           _     (char open)]
    (always (string/join chars))))

(defparser integer []
  (many1 (digit)))

(defparser sign []
  (opt (choice (char \-)
               (char \+))
       \+))

(defparser signed-integer []
  (let->> [s      (sign)
           digits (integer)]
    (always (concat [s] digits))))

(defparser frac []
  (>> (char \.)
      (let->> [digits (integer)]
             (always (concat [\.] digits)))))

(defparser exp []
  (>> (choice (char \e) (char \E))
      (let->> [digits (signed-integer)]
        (always (concat [\e] digits)))))

(defparser p-num []
  (let->> [intpart  (signed-integer)
           fracpart (opt (frac) [])
           expart   (opt (exp) [])]
    (always (read-string (string/join (concat intpart fracpart expart))))))

(defparser separated-by [sep p]
  (either
    (let->> [fst p]
      (let->> [rst (many (>> sep p))]
        (always (vec (cons fst rst)))))
    (always [])))

(defparser p-identifier []
  (let->> [chars (many (letter))]
    (always (string/join chars))))

(defparser p-function-call []
  (let->> [function-name (p-identifier)
           args          (between (char \() (char \)) (separated-by (char \,) (p-expr)))]
    (always (list 'function-call function-name args))))

(defparser ws []
  (many (token #{\space \tab})))

(defparser with-ws [p]
  (let->> [_ (ws)
           r p
           _ (ws)]
    (always r)))

(defparser p-expr []
  (with-ws (choice
             (attempt (p-boolean)) ; attempt so we don't fail on t ^r
             (p-num)
             (p-function-call)
             (p-string))))

(defparser p-target []
  (let->> [expr (with-ws (p-function-call))
           _    (eof)]
    (always expr)))

(defn parse [string]
  (run (p-target) string))
