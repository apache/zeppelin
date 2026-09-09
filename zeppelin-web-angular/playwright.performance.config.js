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

const { defineConfig } = require('@playwright/test');
const baseURL = process.env.PLAYWRIGHT_BASE_URL;
if (!baseURL || process.env.PERF_NOTEBOOK_DISPOSABLE !== '1') {
  throw new Error('Set PLAYWRIGHT_BASE_URL and PERF_NOTEBOOK_DISPOSABLE=1 for an isolated local Zeppelin backend');
}
const target = new URL(baseURL);
if (
  !['localhost', '127.0.0.1', '[::1]'].includes(target.hostname) ||
  target.protocol !== 'http:' ||
  target.username ||
  target.password ||
  target.search ||
  target.hash ||
  target.pathname !== '/'
) {
  throw new Error('The benchmark requires an explicit loopback HTTP origin serving npm run build output');
}
const standard = require('./playwright.config');
module.exports = defineConfig({
  ...standard,
  testMatch: /performance\/notebook-route-baseline\.spec\.ts/,
  testIgnore: [],
  globalSetup: undefined,
  globalTeardown: undefined,
  webServer: undefined,
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 30 * 60 * 1000,
  reporter: 'list',
  use: { ...standard.use, trace: 'off', video: 'off', screenshot: 'off', serviceWorkers: 'block' },
  projects: standard.projects.filter(project => ['setup', 'chromium'].includes(project.name))
});
