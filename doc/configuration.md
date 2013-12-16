# Configuration

## `register-fns-from-namespace`

Registers all of the Lead functions in a namespace.

```clojure
(register-fns-from-namespace 'lead.builtin-functions)
```

## `set-connector`

```clojure
(require 'lead.graphite.connector)
(set-connector (lead.graphite.connector/connector "http://graphite.example.com/"))
```

## `add-routes`

```clojure
(require '[compojure.route :refer [resources]])
(add-routes (resources "/static"))
```

## `set-uri-prefix`

```clojure
(set-uri-prefix "/lead")
```
