# Lead

[![Build Status](https://travis-ci.org/also/lead.png?branch=master)](https://travis-ci.org/also/lead)

Lead ~~is~~ will be a Graphite replacement (or enhancement!). Acting as a Graphite client or server, it can integrate all your sources of time-series data.

Graphite provides a rich set of functions to apply to time series, but couples this to a primitive user interface and storage engine. Lead aims to replace all three.

If you're already invested in Graphite, you can use [lead-graphite-server](https://github.com/also/lead-graphite-server) to expose any time-series data to Graphite. For example, you could apply Graphite function to Amazon CloudWatch data, or incorporate a new data store, like [OpenTSDB](http://opentsdb.net/).

If you're happy with Graphite's Carbon data storage, you can use Lead's implementation of the Graphite functions, while using one of the many existing Graphite UI alternatives, including [lead.js](https://github.com/also/lead.js/blob/master/docs/quickstart.md), a console for exploring Graphite data.

![I'm sure this diagram will answer all your questions](doc/diagram.png)

## Installation and Configuration

```clojure
; TODO
```

## Extending

```clojure
; TODO
```

Extend [Connector](src/lead/connector.clj). See [GraphiteConnector](src/lead/graphite/connector.clj) for an example.

## Roadmap

Lead is still experimental, and currently supports only the Graphite API. Soon, it will be expanded to directly support:

* Lead's own API
* OpenTSDB
* [ElasticSearch](http://www.elasticsearch.org/)
* [Amazon CloudWatch](http://aws.amazon.com/cloudwatch/)
