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

import { expect, test } from '@playwright/test';
import { BasePage } from '../models/base-page';
import { LoginPage } from '../models/login-page';
import { LoginTestUtil, TestCredentials } from '../models/login-page.util';
import { addPageAnnotationBeforeEach, waitForZeppelinReady, PAGES } from '../utils';

test.describe('Zeppelin App Component', () => {
  addPageAnnotationBeforeEach(PAGES.APP);
  let basePage: BasePage;

  test.beforeEach(async ({ page }) => {
    basePage = new BasePage(page);

    await page.goto('/', { waitUntil: 'load' });
    await waitForZeppelinReady(page);
  });

  test('should have correct component selector and structure', async ({ page }) => {
    await basePage.waitForPageLoad();

    await waitForZeppelinReady(page);

    // Verify router-outlet is inside zeppelin-root (use first to avoid multiple elements)
    const zeppelinRoot = page.locator('zeppelin-root');

    // Verify routing has activated by checking that actual content is rendered inside the workspace
    await expect(zeppelinRoot.locator('zeppelin-workspace')).toBeVisible();

    // Check for loading spinner
    const loadingSpinner = zeppelinRoot.locator('zeppelin-spin').filter({ hasText: 'Getting Ticket Data' });
    const logoutSpinner = zeppelinRoot.locator('zeppelin-spin').filter({ hasText: 'Logging out' });

    // After waitForZeppelinReady, both spinners must be gone
    await expect(loadingSpinner).toHaveCount(0);
    await expect(logoutSpinner).toHaveCount(0);
  });

  test('should have proper page title', async ({ page }) => {
    await expect(page).toHaveTitle(/Zeppelin/);
  });

  test('should display home content after loading', async ({ page }) => {
    await waitForZeppelinReady(page);
    // After the `beforeEach` hook, which handles login, the workspace should be visible.
    await expect(basePage.zeppelinWorkspace).toBeVisible();
    // Verify the home page content is rendered (not just a blank shell)
    await expect(basePage.zeppelinWorkspace.locator('zeppelin-home')).toBeVisible();
  });

  test('should hide loading spinner after navigation', async ({ page }) => {
    await waitForZeppelinReady(page);

    await page.goto('/', { waitUntil: 'load', timeout: 10000 });
    await waitForZeppelinReady(page);

    // After the app is ready, the loading spinner must be hidden
    const loadingSpinner = page.locator('zeppelin-spin').filter({ hasText: 'Getting Ticket Data' });
    await expect(loadingSpinner).toBeHidden();
  });

  test('should hide loading spinner after page reload', async ({ page }) => {
    await basePage.waitForPageLoad();

    // Test that loading$ observable works correctly
    const loadingSpinner = page.locator('zeppelin-spin').filter({ hasText: 'Getting Ticket Data' });

    // Reload page to trigger loading state
    await page.reload({ waitUntil: 'load' });

    // If the spinner is briefly visible during reload, it will resolve; just wait for ready

    // Wait for loading to complete
    await waitForZeppelinReady(page);
    await expect(loadingSpinner).toBeHidden();
  });

  test('should show logout spinner when logging out', async ({ page, browser, baseURL }) => {
    await waitForZeppelinReady(page);

    // Only test logout flow for authenticated (non-anonymous) users — skip before any assertions
    const statusElement = page.locator('.status');
    await expect(statusElement).toBeVisible();
    const statusText = await statusElement.textContent();
    test.skip(statusText?.includes('anonymous') ?? false, 'Logout spinner only applies to authenticated users');

    const credentials = await LoginTestUtil.getTestCredentials();
    const logoutUser = getIsolatedLogoutUser(credentials);
    test.skip(!logoutUser, 'No non-shared logout test user available');

    // The default auth storage state is shared by the whole parallel suite. Logging
    // out from that shared user invalidates the server-side Shiro session for many
    // still-running tests, so exercise logout from a throwaway user/session instead.
    const context = await browser.newContext({
      baseURL: baseURL ?? 'http://localhost:4200',
      storageState: { cookies: [], origins: [] }
    });

    try {
      const logoutPage = await context.newPage();
      const loginPage = new LoginPage(logoutPage);
      await loginPage.navigate();
      await loginPage.login(logoutUser!.username, logoutUser!.password);
      await logoutPage.waitForURL('/#/', { timeout: 30000 });
      await waitForZeppelinReady(logoutPage);

      const isolatedStatusElement = logoutPage.locator('.status');
      const logoutSpinner = logoutPage.locator('zeppelin-spin').filter({ hasText: 'Logging out' });

      await expect(logoutSpinner).toBeHidden();

      await isolatedStatusElement.click();
      const logoutButton = logoutPage.getByRole('link', { name: 'Logout' });

      // If the dropdown has no Logout link, auth is not configured — skip gracefully
      const logoutCount = await logoutButton.count();
      test.skip(logoutCount === 0, 'Logout option not available — auth not configured in this environment');

      await logoutButton.click();

      // `toBeVisible` can resolve briefly before the spinner mounts then misses the
      // narrow visibility window. `toHaveCount(1)` polls the DOM for the spinner's
      // presence which is more tolerant of the transient mount.
      await expect(logoutSpinner).toHaveCount(1, { timeout: 10000 });
      await expect(logoutSpinner).toContainText('Logging out ...');
    } finally {
      await context.close();
    }
  });

  test('should maintain component integrity during navigation', async ({ page }) => {
    await waitForZeppelinReady(page);

    // Navigate to different pages and ensure component remains intact
    const testPaths = ['/#/notebook', '/#/jobmanager', '/#/configuration'];

    for (const path of testPaths) {
      await page.goto(path, { waitUntil: 'load', timeout: 10000 });
      await waitForZeppelinReady(page);

      // Workspace must render visible content after each navigation (confirms Angular didn't unmount the root component)
      await expect(page.locator('zeppelin-workspace')).toBeVisible();
    }

    // Return to home
    await page.goto('/', { waitUntil: 'load' });
    await waitForZeppelinReady(page);
  });
});

const getIsolatedLogoutUser = (credentials: Record<string, TestCredentials>): TestCredentials | undefined =>
  Object.values(credentials).find(
    credential => credential.username && credential.password && credential.username !== 'user1'
  );
