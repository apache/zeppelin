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

  async verifyContextMenuAppearsOnHover(folderName: string): Promise<void> {
    await this.folderRenamePage.hoverOverFolder(folderName);

    // Find the specific folder node and its rename button
    const folderNode = this.page
      .locator('.node')
      .filter({
        has: this.page.locator('.folder .name', { hasText: folderName })
      })
      .first();

    const renameButton = folderNode.locator('a[nz-tooltip][nztooltiptitle="Rename folder"]');
    await expect(renameButton).toBeVisible();
  }

  async verifyRenameMenuItemIsDisplayed(folderName: string): Promise<void> {
    // First ensure we hover over the specific folder to show operations
    await this.folderRenamePage.hoverOverFolder(folderName);

    // Find the specific folder node and its rename button
    const folderNode = this.page
      .locator('.node')
      .filter({
        has: this.page.locator('.folder .name', { hasText: folderName })
      })
      .first();

    const renameButton = folderNode.locator('a[nz-tooltip][nztooltiptitle="Rename folder"]');
    await expect(renameButton).toBeVisible();
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

    // Wait for the UI to update before reloading
    const oldFolder = this.page.locator('.folder .name', { hasText: oldName });
    await expect(oldFolder).not.toBeVisible({ timeout: 10000 });

    const newFolder = this.page.locator('.folder .name', { hasText: newName });
    await expect(newFolder).toBeVisible({ timeout: 10000 });

    // Optional: Keep the reload as a final sanity check against the backend state
    await this.page.reload();
    await this.page.waitForLoadState('networkidle', { timeout: 15000 });

    // Re-check for the renamed folder after reload
    const newFolderAfterReload = this.page.locator('.folder .name', { hasText: newName });

    await expect(newFolderAfterReload).toBeVisible({ timeout: 10000 });
  }

  async verifyRenameCancellation(folderName: string): Promise<void> {
    await this.folderRenamePage.hoverOverFolder(folderName);
    await this.folderRenamePage.clickRenameMenuItem(folderName);
    await this.folderRenamePage.enterNewName('Temporary Name');
    await this.folderRenamePage.clickCancel();
    await expect(this.folderRenamePage.renameModal).not.toBeVisible();
    const isVisible = await this.folderRenamePage.isFolderVisible(folderName);
    expect(isVisible).toBe(true);
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
    await this.folderRenamePage.hoverOverFolder(folderName);

    // Find the specific folder node and its delete button
    const folderNode = this.page
      .locator('.node')
      .filter({
        has: this.page.locator('.folder .name', { hasText: folderName })
      })
      .first();

    const deleteIcon = folderNode.locator('a[nz-tooltip][nztooltiptitle="Move folder to Trash"]');
    await expect(deleteIcon).toBeVisible();
  }

  async verifyDeleteConfirmationAppears(): Promise<void> {
    await expect(this.folderRenamePage.deleteConfirmation).toBeVisible();
  }

  async openContextMenuOnHoverAndVerifyOptions(folderName: string): Promise<void> {
    await this.verifyContextMenuAppearsOnHover(folderName);
    await this.verifyRenameMenuItemIsDisplayed(folderName);
  }
}
