var baseConfig = {
  baseUrl: 'http://localhost:8080/',
  directConnect: true,
  // chromeOnly: true,
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
    // waiting for angular app is loaded
    browser.ignoreSynchronization = true;
    browser.manage().timeouts().pageLoadTimeout(300000);
    browser.manage().timeouts().implicitlyWait(60000);

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
  args: ['--headless', '--disable-gpu']
}

if (process.env.TRAVIS) {
  chromeOptions.binary = process.env.CHROME_BIN;
}

if (process.env.TRAVIS) {
  baseConfig.capabilities.chromeOptions = chromeOptions;
}

exports.config = baseConfig;
