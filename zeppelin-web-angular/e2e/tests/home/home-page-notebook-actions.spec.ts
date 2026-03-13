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
import { HomePage } from '../../models/home-page';
import { addPageAnnotationBeforeEach, performLoginIfRequired, waitForZeppelinReady, PAGES } from '../../utils';

addPageAnnotationBeforeEach(PAGES.WORKSPACE.HOME);

test.describe('Home Page Notebook Actions', () => {
  let homePage: HomePage;

  test.beforeEach(async ({ page }) => {
    homePage = new HomePage(page);
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
  });

  test.describe('Given notebook list is displayed', () => {
    test('When page loads Then should show notebook actions', async () => {
      await expect(homePage.nodeList.createNewNoteLink).toBeVisible();
      await expect(homePage.nodeList.importNoteLink).toBeVisible();
      await expect(homePage.nodeList.filterInput).toBeVisible();
    });

    test('When refresh button is clicked Then should keep refresh icon visible', async ({ page }) => {
      const refreshButton = page.locator('a.refresh-note');
      const refreshIcon = page.locator('a.refresh-note i[nz-icon]');

      await expect(refreshButton).toBeVisible();
      await expect(refreshIcon).toBeVisible();

      await homePage.clickRefreshNotes();

      await expect(refreshIcon).toBeVisible();
    });

    test('When filter is used Then should filter notebook list', async ({ page }) => {
      test.skip(true, 'ZEPPELIN-6386: Notebook search filter in the New UI is too slow — re-enable when fixed');
      await homePage.filterNotes('test');
      await page.waitForLoadState('networkidle', { timeout: 15000 });
      const filteredResults = await page.locator('nz-tree .node').count();
      expect(filteredResults).toBeGreaterThan(0);
    });

    test('When filter input receives special characters Then page should not crash', async ({ page }) => {
      // Given: The filter input is visible
      await expect(homePage.nodeList.filterInput).toBeVisible();

      // When: User types special characters that could break regex or URL encoding
      for (const specialInput of ['[test]', '*.note', '/folder/sub', 'a?b=c']) {
        await homePage.nodeList.filterInput.fill(specialInput);
        // Then: The page must still render without crashing — no blank screen, input remains editable.
        // Note: nz-tree may be hidden when the filter returns 0 results; that is valid behavior.
        await expect(page.locator('zeppelin-node-list')).toBeVisible();
        await expect(homePage.nodeList.filterInput).toBeEditable();
        await expect(homePage.nodeList.filterInput).toHaveValue(specialInput);
      }

      // Clean up: clear the filter so other tests start fresh
      await homePage.nodeList.filterInput.fill('');
    });
  });
});
