oog = require 'oog'
goog = oog 'target/test-js/goog'
goog.load 'target/test-js/index.js'

cljs = goog.require 'cljs.core'
t = goog.require 'cemerick.cljs.test'
goog.requireAll()
cljs.enable_console_print_BANG_()
results = t.run_all_tests()
cljs.prn results
