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
import { FolderRenamePage } from '../../../models/folder-rename-page';
import { FolderRenamePageUtil } from '../../../models/folder-rename-page.util';
import {
  addPageAnnotationBeforeEach,
  PAGES,
  performLoginIfRequired,
  waitForZeppelinReady,
  createTestNotebook
} from '../../../utils';

// serial: rename/delete ops mutate the shared notebook list; parallel execution
// would cause folder-not-found races when multiple tests modify the same folder structure
test.describe.serial('Folder Rename', () => {
  let folderRenamePage: FolderRenamePage;
  let folderRenameUtil: FolderRenamePageUtil;
  let testFolderName: string;

  addPageAnnotationBeforeEach(PAGES.SHARE.FOLDER_RENAME);

  test.beforeEach(async ({ page }) => {
    folderRenamePage = new FolderRenamePage(page);
    folderRenameUtil = new FolderRenamePageUtil(folderRenamePage);

    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);

    // Create a test notebook with folder structure
    testFolderName = `TestFolder_${Date.now()}`;
    await createTestNotebook(page, testFolderName);
    await page.goto('/#/');
  });

  test('Given folder exists in notebook list, When hovering over folder, Then context menu should appear', async () => {
    await folderRenameUtil.verifyContextMenuAppearsOnHover(testFolderName);
  });

  test('Given folder exists, When hovering over folder, Then Rename option should be visible', async () => {
    await folderRenameUtil.verifyContextMenuAppearsOnHover(testFolderName);
  });

  test('Given context menu is open, When clicking Rename, Then rename modal should open', async () => {
    await folderRenameUtil.verifyRenameModalOpens(testFolderName);
  });

  test('Given rename modal is open, When checking modal content, Then input field should be displayed', async () => {
    await folderRenamePage.hoverOverFolder(testFolderName);
    await folderRenamePage.clickRenameMenuItem(testFolderName);
    await folderRenameUtil.verifyRenameInputIsDisplayed();
  });

  test('Given rename modal is open, When entering new name and confirming, Then folder should be renamed', async ({
    page
  }) => {
    const browserName = page.context().browser()?.browserType().name();
    const renamedFolderName = `TestFolderRenamed_${`${Date.now()}_${browserName}`}`;

    await folderRenameUtil.verifyFolderCanBeRenamed(testFolderName, renamedFolderName);
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

  test('Given folder can be renamed, When opening context menu multiple times, Then menu should consistently appear', async ({
    page
  }) => {
    await folderRenameUtil.openContextMenuOnHoverAndVerifyOptions(testFolderName);
    await page.locator('h1', { hasText: 'Welcome to Zeppelin!' }).hover();
    await folderRenameUtil.openContextMenuOnHoverAndVerifyOptions(testFolderName);
  });

  test('should remove source folder when renamed to an existing folder name', async ({ page }) => {
    // Create a second folder to use as a name collision target
    const existingFolderName = `ExistingFolder_${Date.now()}`;
    await createTestNotebook(page, existingFolderName);
    await page.goto('/#/'); // Refresh to see the new folder

    // Attempt to rename the first folder to the name of the second folder
    await folderRenamePage.hoverOverFolder(testFolderName);
    await folderRenamePage.clickRenameMenuItem(testFolderName);
    await folderRenamePage.clearNewName();
    await folderRenamePage.enterNewName(existingFolderName);
    await folderRenamePage.clickConfirm();

    // Wait for the source folder to disappear (as it's merged into target)
    await expect(page.locator('.folder .name', { hasText: testFolderName })).toHaveCount(0, { timeout: 10000 });
    // Wait for the target folder to remain visible
    await expect(page.locator('.folder .name', { hasText: existingFolderName })).toBeVisible({ timeout: 10000 });
  });
});
