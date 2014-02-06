# HTTP API

The Lead HTTP API maps closely to the [connector API](/src/main/clojure/lead/connector.clj)

The notable difference is the `render` endpoint vs. the `load` function. In the Lead "master" server, the `target` may be a function call, and will be evaluated using `lead.functions`. Other implementors of the Lead API will only be called with a single metric path `target`, which matches the `load` function.

## `GET /find`

Parameters:

`query`: A metric name pattern. See http://graphite.readthedocs.org/en/latest/render_api.html#paths-and-wildcards

Returns:

```json
[{"name": "metric.path", "is-leaf": true}]
```

## `GET /render`

Parameters:

`target`: see above.

`start`:

`end`:

Returns:

```json
[{"name": "metric.path", "start": 1412136000, "end": 1412222400, "step": 60, "values": [0.0, 0.1, 0.2...]}]
```
