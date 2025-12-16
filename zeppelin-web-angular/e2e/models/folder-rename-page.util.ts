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

import { expect, Page } from '@playwright/test';
import { FolderRenamePage } from './folder-rename-page';

export class FolderRenamePageUtil {
  constructor(
    private readonly page: Page,
    private readonly folderRenamePage: FolderRenamePage
  ) {}

  private getFolderNode(folderName: string) {
    return this.page
      .locator('.node')
      .filter({
        has: this.page.locator('.folder .name', { hasText: folderName })
      })
      .first();
  }

  async verifyRenameButtonIsVisible(folderName: string): Promise<void> {
    await this.folderRenamePage.hoverOverFolder(folderName);
    const folderNode = this.getFolderNode(folderName);
    const renameButton = folderNode.locator('.folder .operation a[nz-tooltip][nztooltiptitle="Rename folder"]');
    // Just verify the element exists in DOM, not visibility(for Webkit & Edge)
    await expect(renameButton).toHaveCount(1);
  }

  async verifyDeleteButtonIsVisible(folderName: string): Promise<void> {
    await this.folderRenamePage.hoverOverFolder(folderName);
    const folderNode = this.getFolderNode(folderName);
    const deleteButton = folderNode.locator('.folder .operation a[nztooltiptitle*="Move folder to Trash"]');
    await expect(deleteButton).toBeVisible();
  }

  async verifyContextMenuAppearsOnHover(folderName: string): Promise<void> {
    await this.verifyRenameButtonIsVisible(folderName);
  }

  async verifyRenameMenuItemIsDisplayed(folderName: string): Promise<void> {
    await this.verifyRenameButtonIsVisible(folderName);
  }

  async verifyRenameModalOpens(folderName: string): Promise<void> {
    await this.folderRenamePage.clickRenameMenuItem(folderName);

    // Wait for modal to appear with extended timeout
    await expect(this.folderRenamePage.renameModal).toBeVisible({ timeout: 10000 });
  }

  async verifyRenameInputIsDisplayed(): Promise<void> {
    await expect(this.folderRenamePage.renameInput).toBeVisible();
  }

  async verifyFolderCanBeRenamed(oldName: string, newName: string): Promise<void> {
    await this.folderRenamePage.hoverOverFolder(oldName);
    await this.folderRenamePage.clickRenameMenuItem(oldName);
    await this.folderRenamePage.renameInput.waitFor({ state: 'visible', timeout: 5000 });
    await this.folderRenamePage.clearNewName();
    await this.folderRenamePage.enterNewName(newName);

    await this.folderRenamePage.clickConfirm();

    // Wait for the modal to disappear
    await expect(this.folderRenamePage.renameModal).not.toBeVisible({ timeout: 10000 });

    // Wait for the UI to update before reloading for the old name to disappear
    const oldFolder = this.page.locator('.folder .name', { hasText: oldName });
    await expect(oldFolder).not.toBeVisible({ timeout: 10000 });

    // Optional: Keep the reload as a final sanity check against the backend state
    await this.page.reload();
    await this.page.waitForLoadState('networkidle', { timeout: 15000 });

    const baseNewName = newName.split('/').pop();

    // Ensure the folder list is stable and contains the new folder after reload
    await this.page.waitForFunction(
      ([expectedBaseName]) => {
        if (!expectedBaseName) {
          throw Error('Renamed Folder name is not exist.');
        }
        const folders = Array.from(document.querySelectorAll('.folder .name'));
        return folders.some(folder => folder.textContent?.includes(expectedBaseName));
      },
      [baseNewName],
      { timeout: 30000 }
    );
  }

  async verifyEmptyNameIsNotAllowed(folderName: string): Promise<void> {
    await this.folderRenamePage.hoverOverFolder(folderName);
    await this.folderRenamePage.clickRenameMenuItem(folderName);
    await this.folderRenamePage.clearNewName();

    // NEW ASSERTION: The confirm button should be disabled when the input is empty.
    await expect(this.folderRenamePage.confirmButton).toBeDisabled({ timeout: 5000 });

    // Clean up: Click cancel to close the modal after verifying validation.
    await this.folderRenamePage.clickCancel();
    await expect(this.folderRenamePage.renameModal).not.toBeVisible({ timeout: 5000 });

    // Verify the original folder still exists and was not renamed or deleted.
    const originalFolderLocator = this.page.locator('.folder .name', { hasText: folderName });
    await expect(originalFolderLocator).toBeVisible({ timeout: 5000 });
  }

  async verifyDeleteIconIsDisplayed(folderName: string): Promise<void> {
    await this.verifyDeleteButtonIsVisible(folderName);
  }

  async verifyDeleteConfirmationAppears(): Promise<void> {
    await expect(this.folderRenamePage.deleteConfirmation).toBeVisible();
  }

  async openContextMenuOnHoverAndVerifyOptions(folderName: string): Promise<void> {
    await this.verifyRenameButtonIsVisible(folderName);
    await this.verifyDeleteButtonIsVisible(folderName);
  }
}
