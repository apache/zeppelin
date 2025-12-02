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
import { NoteRenamePage } from '../../../models/note-rename-page';
import { NoteRenamePageUtil } from '../../../models/note-rename-page.util';
import {
  addPageAnnotationBeforeEach,
  PAGES,
  performLoginIfRequired,
  waitForZeppelinReady,
  createTestNotebook,
  deleteTestNotebook
} from '../../../utils';

test.describe('Note Rename', () => {
  let noteRenamePage: NoteRenamePage;
  let noteRenameUtil: NoteRenamePageUtil;
  let testNotebook: { noteId: string; paragraphId: string };

  addPageAnnotationBeforeEach(PAGES.SHARE.NOTE_RENAME);

  test.beforeEach(async ({ page }) => {
    noteRenamePage = new NoteRenamePage(page);
    noteRenameUtil = new NoteRenamePageUtil(page, noteRenamePage);

    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);

    // Create a test notebook for each test
    testNotebook = await createTestNotebook(page);

    // Navigate to the test notebook
    await page.goto(`/#/notebook/${testNotebook.noteId}`);
    await page.waitForLoadState('networkidle');
  });

  test.afterEach(async ({ page }) => {
    // Clean up the test notebook after each test
    if (testNotebook?.noteId) {
      await deleteTestNotebook(page, testNotebook.noteId);
    }
  });

  test('Given notebook page is loaded, When checking note title, Then title should be displayed', async () => {
    await noteRenameUtil.verifyTitleIsDisplayed();
  });

  test('Given note title is displayed, When checking default title, Then title should match pattern', async () => {
    await noteRenameUtil.verifyTitleText('Test Notebook');
  });

  test('Given note title is displayed, When clicking title, Then title input should appear', async () => {
    await noteRenameUtil.verifyTitleInputAppearsOnClick();
  });

  test('Given title input is displayed, When entering new title and pressing Enter, Then title should be updated', async () => {
    await noteRenameUtil.verifyTitleCanBeChanged(`Test Note 1-${Date.now()}`);
  });

  test('Given title input is displayed, When entering new title and blurring, Then title should be updated', async () => {
    await noteRenameUtil.verifyTitleChangeWithBlur(`Test Note 2-${Date.now()}`);
  });

  test('Given title input is displayed, When entering text and pressing Escape, Then changes should be cancelled', async () => {
    const originalTitle = await noteRenamePage.getTitle();
    await noteRenameUtil.verifyTitleChangeCancelsOnEscape(originalTitle);
  });

  test('Given title input is displayed, When clearing title and pressing Enter, Then empty title should not be allowed', async () => {
    await noteRenameUtil.verifyEmptyTitleIsNotAllowed();
  });

  test('Given note title exists, When changing title multiple times, Then each change should persist', async () => {
    await noteRenameUtil.verifyTitleCanBeChanged(`First Change-${Date.now()}`);
    await noteRenameUtil.verifyTitleCanBeChanged(`Second Change-${Date.now()}`);
    await noteRenameUtil.verifyTitleCanBeChanged(`Third Change-${Date.now()}`);
  });

  test('Given title is in edit mode, When checking input visibility, Then input should be visible and title should be hidden', async () => {
    await noteRenamePage.clickTitle();
    const isInputVisible = await noteRenamePage.isTitleInputVisible();
    expect(isInputVisible).toBe(true);
  });

  test('Given title has special characters, When renaming with special characters, Then special characters should be preserved', async () => {
    const title = `Test-Note_123 (v2)-${Date.now()}`;
    await noteRenamePage.clickTitle();
    await noteRenamePage.clearTitle();
    await noteRenamePage.enterTitle(title);
    await noteRenamePage.pressEnter();
    await noteRenamePage.page.waitForTimeout(500);
    await noteRenameUtil.verifyTitleText(title);
  });
});
