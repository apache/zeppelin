exports.config = {
  baseUrl: 'http://localhost:8080/',
  directConnect: true,
  chromeOnly: true,
  capabilities: {
    browserName: 'chrome',
    chromeOptions: {
      // args: ["--headless", 'no-sandbox', "--disable-gpu", "--window-size=800x600"]
    }
  },
  allScriptsTimeout: 110000,

  framework: 'jasmine',
  specs: ['e2e/**/*.js'],
  jasmineNodeOpts: {
    showTiming: true,
    showColors: true,
    isVerbose: true,
    includeStackTrace: false,
    defaultTimeoutInterval: 400000,
    print: function() {}, // remove protractor dot reporter, we are using jasmine-spec-reporter
  },

  onPrepare: function() {
    // waiting for angular app is loaded
    browser.ignoreSynchronization = true;
    browser.manage().timeouts().pageLoadTimeout(80000);
    browser.manage().timeouts().implicitlyWait(50000);


    // add reporter to display executed tests in console
    var SpecReporter = require('jasmine-spec-reporter').SpecReporter;
    jasmine.getEnv().addReporter(new SpecReporter({
      spec: {
        displayStacktrace: true
      }
    }));
  },
};
