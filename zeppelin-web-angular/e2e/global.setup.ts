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

import * as fs from 'fs';
import * as path from 'path';
import { test as setup, expect } from '@playwright/test';
import { LoginTestUtil } from './models/login-page.util';
import { performLoginIfRequired, waitForZeppelinReady } from './utils';

// Resolved against the Playwright project rootDir (zeppelin-web-angular/).
// Must match the `storageState` value declared for browser projects in playwright.config.js.
export const STORAGE_STATE = path.join('playwright', '.auth', 'user.json');

setup('authenticate', async ({ page }) => {
  fs.mkdirSync(path.dirname(STORAGE_STATE), { recursive: true });

  const isShiroEnabled = await LoginTestUtil.isShiroEnabled();
  if (!isShiroEnabled) {
    // Auth variant disabled — write an empty storage state so dependent projects load,
    // then exit. This keeps the setup-project pattern uniform across CI matrix variants.
    await page.context().storageState({ path: STORAGE_STATE });
    return;
  }

  await page.goto('/');
  await waitForZeppelinReady(page);

  await performLoginIfRequired(page);

  // Verify we are authenticated. Don't rely on performLoginIfRequired's return value —
  // it returns false both for "no work to do" and "login attempt failed".
  await expect(page.locator('zeppelin-login')).toBeHidden({ timeout: 30000 });
  await expect(page.getByRole('heading', { name: 'Welcome to Zeppelin!' })).toBeVisible({ timeout: 30000 });

  await page.context().storageState({ path: STORAGE_STATE });
});
