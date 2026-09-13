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

import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const root = fileURLToPath(new URL('../../', import.meta.url));
const scripts = JSON.parse(readFileSync(new URL('../../package.json', import.meta.url))).scripts;

function loadRunner(live, regular = false) {
  const command = scripts[live ? 'e2e:core-contract:live' : 'e2e:core-contract'];
  const config = regular ? 'playwright.config.js' : command.match(/--config[= ]([^ ]+)/)?.[1] || 'playwright.config.js';
  const env = {
    ...process.env,
    ZEPPELIN_E2E_CORE_CONTRACT: '1',
    PLAYWRIGHT_BASE_URL: 'http://127.0.0.1:19999',
    ZEPPELIN_E2E_SHIRO_INI: '/nonexistent/capture-shiro.ini'
  };
  delete env.CI;
  if (regular) env.ZEPPELIN_CORE_CONTRACT_RUN_DIR = '/unused-capture-run';
  else delete env.ZEPPELIN_CORE_CONTRACT_RUN_DIR;
  delete env.ZEPPELIN_E2E_LIVE_CAPTURE;
  if (live) env.ZEPPELIN_E2E_LIVE_CAPTURE = '1';
  return JSON.parse(
    execFileSync(
      process.execPath,
      [
        '-e',
        `require.extensions['.ts'] = require.extensions['.js']; console.log(JSON.stringify(require('./${config}')))`
      ],
      { cwd: root, env, encoding: 'utf8' }
    )
  );
}

test('offline runner cannot authenticate, load saved credentials, clean notebooks or start the app', () => {
  const config = loadRunner(false);
  assert.equal(config.globalSetup, undefined);
  assert.equal(config.globalTeardown, undefined);
  assert.equal(config.webServer, undefined);
  assert.equal(config.use?.storageState, undefined);
  assert.equal(config.use.baseURL, 'http://fixture.test');
  assert.equal(config.projects.length, 1);
  assert.equal(existsSync(path.dirname(config.outputDir)), false);
  assert.notEqual(path.resolve(config.outputDir), path.join(root, 'test-results'));
  for (const project of config.projects) {
    assert.equal(project.use?.storageState, undefined);
    assert.deepEqual(project.dependencies || [], []);
  }
});

test('live runner preserves authentication against the explicit server without broad notebook cleanup', () => {
  const config = loadRunner(true);
  assert.equal(config.use.baseURL, 'http://127.0.0.1:19999');
  assert.equal(config.globalSetup, undefined);
  assert.equal(config.globalTeardown, undefined);
  assert.equal(config.webServer, undefined);
  const chromium = config.projects.find(project => project.name === 'chromium');
  assert.deepEqual(chromium.dependencies, ['setup']);
  const setup = config.projects.find(project => project.name === 'setup');
  assert.ok(path.isAbsolute(chromium.use.storageState));
  assert.equal(setup.metadata.authStatePath, chromium.use.storageState);
  assert.equal(path.dirname(path.dirname(chromium.use.storageState)), path.dirname(config.outputDir));
  assert.notEqual(path.dirname(chromium.use.storageState), config.outputDir);
  assert.notEqual(
    chromium.use.storageState,
    loadRunner(true).projects.find(project => project.name === 'chromium').use.storageState
  );
  assert.equal(existsSync(path.dirname(chromium.use.storageState)), false);
});

// Opt in because this regression launches Chromium; the normal fixture check stays Node-only.
test(
  'live anonymous setup writes isolated auth state and preserves the regular suite snapshot',
  {
    skip: process.env.ZEPPELIN_RUN_AUTH_SETUP_TEST !== '1'
  },
  () => {
    const cwd = mkdtempSync(path.join(tmpdir(), 'zeppelin-auth-regression-'));
    try {
      const sentinel = path.join(cwd, 'playwright/.auth/user.json');
      mkdirSync(path.dirname(sentinel), { recursive: true });
      writeFileSync(sentinel, 'regular-suite-sentinel');
      const configPath = path.join(cwd, 'playwright.config.cjs');
      writeFileSync(
        configPath,
        `
      const config = require(${JSON.stringify(path.join(root, 'playwright.core-contract.config.js'))});
      config.testDir = ${JSON.stringify(path.join(root, 'e2e'))};
      require('node:fs').writeFileSync(${JSON.stringify(path.join(cwd, 'resolved.json'))}, JSON.stringify(config));
      module.exports = config;
    `
      );
      const env = {
        ...process.env,
        ZEPPELIN_E2E_LIVE_CAPTURE: '1',
        PLAYWRIGHT_BASE_URL: 'http://127.0.0.1:19999',
        ZEPPELIN_E2E_SHIRO_INI: path.join(cwd, 'absent-shiro.ini'),
        ZEPPELIN_CORE_CONTRACT_RUN_DIR: path.join(cwd, 'capture-run')
      };
      execFileSync(
        process.execPath,
        [path.join(root, 'node_modules/@playwright/test/cli.js'), 'test', '--config', configPath, '--project=setup'],
        { cwd, env, encoding: 'utf8', timeout: 60000 }
      );
      const config = JSON.parse(readFileSync(path.join(cwd, 'resolved.json')));
      const statePath = config.projects.find(project => project.name === 'chromium').use.storageState;
      assert.equal(readFileSync(sentinel, 'utf8'), 'regular-suite-sentinel');
      assert.deepEqual(JSON.parse(readFileSync(statePath)), { cookies: [], origins: [] });
    } finally {
      rmSync(cwd, { recursive: true, force: true });
    }
  }
);

test('regular suite ignores an inherited capture run directory', () => {
  const config = loadRunner(true, true);
  assert.equal(config.projects.find(project => project.name === 'setup').metadata?.authStatePath, undefined);
  for (const project of config.projects.filter(project => project.name !== 'setup')) {
    assert.equal(project.use.storageState, 'playwright/.auth/user.json');
  }
});
