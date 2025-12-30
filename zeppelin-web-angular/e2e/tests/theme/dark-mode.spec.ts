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
import { DarkModePage } from '../../models/dark-mode-page';
import { addPageAnnotationBeforeEach, performLoginIfRequired, waitForZeppelinReady, PAGES } from '../../utils';

test.describe('Dark Mode Theme Switching', () => {
  addPageAnnotationBeforeEach(PAGES.SHARE.THEME_TOGGLE);
  let darkModePage: DarkModePage;

  test.beforeEach(async ({ page }) => {
    darkModePage = new DarkModePage(page);
    await page.goto('/#/');
    await waitForZeppelinReady(page);

    // Handle authentication if shiro.ini exists
    await performLoginIfRequired(page);

    // Ensure a clean localStorage for each test
    await darkModePage.clearLocalStorage();
  });

  test('Scenario: User can switch to dark mode and persistence is maintained', async ({ page, browserName }) => {
    // GIVEN: User is on the main page, which starts in 'system' mode by default (localStorage cleared).
    await test.step('GIVEN the page starts in system mode', async () => {
      await darkModePage.assertSystemTheme(); // Robot icon for system theme
    });

    // WHEN: Explicitly set theme to light mode for the rest of the test.
    await test.step('WHEN the user explicitly sets theme to light mode', async () => {
      await darkModePage.setThemeInLocalStorage('light');
      await page.waitForTimeout(500);
      if (browserName === 'webkit') {
        const currentUrl = page.url();
        await page.goto(currentUrl, { waitUntil: 'load' });
      } else {
        page.reload();
      }
      await waitForZeppelinReady(page);
      await darkModePage.assertLightTheme(); // Now it should be light mode with sun icon
    });

    // WHEN: User switches to dark mode by setting localStorage and reloading.
    await test.step('WHEN the user explicitly sets theme to dark mode', async () => {
      await darkModePage.setThemeInLocalStorage('dark');
      await page.waitForTimeout(500);
      if (browserName === 'webkit') {
        const currentUrl = page.url();
        await page.goto(currentUrl, { waitUntil: 'load' });
      } else {
        page.reload();
      }
      await waitForZeppelinReady(page);
      await darkModePage.assertDarkTheme();
    });

    // AND: User clicks the toggle again to switch back to light mode.
    await test.step('AND the user clicks the toggle to switch back to light mode', async () => {
      await darkModePage.toggleTheme();
    });

    // THEN: The theme switches to system mode.
    await test.step('THEN the theme switches to system mode', async () => {
      await darkModePage.assertSystemTheme();
    });
  });

  test('Scenario: System Theme and Local Storage Interaction', async ({ page }) => {
    // Ensure localStorage is clear for each sub-scenario
    await darkModePage.clearLocalStorage();

    await test.step('GIVEN: No localStorage, System preference is Light', async () => {
      await page.emulateMedia({ colorScheme: 'light' });
      await page.goto('/');
      await waitForZeppelinReady(page);
      // When no explicit theme is set, it defaults to 'system' mode
      // Even in system mode with light preference, the icon should be robot
      await expect(darkModePage.rootElement).toHaveClass(/light/);
      await expect(darkModePage.rootElement).toHaveAttribute('data-theme', 'light');
      await darkModePage.assertSystemTheme(); // Should show robot icon
    });

    await test.step('GIVEN: No localStorage, System preference is Dark (initial system state)', async () => {
      await darkModePage.setThemeInLocalStorage('system');
      await page.goto('/');
      await waitForZeppelinReady(page);
      await darkModePage.assertSystemTheme(); // Robot icon for system theme
    });

    await test.step("GIVEN: localStorage is 'dark', System preference is Light", async () => {
      await darkModePage.setThemeInLocalStorage('dark');
      await page.emulateMedia({ colorScheme: 'light' });
      await page.goto('/');
      await waitForZeppelinReady(page);
      await darkModePage.assertDarkTheme(); // localStorage should override system
    });

    await test.step("GIVEN: localStorage is 'system', THEN: Emulate system preference change to Light", async () => {
      await darkModePage.setThemeInLocalStorage('system');
      await page.emulateMedia({ colorScheme: 'light' });
      await page.goto('/');
      await waitForZeppelinReady(page);
      await expect(darkModePage.rootElement).toHaveClass(/light/);
      await expect(darkModePage.rootElement).toHaveAttribute('data-theme', 'light');
      await darkModePage.assertSystemTheme(); // Robot icon for system theme
    });

    await test.step("GIVEN: localStorage is 'system', THEN: Emulate system preference change to Dark", async () => {
      await darkModePage.setThemeInLocalStorage('system');
      await page.emulateMedia({ colorScheme: 'dark' });
      await page.goto('/');
      await waitForZeppelinReady(page);
      await expect(darkModePage.rootElement).toHaveClass(/dark/);
      await expect(darkModePage.rootElement).toHaveAttribute('data-theme', 'dark');
      await darkModePage.assertSystemTheme(); // Robot icon for system theme
    });
  });
});
