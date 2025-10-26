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
import { ThemePage } from '../../models/theme.page';
import { addPageAnnotationBeforeEach, performLoginIfRequired, waitForZeppelinReady, PAGES } from '../../utils';

test.describe('Dark Mode Theme Switching', () => {
  addPageAnnotationBeforeEach(PAGES.SHARE.THEME_TOGGLE);
  let themePage: ThemePage;

  test.beforeEach(async ({ page, browserName }) => {
    // TODO: This crash occurs only on WebKit. The root cause should be investigated and addressed.
    if (browserName === 'webkit') {
      test.skip();
    }
    themePage = new ThemePage(page);
    await page.goto('/');
    await waitForZeppelinReady(page);

    // Handle authentication if shiro.ini exists
    await performLoginIfRequired(page);

    // Ensure a clean localStorage for each test
    await themePage.clearLocalStorage();
  });

  test('Scenario: User can switch to dark mode and persistence is maintained', async ({ page, context }) => {
    let currentPage = page;

    // GIVEN: User is on the main page, which starts in 'system' mode by default (localStorage cleared).
    await test.step('GIVEN the page starts in system mode', async () => {
      await themePage.assertSystemTheme(); // Robot icon for system theme
    });

    // WHEN: Explicitly set theme to light mode for the rest of the test.
    await test.step('WHEN the user explicitly sets theme to light mode', async () => {
      await themePage.setThemeInLocalStorage('light');
      await page.reload();
      await waitForZeppelinReady(page);
      await themePage.assertLightTheme(); // Now it should be light mode with sun icon
    });

    // WHEN: User switches to dark mode by setting localStorage and reloading.
    await test.step('WHEN the user switches to dark mode', async () => {
      await themePage.setThemeInLocalStorage('dark');
      const newPage = await context.newPage();
      await newPage.goto(currentPage.url(), { waitUntil: 'networkidle' });
      await waitForZeppelinReady(newPage);

      // Update themePage to use newPage and verify dark mode
      themePage = new ThemePage(newPage);
      currentPage = newPage;
      await themePage.assertDarkTheme();
    });

    // AND: User refreshes the page.
    await test.step('AND the user refreshes the page', async () => {
      await currentPage.reload();
      await waitForZeppelinReady(currentPage);
    });

    // THEN: Dark mode is maintained after refresh.
    await test.step('THEN dark mode is maintained after refresh', async () => {
      await themePage.assertDarkTheme();
    });

    // AND: User clicks the toggle again to switch back to light mode.
    await test.step('AND the user clicks the toggle to switch back to light mode', async () => {
      await themePage.toggleTheme();
    });

    // THEN: The theme switches to system mode.
    await test.step('THEN the theme switches to system mode', async () => {
      await themePage.assertSystemTheme();
    });
  });

  test('Scenario: System Theme and Local Storage Interaction', async ({ page, context }) => {
    // Ensure localStorage is clear for each sub-scenario
    await themePage.clearLocalStorage();

    await test.step('GIVEN: No localStorage, System preference is Light', async () => {
      await page.emulateMedia({ colorScheme: 'light' });
      await page.goto('/');
      await waitForZeppelinReady(page);
      // When no explicit theme is set, it defaults to 'system' mode
      // Even in system mode with light preference, the icon should be robot
      await expect(themePage.rootElement).toHaveClass(/light/);
      await expect(themePage.rootElement).toHaveAttribute('data-theme', 'light');
      await themePage.assertSystemTheme(); // Should show robot icon
    });

    await test.step('GIVEN: No localStorage, System preference is Dark (initial system state)', async () => {
      await themePage.setThemeInLocalStorage('system');
      await page.goto('/');
      await waitForZeppelinReady(page);
      await themePage.assertSystemTheme(); // Robot icon for system theme
    });

    await test.step("GIVEN: localStorage is 'dark', System preference is Light", async () => {
      await themePage.setThemeInLocalStorage('dark');
      await page.emulateMedia({ colorScheme: 'light' });
      await page.goto('/');
      await waitForZeppelinReady(page);
      await themePage.assertDarkTheme(); // localStorage should override system
    });

    await test.step("GIVEN: localStorage is 'system', THEN: Emulate system preference change to Light", async () => {
      await themePage.setThemeInLocalStorage('system');
      await page.emulateMedia({ colorScheme: 'light' });
      await page.goto('/');
      await waitForZeppelinReady(page);
      await expect(themePage.rootElement).toHaveClass(/light/);
      await expect(themePage.rootElement).toHaveAttribute('data-theme', 'light');
      await themePage.assertSystemTheme(); // Robot icon for system theme
    });

    await test.step("GIVEN: localStorage is 'system', THEN: Emulate system preference change to Dark", async () => {
      await themePage.setThemeInLocalStorage('system');
      await page.emulateMedia({ colorScheme: 'dark' });
      await page.goto('/');
      await waitForZeppelinReady(page);
      await expect(themePage.rootElement).toHaveClass(/dark/);
      await expect(themePage.rootElement).toHaveAttribute('data-theme', 'dark');
      await themePage.assertSystemTheme(); // Robot icon for system theme
    });
  });
});
