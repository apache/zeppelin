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
import { HomePage } from '../../../models/home-page';
import { FolderRenamePage } from '../../../models/folder-rename-page';
import { FolderRenamePageUtil } from '../../../models/folder-rename-page.util';
import {
  addPageAnnotationBeforeEach,
  PAGES,
  performLoginIfRequired,
  waitForZeppelinReady,
  createTestNotebook,
  deleteTestNotebook
} from '../../../utils';

test.describe('Folder Rename', () => {
  let homePage: HomePage;
  let folderRenamePage: FolderRenamePage;
  let folderRenameUtil: FolderRenamePageUtil;
  let testNotebook: { noteId: string; paragraphId: string };
  let testFolderName: string;
  const renamedFolderName = 'RenamedFolder';

  addPageAnnotationBeforeEach(PAGES.SHARE.FOLDER_RENAME);

  test.beforeEach(async ({ page }) => {
    homePage = new HomePage(page);
    folderRenamePage = new FolderRenamePage(page);
    folderRenameUtil = new FolderRenamePageUtil(page, folderRenamePage);

    await page.goto('/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);

    // Create a test notebook with folder structure
    testFolderName = `TestFolder_${Date.now()}`;
    testNotebook = await createTestNotebook(page, testFolderName);
    // testFolderName is now the folder that contains the notebook
  });

  test.afterEach(async ({ page }) => {
    // Clean up the test notebook
    if (testNotebook?.noteId) {
      await deleteTestNotebook(page, testNotebook.noteId);
    }
  });

  test('Given folder exists in notebook list, When hovering over folder, Then context menu should appear', async () => {
    await folderRenameUtil.verifyContextMenuAppearsOnHover(testFolderName);
  });

  test('Given context menu is open, When checking menu items, Then Rename option should be visible', async () => {
    await folderRenamePage.hoverOverFolder(testFolderName);
    await folderRenameUtil.verifyRenameMenuItemIsDisplayed(testFolderName);
  });

  test('Given context menu is open, When clicking Rename, Then rename modal should open', async () => {
    await folderRenamePage.hoverOverFolder(testFolderName);
    await folderRenameUtil.verifyRenameModalOpens(testFolderName);
  });

  test('Given rename modal is open, When checking modal content, Then input field should be displayed', async () => {
    await folderRenamePage.hoverOverFolder(testFolderName);
    await folderRenamePage.clickRenameMenuItem(testFolderName);
    await folderRenameUtil.verifyRenameInputIsDisplayed();
  });

  test('Given rename modal is open, When entering new name and confirming, Then folder should be renamed', async () => {
    await folderRenameUtil.verifyFolderCanBeRenamed(testFolderName, renamedFolderName);
  });

  test('Given rename modal is open, When clicking Cancel, Then modal should close without changes', async () => {
    await folderRenameUtil.verifyRenameCancellation(testFolderName);
  });

  test('Given rename modal is open, When submitting empty name, Then empty name should not be allowed', async () => {
    await folderRenameUtil.verifyEmptyNameIsNotAllowed(testFolderName);
  });

  test('Given folder is hovered, When checking available options, Then Delete icon should be visible and clickable', async () => {
    await folderRenamePage.hoverOverFolder(testFolderName);
    await folderRenameUtil.verifyDeleteIconIsDisplayed(testFolderName);
  });

  test('Given folder exists, When clicking delete icon, Then delete confirmation should appear', async () => {
    await folderRenamePage.clickDeleteIcon(testFolderName);
    await folderRenameUtil.verifyDeleteConfirmationAppears();
  });

  test('Given folder can be renamed, When opening context menu multiple times, Then menu should consistently appear', async () => {
    await folderRenameUtil.openContextMenuOnHoverAndVerifyOptions(testFolderName);
    await folderRenamePage.page.keyboard.press('Escape');
    await folderRenamePage.page.waitForTimeout(500);
    await folderRenameUtil.openContextMenuOnHoverAndVerifyOptions(testFolderName);
  });

  test('Given folder is renamed, When checking folder list, Then old name should not exist and new name should exist', async ({
    page
  }) => {
    await folderRenamePage.hoverOverFolder(testFolderName);
    await folderRenamePage.clickRenameMenuItem(testFolderName);
    await folderRenamePage.clearNewName();
    await folderRenamePage.enterNewName(renamedFolderName);
    await folderRenamePage.clickConfirm();
    await page.waitForTimeout(1000);

    const oldFolderVisible = await folderRenamePage.isFolderVisible(testFolderName);
    const newFolderVisible = await folderRenamePage.isFolderVisible(renamedFolderName);

    expect(oldFolderVisible).toBe(false);
    expect(newFolderVisible).toBe(true);
  });
});
