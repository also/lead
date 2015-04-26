var oog = require('oog');
var goog = oog('target/test-js/goog');
goog.load('target/test-js/index.js');

var cljs = goog.require('cljs.core');
cljs.enable_console_print_BANG_();
var t = goog.require('cemerick.cljs.test');
goog.requireAll();

var results = t.run_all_tests();
cljs.prn(results);
