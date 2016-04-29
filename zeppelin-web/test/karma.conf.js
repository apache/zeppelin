// Karma configuration
// http://karma-runner.github.io/0.12/config/configuration-file.html
// Generated on 2014-08-29 using
// generator-karma 0.8.3

module.exports = function(config) {
  'use strict';

  config.set({
    // enable / disable watching file and executing tests whenever any file changes
    autoWatch: true,

    // base path, that will be used to resolve files and exclude
    basePath: '../',

    // testing framework to use (jasmine/mocha/qunit/...)
    frameworks: ['jasmine'],

    // list of files / patterns to load in the browser
    files: [
      // bower:js
      'bower_components/jquery/dist/jquery.js',
      'bower_components/es5-shim/es5-shim.js',
      'bower_components/angular/angular.js',
      'bower_components/json3/lib/json3.js',
      'bower_components/bootstrap/dist/js/bootstrap.js',
      'bower_components/angular-cookies/angular-cookies.js',
      'bower_components/angular-sanitize/angular-sanitize.js',
      'bower_components/angular-animate/angular-animate.js',
      'bower_components/angular-touch/angular-touch.js',
      'bower_components/angular-route/angular-route.js',
      'bower_components/angular-resource/angular-resource.js',
      'bower_components/angular-bootstrap/ui-bootstrap-tpls.js',
      'bower_components/angular-websocket/angular-websocket.min.js',
      'bower_components/ace-builds/src-noconflict/ace.js',
      'bower_components/ace-builds/src-noconflict/mode-scala.js',
      'bower_components/ace-builds/src-noconflict/mode-python.js',
      'bower_components/ace-builds/src-noconflict/mode-sql.js',
      'bower_components/ace-builds/src-noconflict/mode-markdown.js',
      'bower_components/ace-builds/src-noconflict/mode-sh.js',
      'bower_components/ace-builds/src-noconflict/mode-r.js',
      'bower_components/ace-builds/src-noconflict/keybinding-emacs.js',
      'bower_components/ace-builds/src-noconflict/ext-language_tools.js',
      'bower_components/ace-builds/src-noconflict/theme-chrome.js',
      'bower_components/angular-ui-ace/ui-ace.js',
      'bower_components/jquery.scrollTo/jquery.scrollTo.js',
      'bower_components/d3/d3.js',
      'bower_components/nvd3/build/nv.d3.js',
      'bower_components/jquery-ui/jquery-ui.js',
      'bower_components/angular-dragdrop/src/angular-dragdrop.js',
      'bower_components/perfect-scrollbar/src/perfect-scrollbar.js',
      'bower_components/ng-sortable/dist/ng-sortable.js',
      'bower_components/angular-elastic/elastic.js',
      'bower_components/angular-elastic-input/dist/angular-elastic-input.min.js',
      'bower_components/angular-xeditable/dist/js/xeditable.js',
      'bower_components/highlightjs/highlight.pack.js',
      'bower_components/lodash/lodash.js',
      'bower_components/angular-filter/dist/angular-filter.min.js',
      'bower_components/ngtoast/dist/ngToast.js',
      'bower_components/ng-focus-if/focusIf.js',
      'bower_components/bootstrap3-dialog/dist/js/bootstrap-dialog.min.js',
      'bower_components/zeroclipboard/dist/ZeroClipboard.js',
      'bower_components/moment/moment.js',
      'bower_components/pikaday/pikaday.js',
      'bower_components/handsontable/dist/handsontable.js',
      'bower_components/angular-mocks/angular-mocks.js',
      // endbower
      'src/app/app.js',
      'src/app/app.controller.js',
      'src/app/**/*.js',
      'src/components/**/*.js',
      'test/spec/**/*.js'
    ],

    // list of files / patterns to exclude
    exclude: [],

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
    browsers: [
      'PhantomJS'
    ],

    reporters: ['coverage','progress'],

    preprocessors: {
      'src/*/{*.js,!(test)/**/*.js}': 'coverage'
    },

    coverageReporter: {
      type: 'html',
      dir: '../reports/zeppelin-web-coverage',
      subdir: '.'
    },

    // Which plugins to enable
    plugins: [
      'karma-phantomjs-launcher',
      'karma-jasmine',
      'karma-coverage'
    ],

    // Continuous Integration mode
    // if true, it capture browsers, run tests and exit
    singleRun: false,

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
