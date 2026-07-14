/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

const { defineConfig, devices } = require('@playwright/test');

const classicTests = /tests\/classic\/.*\.spec\.ts/;
const isClassicOnlyRun = process.env.E2E_CLASSIC === '1' || process.argv.some(arg => arg.includes('tests/classic'));
const defaultBaseURL = process.env.CI || isClassicOnlyRun ? 'http://localhost:8080' : 'http://localhost:4200';
process.env.PLAYWRIGHT_BASE_URL = process.env.PLAYWRIGHT_BASE_URL || defaultBaseURL;

// https://playwright.dev/docs/test-configuration
module.exports = defineConfig({
  testDir: './e2e',
  globalSetup: require.resolve('./e2e/global-setup'),
  globalTeardown: require.resolve('./e2e/global-teardown'),
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 1,
  workers: 5,
  timeout: 300000,
  expect: {
    timeout: 60000
  },
  reporter: [
    [!!process.env.CI ? 'github' : 'list'],
    ['html', { open: !!process.env.CI ? 'never' : 'always' }],
    ['./e2e/reporter.coverage.ts']
  ],
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL,
    trace: 'on-first-retry', // https://playwright.dev/docs/trace-viewer
    screenshot: process.env.CI ? 'off' : 'only-on-failure',
    video: process.env.CI ? 'off' : 'retain-on-failure',
    launchOptions: {
      args: ['--disable-dev-shm-usage']
    },
    headless: true,
    actionTimeout: 60000,
    navigationTimeout: 180000
  },
  projects: [
    // Auth setup runs once and writes playwright/.auth/user.json, which the browser
    // projects consume via storageState — replaces the per-test login that raced
    // under parallel workers.
    {
      name: 'setup',
      testMatch: /global\.setup\.ts/
    },
    // skip classic in the auth CI leg (its Protractor predecessor was anonymous-only)
    ...(process.env.E2E_MODE === 'auth'
      ? []
      : [
          {
            name: 'classic',
            testMatch: classicTests,
            use: {
              ...devices['Desktop Chrome'],
              baseURL: 'http://localhost:8080',
              storageState: 'playwright/.auth/user.json'
            },
            dependencies: ['setup']
          }
        ]),
    {
      name: 'chromium',
      testIgnore: classicTests,
      use: {
        ...devices['Desktop Chrome'],
        permissions: ['clipboard-read', 'clipboard-write'],
        storageState: 'playwright/.auth/user.json'
      },
      dependencies: ['setup']
    },
    {
      name: 'Google Chrome',
      testIgnore: classicTests,
      use: {
        ...devices['Desktop Chrome'],
        channel: 'chrome',
        permissions: ['clipboard-read', 'clipboard-write'],
        storageState: 'playwright/.auth/user.json'
      },
      dependencies: ['setup']
    },
    {
      name: 'firefox',
      testIgnore: classicTests,
      use: {
        ...devices['Desktop Firefox'],
        storageState: 'playwright/.auth/user.json'
      },
      dependencies: ['setup']
    },
    {
      name: 'webkit',
      testIgnore: classicTests,
      use: {
        ...devices['Desktop Safari'],
        launchOptions: {
          slowMo: 200
        },
        storageState: 'playwright/.auth/user.json'
      },
      dependencies: ['setup']
    },
    {
      name: 'Microsoft Edge',
      testIgnore: classicTests,
      use: {
        ...devices['Desktop Edge'],
        channel: 'msedge',
        permissions: ['clipboard-read', 'clipboard-write'],
        storageState: 'playwright/.auth/user.json'
      },
      dependencies: ['setup']
    }
  ],
  webServer:
    process.env.CI || isClassicOnlyRun
      ? undefined
      : {
          command: 'npm run start',
          url: 'http://localhost:4200',
          reuseExistingServer: true,
          timeout: 2 * 60 * 1000
        }
});
