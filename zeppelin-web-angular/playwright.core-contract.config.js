/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

const { defineConfig, devices } = require('@playwright/test');
const { baseConfig } = require('./playwright.shared');
const { randomUUID } = require('node:crypto');
const { tmpdir } = require('node:os');
const path = require('node:path');

const live = process.env.ZEPPELIN_E2E_LIVE_CAPTURE === '1';
if (live && !process.env.PLAYWRIGHT_BASE_URL) {
  throw new Error('Live capture requires PLAYWRIGHT_BASE_URL pointing to the isolated capture server');
}

// Workers reload the config, so inherit one run directory without creating it on import.
const runDir = (process.env.ZEPPELIN_CORE_CONTRACT_RUN_DIR ||= path.join(
  tmpdir(),
  `zeppelin-core-contract-${randomUUID()}`
));
const authStatePath = path.join(runDir, '.auth', 'user.json');

module.exports = defineConfig({
  ...baseConfig,
  // Shared hooks can delete unrelated notebooks or contact a server during offline runs.
  globalSetup: undefined,
  globalTeardown: undefined,
  webServer: undefined,
  outputDir: path.join(runDir, 'results'),
  workers: 1,
  retries: 0,
  reporter: 'list',
  use: {
    ...baseConfig.use,
    baseURL: live ? process.env.PLAYWRIGHT_BASE_URL : 'http://fixture.test'
  },
  projects: [
    ...(live ? [{ name: 'setup', testMatch: /global\.setup\.ts/, metadata: { authStatePath } }] : []),
    {
      name: 'chromium',
      testMatch: '**/tests/notebook/core-contract/capture-fixtures.spec.ts',
      grep: live ? /@live/ : undefined,
      grepInvert: live ? undefined : /@live/,
      use: {
        ...devices['Desktop Chrome'],
        ...(live ? { storageState: authStatePath } : {})
      },
      dependencies: live ? ['setup'] : []
    }
  ]
});
