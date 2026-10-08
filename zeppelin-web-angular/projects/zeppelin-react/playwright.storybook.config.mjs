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

import { defineConfig } from '@playwright/test';

const port = Number(process.env.STORYBOOK_PORT ?? 6007);
if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Invalid STORYBOOK_PORT');

export default defineConfig({
  testDir: './test/storybook',
  fullyParallel: true,
  workers: 3,
  retries: 0,
  timeout: 45_000,
  reporter: [['list'], ['html', { outputFolder: 'storybook-report', open: 'never' }]],
  outputDir: 'storybook-test-results',
  use: {
    channel: process.env.STORYBOOK_BROWSER_CHANNEL,
    baseURL: `http://127.0.0.1:${port}`,
    viewport: { width: 900, height: 900 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  },
  webServer: {
    command: `python3 -m http.server ${port} --bind 127.0.0.1 --directory storybook-static`,
    url: `http://127.0.0.1:${port}/index.json`,
    reuseExistingServer: false
  }
});
