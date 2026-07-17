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

// Classic UI suite (e2e/tests/classic/): needs a Zeppelin server built with -Pweb-classic
// (:4200 does not serve /classic, hence no webServer). Run with: npm run e2e:classic
process.env.PLAYWRIGHT_BASE_URL = process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:8080';

module.exports = defineConfig({
  ...baseConfig,
  testMatch: /tests\/classic\/.*\.spec\.ts/,
  outputDir: 'test-results-classic',
  // Classic pages are outside the PAGES coverage denominator, so no coverage reporter.
  reporter: [
    [!!process.env.CI ? 'github' : 'list'],
    ['html', { outputFolder: 'playwright-report-classic', open: !!process.env.CI ? 'never' : 'always' }]
  ],
  use: {
    ...baseConfig.use,
    baseURL: process.env.PLAYWRIGHT_BASE_URL
  },
  projects: [
    {
      name: 'setup',
      testMatch: /global\.setup\.ts/
    },
    {
      name: 'classic',
      use: {
        ...devices['Desktop Chrome'],
        storageState: 'playwright/.auth/user.json'
      },
      dependencies: ['setup']
    }
  ]
});
