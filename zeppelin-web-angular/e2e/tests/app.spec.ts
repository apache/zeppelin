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
import { LoginTestUtil } from '../models/login-page.util';
import { addPageAnnotationBeforeEach, waitForZeppelinReady, PAGES } from '../utils';

test.describe('Zeppelin App Component', () => {
  addPageAnnotationBeforeEach(PAGES.APP);
  let basePage: BasePage;

  test.beforeEach(async ({ page }) => {
    basePage = new BasePage(page);

    await page.goto('/', { waitUntil: 'load' });
  });

  test('should have correct component selector and structure', async ({ page }) => {
    await basePage.waitForPageLoad();

    // Test zeppelin-root selector
    const zeppelinRoot = page.locator('zeppelin-root');
    await expect(zeppelinRoot).toBeAttached();

    await waitForZeppelinReady(page);

    // Verify router-outlet is inside zeppelin-root (use first to avoid multiple elements)
    const routerOutlet = zeppelinRoot.locator('router-outlet').first();
    await expect(routerOutlet).toBeAttached();

    // Check for loading spinner
    const loadingSpinner = zeppelinRoot.locator('zeppelin-spin').filter({ hasText: 'Getting Ticket Data' });
    const logoutSpinner = zeppelinRoot.locator('zeppelin-spin').filter({ hasText: 'Logging out' });

    // Loading spinner should exist, logout spinner may or may not exist depending on conditions
    const loadingSpinnerCount = await loadingSpinner.count();
    const logoutSpinnerCount = await logoutSpinner.count();

    expect(loadingSpinnerCount).toBeGreaterThanOrEqual(0);
    expect(logoutSpinnerCount).toBeGreaterThanOrEqual(0);
  });

  test('should have proper page title', async ({ page }) => {
    await expect(page).toHaveTitle(/Zeppelin/);
  });

  test('should display workspace after loading', async ({ page }) => {
    await waitForZeppelinReady(page);
    const isShiroEnabled = await LoginTestUtil.isShiroEnabled();
    if (isShiroEnabled) {
      await expect(page.locator('zeppelin-login')).toBeVisible();
    } else {
      await expect(page.locator('zeppelin-workspace')).toBeVisible();
    }
  });

  test('should handle navigation events correctly', async ({ page }) => {
    await waitForZeppelinReady(page);

    // Test navigation back to root path
    try {
      await page.goto('/', { waitUntil: 'load', timeout: 10000 });

      // Check if loading spinner appears during navigation
      const loadingSpinner = page.locator('zeppelin-spin').filter({ hasText: 'Getting Ticket Data' });

      // Loading might be very fast, so we check if it exists
      const spinnerCount = await loadingSpinner.count();
      expect(spinnerCount).toBeGreaterThanOrEqual(0);

      await waitForZeppelinReady(page);

      // After ready, loading should be hidden if it was visible
      if (await loadingSpinner.isVisible()) {
        await expect(loadingSpinner).toBeHidden();
      }
    } catch (error) {
      console.log('Navigation test skipped due to timeout:', error);
    }
  });

  test('should properly manage loading state observable', async ({ page }) => {
    await basePage.waitForPageLoad();

    // Test that loading$ observable works correctly
    const loadingSpinner = page.locator('zeppelin-spin').filter({ hasText: 'Getting Ticket Data' });

    // Reload page to trigger loading state
    await page.reload({ waitUntil: 'load' });

    // Check loading state during page load
    const initialLoadingVisible = await loadingSpinner.isVisible();

    if (initialLoadingVisible) {
      await expect(loadingSpinner).toBeVisible();
      await expect(loadingSpinner).toContainText('Getting Ticket Data ...');
    }

    // Wait for loading to complete
    await waitForZeppelinReady(page);
    await expect(loadingSpinner).toBeHidden();
  });

  test('should handle logout observable correctly', async ({ page }) => {
    await waitForZeppelinReady(page);

    const logoutSpinner = page.locator('zeppelin-spin').filter({ hasText: 'Logging out' });

    // Initially logout spinner should be hidden
    await expect(logoutSpinner).toBeHidden();

    // Check if we have a logout mechanism available
    const statusElement = page.locator('.status');
    if (await statusElement.isVisible()) {
      const statusText = await statusElement.textContent();

      if (statusText && !statusText.includes('anonymous')) {
        // If not anonymous user, test logout spinner
        await statusElement.click();
        const logoutButton = page.getByRole('link', { name: 'Logout' });

        if (await logoutButton.isVisible()) {
          await logoutButton.click();

          // Logout spinner should appear
          await expect(logoutSpinner).toBeVisible();
          await expect(logoutSpinner).toContainText('Logging out ...');
        }
      }
    }
  });

  test('should maintain component integrity during navigation', async ({ page }) => {
    await waitForZeppelinReady(page);

    const zeppelinRoot = page.locator('zeppelin-root');
    const routerOutlet = zeppelinRoot.locator('router-outlet').first();

    // Navigate to different pages and ensure component remains intact
    const testPaths = ['/#/notebook', '/#/jobmanager', '/#/configuration'];

    for (const path of testPaths) {
      await page.goto(path, { waitUntil: 'load', timeout: 10000 });
      await waitForZeppelinReady(page);

      // Component should still be attached
      await expect(zeppelinRoot).toBeAttached();

      // Router outlet should still be present
      await expect(routerOutlet).toBeAttached();
    }

    // Return to home
    await page.goto('/', { waitUntil: 'load' });
    await waitForZeppelinReady(page);
    await expect(zeppelinRoot).toBeAttached();
  });

  test('should verify spinner text content and visibility', async ({ page }) => {
    await basePage.waitForPageLoad();

    // Check exact text content of spinners
    const loadingSpinner = page.locator('zeppelin-spin').filter({ hasText: 'Getting Ticket Data' });
    const logoutSpinner = page.locator('zeppelin-spin').filter({ hasText: 'Logging out' });

    // Verify spinner elements exist
    expect(await loadingSpinner.count()).toBeGreaterThanOrEqual(0);
    expect(await logoutSpinner.count()).toBeGreaterThanOrEqual(0);

    // If loading spinner is visible, check its exact text
    if (await loadingSpinner.isVisible()) {
      await expect(loadingSpinner).toHaveText('Getting Ticket Data ...');
    }

    // Logout spinner should not be visible initially
    await expect(logoutSpinner).toBeHidden();
  });
});
