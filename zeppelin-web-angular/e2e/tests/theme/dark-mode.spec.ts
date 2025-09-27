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
import { ZeppelinHelper } from '../../helper';
import { ThemePage } from '../../models/theme.page';
import { addPageAnnotationBeforeEach, PAGES } from '../../utils';

test.describe('Dark Mode Theme Switching', () => {
  addPageAnnotationBeforeEach(PAGES.SHARE.THEME_TOGGLE);
  let zeppelinHelper: ZeppelinHelper;
  let themePage: ThemePage;

  test.beforeEach(async ({ page }) => {
    zeppelinHelper = new ZeppelinHelper(page);
    themePage = new ThemePage(page);
    await page.goto('/', { waitUntil: 'load' });
    await expect(page).toHaveURL(/^http:\/\/localhost:4200/);
    await zeppelinHelper.waitForZeppelinReady();
    // Ensure a clean localStorage for each test
    await themePage.clearLocalStorage();
  });

  test('Scenario: User can switch to dark mode and persistence is maintained', async ({ page }) => {
    // GIVEN: User is on the main page, which starts in 'system' mode by default (localStorage cleared).
    await test.step('GIVEN the page starts in system mode', async () => {
      await themePage.assertSystemTheme(); // Robot icon for system theme
    });

    // WHEN: Explicitly set theme to light mode for the rest of the test.
    await test.step('WHEN the user explicitly sets theme to light mode', async () => {
      await themePage.setThemeInLocalStorage('light');
      await page.reload();
      await zeppelinHelper.waitForZeppelinReady();
      await themePage.assertLightTheme(); // Now it should be light mode with sun icon
    });

    // WHEN: User switches to dark mode by setting localStorage and reloading.
    await test.step('WHEN the user switches to dark mode', async () => {
      await themePage.setThemeInLocalStorage('dark');
      await page.reload();
      await zeppelinHelper.waitForZeppelinReady();
    });

    // THEN: The theme changes to dark mode.
    await test.step('THEN the page switches to dark mode', async () => {
      await themePage.assertDarkTheme();
    });

    // AND: User refreshes the page.
    await test.step('AND the user refreshes the page', async () => {
      await page.reload();
      await zeppelinHelper.waitForZeppelinReady();
    });

    // THEN: Dark mode is maintained after refresh.
    await test.step('THEN dark mode is maintained after refresh', async () => {
      await themePage.assertDarkTheme();
    });

    // AND: User clicks the toggle again to switch back to light mode.
    await test.step('AND the user clicks the toggle to switch back to light mode', async () => {
      await themePage.toggleTheme();
    });

    // THEN: The theme reverts to light mode.
    await test.step('THEN the page reverts to light mode', async () => {
      await themePage.assertLightTheme();
    });
  });

  test('Scenario: System Theme and Local Storage Interaction', async ({ page, context }) => {
    // Ensure localStorage is clear for each sub-scenario
    await themePage.clearLocalStorage();

    await test.step('GIVEN: No localStorage, System preference is Light', async () => {
      await page.emulateMedia({ colorScheme: 'light' });
      await page.goto('/', { waitUntil: 'load' });
      await zeppelinHelper.waitForZeppelinReady();
      // When no explicit theme is set, it defaults to 'system' mode
      // Even in system mode with light preference, the icon should be robot
      await expect(themePage.rootElement).toHaveClass(/light/);
      await expect(themePage.rootElement).toHaveAttribute('data-theme', 'light');
      await themePage.assertSystemTheme(); // Should show robot icon
    });

    await test.step('GIVEN: No localStorage, System preference is Dark (initial system state)', async () => {
      await themePage.setThemeInLocalStorage('system');
      await page.goto('/', { waitUntil: 'load' });
      await zeppelinHelper.waitForZeppelinReady();
      await themePage.assertSystemTheme(); // Robot icon for system theme
    });

    await test.step("GIVEN: localStorage is 'dark', System preference is Light", async () => {
      await themePage.setThemeInLocalStorage('dark');
      await page.emulateMedia({ colorScheme: 'light' });
      await page.goto('/', { waitUntil: 'load' });
      await zeppelinHelper.waitForZeppelinReady();
      await themePage.assertDarkTheme(); // localStorage should override system
    });

    await test.step("GIVEN: localStorage is 'system', THEN: Emulate system preference change to Light", async () => {
      await themePage.setThemeInLocalStorage('system');
      await page.goto('/', { waitUntil: 'load' });
      await zeppelinHelper.waitForZeppelinReady();
      await page.emulateMedia({ colorScheme: 'light' });
      await page.waitForSelector('html.light', { timeout: 15000 });
      await expect(themePage.rootElement).toHaveClass(/light/);
      await expect(themePage.rootElement).toHaveAttribute('data-theme', 'light');
      await themePage.assertSystemTheme(); // Robot icon for system theme
    });

    await test.step("GIVEN: localStorage is 'system', THEN: Emulate system preference change to Dark", async () => {
      await themePage.setThemeInLocalStorage('system');
      await page.goto('/', { waitUntil: 'load' });
      await zeppelinHelper.waitForZeppelinReady();
      await page.emulateMedia({ colorScheme: 'dark' });
      await page.waitForSelector('html.dark', { timeout: 15000 });
      await expect(themePage.rootElement).toHaveClass(/dark/);
      await expect(themePage.rootElement).toHaveAttribute('data-theme', 'dark');
      await themePage.assertSystemTheme(); // Robot icon for system theme
    });
  });
});
