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
const { baseConfig } = require('./playwright.shared');

const defaultBaseURL = process.env.CI ? 'http://localhost:8080' : 'http://localhost:4200';
process.env.PLAYWRIGHT_BASE_URL = process.env.PLAYWRIGHT_BASE_URL || defaultBaseURL;

// https://playwright.dev/docs/test-configuration
module.exports = defineConfig({
  ...baseConfig,
  // The legacy classic UI suite runs separately via playwright.classic.config.js.
  testIgnore: /tests\/classic\/.*\.spec\.ts/,
  reporter: [
    [!!process.env.CI ? 'github' : 'list'],
    ['html', { open: !!process.env.CI ? 'never' : 'always' }],
    ['./e2e/reporter.coverage.ts']
  ],
  use: {
    ...baseConfig.use,
    baseURL: process.env.PLAYWRIGHT_BASE_URL
  },
  projects: [
    // Auth setup runs once and writes playwright/.auth/user.json, which the browser
    // projects consume via storageState — replaces the per-test login that raced
    // under parallel workers.
    {
      name: 'setup',
      testMatch: /global\.setup\.ts/
    },
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        permissions: ['clipboard-read', 'clipboard-write'],
        storageState: 'playwright/.auth/user.json'
      },
      dependencies: ['setup']
    },
    {
      name: 'firefox',
      use: {
        ...devices['Desktop Firefox'],
        storageState: 'playwright/.auth/user.json'
      },
      dependencies: ['setup']
    },
    {
      name: 'webkit',
      use: {
        ...devices['Desktop Safari'],
        launchOptions: {
          slowMo: 200
        },
        storageState: 'playwright/.auth/user.json'
      },
      dependencies: ['setup']
    }
  ],
  webServer: process.env.CI
    ? undefined
    : {
        command: 'npm run start',
        url: 'http://localhost:4200',
        reuseExistingServer: true,
        timeout: 2 * 60 * 1000
      }
});
