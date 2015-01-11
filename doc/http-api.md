# HTTP API

The Lead HTTP API maps closely to the [connector API](/src/main/clojure/lead/connector.clj)

The notable difference is the `execute` endpoint vs. the `load` function. In the Lead "master" server, the `target` may be a function call, and will be evaluated using `lead.functions`. Other implementors of the Lead API will only be called with a single metric path `target`, which matches the `load` function.

## `GET /find`

Parameters:

`query`: A metric name pattern. See http://graphite.readthedocs.org/en/latest/render_api.html#paths-and-wildcards

Returns:

```json
[{"name": "metric.path", "is-leaf": true}]
```

## `GET /execute`

Parameters:

`target`: see above.

`start`:

`end`:

Returns:

```json
{
  "opts": {
    "params": {},
    "now": 1409418978,
    "start": 1409332578,
    "end": 1409418978
  },
  "results": [
    {
      "name": "randomWalkFunction('random walk')",
      "result": [
        {
          "name": "random walk",
          "start": 1409332578,
          "end": 1409418978,
          "step": 60,
          "values": [
            0,
            -0.09634609011669037,
            -0.3853613444125503,
            -0.45663534456644406
          ]
        }
      ]
    }
  ],
  "exceptions": []
}
```

## `POST /execute`

Parameters:

Same as `GET /execute`, in the JSON POST body.

Returns:

Same as `GET /execute`


## `GET /functions`

Parameters:

None

Returns:

```json
{
  "min": {
    "ns": "lead.builtin-functions",
    "file": "lead/builtin_functions.clj",
    "name": "min-serieses",
    "line": 93,
    "aliases": [
      "min",
      "minSeries"
    ],
    "schema": {
      "input": {
        "first": [
          "RegularSeriesList"
        ],
        "last": null
      },
      "output": "RegularSeriesList"
    }
  }
}
```
