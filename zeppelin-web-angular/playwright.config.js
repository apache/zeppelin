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

// https://playwright.dev/docs/test-configuration
module.exports = defineConfig({
  testDir: './e2e',
  globalSetup: require.resolve('./e2e/global-setup'),
  globalTeardown: require.resolve('./e2e/global-teardown'),
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: 1,
  workers: 10,
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
    baseURL: process.env.CI ? 'http://localhost:8080' : 'http://localhost:4200',
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
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], permissions: ['clipboard-read', 'clipboard-write'] }
    },
    {
      name: 'Google Chrome',
      use: { ...devices['Desktop Chrome'], channel: 'chrome', permissions: ['clipboard-read', 'clipboard-write'] }
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] }
    },
    {
      name: 'webkit',
      use: {
        ...devices['Desktop Safari'],
        launchOptions: {
          slowMo: 200
        }
      }
    },
    {
      name: 'Microsoft Edge',
      use: { ...devices['Desktop Edge'], channel: 'msedge', permissions: ['clipboard-read', 'clipboard-write'] }
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
