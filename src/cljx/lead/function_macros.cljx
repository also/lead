(ns lead.function-macros)

(defmacro leadfn
  [name & body]
  `(do (def ~name (fn ~@body))
       (aset ~name "meta" ~(assoc (meta name) :name (str name)))))
