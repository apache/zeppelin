/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Karma configuration
// http://karma-runner.github.io/0.12/config/configuration-file.html
// Generated on 2014-08-29 using
// generator-karma 0.8.3

var webpackConfig = require('./webpack.config');

module.exports = function(config) {
  'use strict';

  config.set({
    // enable / disable watching file and executing tests whenever any file changes
    autoWatch: true,

    // base path, that will be used to resolve files and exclude
    basePath: './',

    // testing framework to use (jasmine/mocha/qunit/...)
    frameworks: ['jasmine'],

    // list of files / patterns to load in the browser
    files: [
      // for polyfill
      'node_modules/babel-polyfill/dist/polyfill.js',

      // npm:js
      'node_modules/jquery/dist/jquery.js',
      'node_modules/es5-shim/es5-shim.js',
      'node_modules/angular/angular.js',
      'node_modules/json3/lib/json3.js',
      'node_modules/bootstrap/dist/js/bootstrap.js',
      'node_modules/angular-cookies/angular-cookies.js',
      'node_modules/angular-sanitize/angular-sanitize.js',
      'node_modules/angular-animate/angular-animate.js',
      'node_modules/angular-touch/angular-touch.js',
      'node_modules/angular-route/angular-route.js',
      'node_modules/angular-resource/angular-resource.js',
      'node_modules/angular-ui-bootstrap/dist/ui-bootstrap-tpls.js',
      'node_modules/angular-websocket/angular-websocket.min.js',
      'node_modules/ace-builds/src-noconflict/ace.js',
      'node_modules/ace-builds/src-noconflict/mode-scala.js',
      'node_modules/ace-builds/src-noconflict/mode-python.js',
      'node_modules/ace-builds/src-noconflict/mode-sql.js',
      'node_modules/ace-builds/src-noconflict/mode-markdown.js',
      'node_modules/ace-builds/src-noconflict/mode-pig.js',
      'node_modules/ace-builds/src-noconflict/mode-sh.js',
      'node_modules/ace-builds/src-noconflict/mode-r.js',
      'node_modules/ace-builds/src-noconflict/mode-sparql.js',
      'node_modules/ace-builds/src-noconflict/keybinding-emacs.js',
      'node_modules/ace-builds/src-noconflict/ext-language_tools.js',
      'node_modules/ace-builds/src-noconflict/theme-chrome.js',
      'node_modules/ace-builds/src-noconflict/mode-javascript.js',
      'node_modules/angular-ui-ace/src/ui-ace.js',
      'node_modules/jquery.scrollTo/jquery.scrollTo.js',
      'node_modules/d3/d3.js',
      'node_modules/nvd3/build/nv.d3.js',
      'node_modules/jquery-ui/dist/jquery-ui.js',
      'node_modules/angular-dragdrop/src/angular-dragdrop.js',
      'node_modules/perfect-scrollbar/src/perfect-scrollbar.js',
      'node_modules/ng-sortable/dist/ng-sortable.js',
      'node_modules/angular-elastic/elastic.js',
      'node_modules/angular-elastic-input/dist/angular-elastic-input.min.js',
      'node_modules/angular-xeditable/dist/js/xeditable.js',
      'node_modules/highlight.js/lib/highlight.js',
      'node_modules/lodash/index.js',
      'node_modules/angular-filter/dist/angular-filter.js',
      'node_modules/ng-toast/dist/ngToast.js',
      'node_modules/ng-focus-if/focusIf.js',
      'node_modules/bootstrap3-dialog/dist/js/bootstrap-dialog.min.js',
      'node_modules/select2/dist/js/select2.js',
      'node_modules/mathjax/MathJax.js',
      'node_modules/clipboard/dist/clipboard.js',
      'node_modules/ngclipboard/dist/ngclipboard.js',
      'node_modules/diff/dist/diff.js',
      'node_modules/ng-infinite-scroll/build/ng-infinite-scroll.js',
      'node_modules/jszip/dist/jszip.js',
      'node_modules/excel-builder/dist/excel-builder.dist.js',
      'node_modules/angular-mocks/angular-mocks.js',
      // endnpm

      'src/index.js',
      { pattern: 'src/**/*.test.js', watched: false },
    ],

    // list of files / patterns to exclude
    exclude: [
      '.tmp/app/visualization/builtins/*.js'
    ],

    // web server port
    port: 9002,

    // Start these browsers, currently available:
    // - Chrome
    // - ChromeCanary
    // - Firefox
    // - Opera
    // - Safari (only Mac)
    // - PhantomJS
    // - IE (only Windows)
    browsers: [ 'FirefoxHeadless' ],

    plugins: [
      'karma-coverage',
      'karma-jasmine',
      'karma-sourcemap-loader',
      'karma-webpack',
      'karma-spec-reporter',
      'karma-firefox-launcher',
    ],

    reporters: ['spec', 'coverage'],

    webpack: webpackConfig,
    webpackMiddleware: {
      stats: 'errors-only'
    },

    preprocessors: {
      'src/**/*.js': ['webpack', 'sourcemap'],
    },

    coverageReporter: {
      dir: 'reports/coverage',
      reporters: [
        { type: 'html' },
        { type: 'text-summary' },
      ]
    },

    // Continuous Integration mode
    // if true, it capture browsers, run tests and exit
    singleRun: true,

    colors: true,

    // level of logging
    // possible values: LOG_DISABLE || LOG_ERROR || LOG_WARN || LOG_INFO || LOG_DEBUG
    logLevel: config.LOG_INFO,

    // Uncomment the following lines if you are using grunt's server to run the tests
    // proxies: {
    //   '/': 'http://localhost:9000/'
    // },
    // URL root prevent conflicts with the site root
    // urlRoot: '_karma_'
  });
};
