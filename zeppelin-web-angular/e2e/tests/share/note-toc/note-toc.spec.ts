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

import { test, expect } from '@playwright/test';
import { NoteTocPage } from '../../../models/note-toc-page';
import { NoteTocPageUtil } from '../../../models/note-toc-page.util';
import {
  addPageAnnotationBeforeEach,
  PAGES,
  performLoginIfRequired,
  waitForZeppelinReady,
  createTestNotebook,
  deleteTestNotebook
} from '../../../utils';

test.describe('Note Table of Contents', () => {
  let noteTocPage: NoteTocPage;
  let noteTocUtil: NoteTocPageUtil;
  let testNotebook: { noteId: string; paragraphId: string };

  addPageAnnotationBeforeEach(PAGES.SHARE.NOTE_TOC);

  test.beforeEach(async ({ page }) => {
    noteTocPage = new NoteTocPage(page);
    noteTocUtil = new NoteTocPageUtil(noteTocPage);

    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);

    testNotebook = await createTestNotebook(page);

    // Use the more robust navigation method from parent class
    await noteTocPage.navigateToNotebook(testNotebook.noteId);

    // Wait for notebook to fully load
    await page.waitForLoadState('networkidle');

    // Verify we're actually in a notebook with more specific checks
    await expect(page).toHaveURL(new RegExp(`#/notebook/${testNotebook.noteId}`));
    await expect(page.locator('zeppelin-notebook-paragraph').first()).toBeVisible({ timeout: 15000 });

    // Only proceed if TOC button exists (confirms notebook context)
    await expect(noteTocPage.tocToggleButton).toBeVisible({ timeout: 10000 });
  });

  test.afterEach(async ({ page }) => {
    if (testNotebook?.noteId) {
      await deleteTestNotebook(page, testNotebook.noteId);
    }
  });

  test('Given notebook page is loaded, When clicking TOC toggle button, Then TOC panel should open', async () => {
    await noteTocUtil.verifyTocPanelOpens();
  });

  test('Given TOC panel is open, When checking panel title, Then title should display "Table of Contents"', async () => {
    await noteTocUtil.verifyTocPanelOpens();
    await noteTocUtil.verifyTocTitleIsDisplayed();
  });

  test('Given TOC panel is open with no headings, When checking content, Then empty message should be displayed', async () => {
    await noteTocUtil.verifyTocPanelOpens();
    await noteTocUtil.verifyEmptyMessageIsDisplayed();
  });

  test('Given TOC panel is open, When clicking close button, Then TOC panel should close', async () => {
    await noteTocUtil.verifyTocPanelOpens();
    await noteTocUtil.verifyTocPanelCloses();
  });

  test('Given TOC toggle button exists, When checking button visibility, Then button should be accessible', async () => {
    await expect(noteTocPage.tocToggleButton).toBeVisible();
  });

  test('Given TOC panel can be toggled, When opening and closing multiple times, Then panel should respond consistently', async () => {
    await noteTocUtil.verifyTocPanelOpens();
    await noteTocUtil.verifyTocPanelCloses();
    await noteTocUtil.verifyTocPanelOpens();
    await noteTocUtil.verifyTocPanelCloses();
  });
});
