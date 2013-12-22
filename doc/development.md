## ClojureScript

Lead uses Node.js for JavaScript tests. Run

```
npm install
```

before running tests.

There's a separate Leiningen profile for ClojureScript tests to add the dependency on instaparse-cljs.

```
lein cljx
lein with-profile cljs cljsbuild test
```
