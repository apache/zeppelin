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

  async verifyRenameModalOpens(folderName?: string): Promise<void> {
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
    await this.folderRenamePage.clearNewName();
    await this.folderRenamePage.enterNewName(newName);
    await this.folderRenamePage.clickConfirm();
    await this.page.waitForTimeout(1000);
    const isVisible = await this.folderRenamePage.isFolderVisible(newName);
    expect(isVisible).toBe(true);
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
    await this.folderRenamePage.clickConfirm();
    await expect(this.folderRenamePage.renameModal).toBeVisible();
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
