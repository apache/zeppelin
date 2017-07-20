var baseConfig = {
  baseUrl: 'http://localhost:8080/',
  directConnect: true,
  capabilities: {
    browserName: 'chrome',
  },
  allScriptsTimeout: 300000, // 5 min

  framework: 'jasmine',
  specs: ['e2e/**/*.js'],
  jasmineNodeOpts: {
    showTiming: true,
    showColors: true,
    isVerbose: true,
    includeStackTrace: false,
    defaultTimeoutInterval: 300000, // 5 min
    print: function() {}, // remove protractor dot reporter, we are using jasmine-spec-reporter
  },

  onPrepare: function() {
    // should be false for angular apps
    // browser.ignoreSynchronization = true;

    browser.manage().timeouts().pageLoadTimeout(300000);
    // with the implicitlyWait() this will even though you expect the element not to be there
    browser.manage().timeouts().implicitlyWait(30000);

    // add reporter to display executed tests in console
    var SpecReporter = require('jasmine-spec-reporter').SpecReporter;
    jasmine.getEnv().addReporter(new SpecReporter({
      spec: {
        displayStacktrace: true
      }
    }));
  },
};

var chromeOptions = {
  args: ['--disable-gpu', '--no-sandbox']
}

baseConfig.capabilities.chromeOptions = chromeOptions;

exports.config = baseConfig;
