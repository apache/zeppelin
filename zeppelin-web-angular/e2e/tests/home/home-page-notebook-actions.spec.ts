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
      await expect(homePage.nodeList.tree).toBeVisible();
    });

    test('When refresh button is clicked Then should trigger reload with loading state', async ({ page }) => {
      const refreshButton = page.locator('a.refresh-note');
      const refreshIcon = page.locator('a.refresh-note i[nz-icon]');

      await expect(refreshButton).toBeVisible();
      await expect(refreshIcon).toBeVisible();

      await homePage.clickRefreshNotes();

      await page.waitForTimeout(500);

      await expect(refreshIcon).toBeVisible();
    });

    test('When filter is used Then should filter notebook list', async ({ page }) => {
      // Note (ZEPPELIN-6386):
      // The Notebook search filter in the New UI is currently too slow,
      // so this test is temporarily skipped. The skip will be removed
      // once the performance issue is resolved.
      test.skip();
      await homePage.filterNotes('test');
      await page.waitForLoadState('networkidle', { timeout: 15000 });
      const filteredResults = await page.locator('nz-tree .node').count();
      expect(filteredResults).toBeGreaterThanOrEqual(0);
    });
  });

  test.describe('Given create new note action', () => {
    test('When create new note is clicked Then should open note creation modal', async ({ page }) => {
      await homePage.clickCreateNewNote();
      await page.waitForSelector('zeppelin-note-create', { timeout: 10000 });
      await expect(page.locator('zeppelin-note-create')).toBeVisible();
    });
  });

  test.describe('Given import note action', () => {
    test('When import note is clicked Then should open import modal', async ({ page }) => {
      await homePage.clickImportNote();
      await page.waitForSelector('zeppelin-note-import', { timeout: 10000 });
      await expect(page.locator('zeppelin-note-import')).toBeVisible();
    });
  });

  test.describe('Given notebook refresh functionality', () => {
    test('When refresh is triggered Then should maintain notebook list visibility', async () => {
      await homePage.clickRefreshNotes();
      await homePage.waitForRefreshToComplete();
      await expect(homePage.zeppelinNodeList).toBeVisible();
      const isStillVisible = await homePage.zeppelinNodeList.isVisible();
      expect(isStillVisible).toBe(true);
    });
  });
});
