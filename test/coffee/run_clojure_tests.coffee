oog = require 'oog'
goog = oog 'target/test-js/goog'
goog.load 'target/test-js/index.js'

cljs = goog.require 'cljs.core'
t = goog.require 'cemerick.cljs.test'
goog.require 'lead.builtin_functions_test'
cljs.set_print_fn_BANG_ (s) -> console.log s
results = t.run_all_tests()
cljs.prn results
