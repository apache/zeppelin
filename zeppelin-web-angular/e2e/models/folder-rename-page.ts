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

import { expect, Locator, Page } from '@playwright/test';
import { BasePage } from './base-page';

export class FolderRenamePage extends BasePage {
  readonly folderList: Locator;
  readonly renameModal: Locator;
  readonly renameInput: Locator;
  readonly confirmButton: Locator;
  readonly cancelButton: Locator;
  readonly deleteConfirmation: Locator;

  constructor(page: Page) {
    super(page);
    this.folderList = page.locator('zeppelin-node-list');
    this.renameModal = page.locator('.ant-modal');
    this.renameInput = page.locator('input[placeholder="Insert New Name"]');
    this.confirmButton = page.getByRole('button', { name: 'Rename' });
    this.cancelButton = page.locator('.ant-modal-close-x'); // Modal close button
    this.deleteConfirmation = page.locator('.ant-popover').filter({ hasText: 'This folder will be moved to trash.' });
  }

  private getFolderNode(folderName: string): Locator {
    return this.page
      .locator('.folder')
      .filter({
        has: this.page.locator('a.name', {
          hasText: new RegExp(`^\\s*${folderName}\\s*$`, 'i')
        })
      })
      .first();
  }

  async hoverOverFolder(folderName: string): Promise<void> {
    // Wait for the folder list to be loaded
    await this.page.waitForSelector('zeppelin-node-list', { state: 'visible' });

    const folderNode = await this.getFolderNode(folderName);

    await folderNode.hover();
  }

  async clickDeleteIcon(folderName: string): Promise<void> {
    // First hover over the folder to reveal the delete icon
    await this.hoverOverFolder(folderName);

    const folderNode = await this.getFolderNode(folderName);

    await folderNode.hover();

    // Wait for operation buttons to appear and try multiple selector patterns
    const deleteIcon = folderNode.locator(
      '.operation a[nztooltiptitle*="Move folder to Trash"], .operation a[nztooltiptitle*="Trash"]'
    );
    await expect(deleteIcon).toBeVisible({ timeout: 5000 });
    await deleteIcon.click({ force: true });
  }

  async clickRenameMenuItem(folderName: string): Promise<void> {
    const folderNode = await this.getFolderNode(folderName);
    const nameLink = folderNode.locator('a.name');

    await nameLink.scrollIntoViewIfNeeded();
    await nameLink.hover({ force: true });

    const renameIcon = folderNode.locator('.operation a[nztooltiptitle="Rename folder"]');

    await expect(renameIcon).toBeVisible({ timeout: 3000 });
    await renameIcon.click({ force: true });

    await this.renameModal.waitFor({ state: 'visible', timeout: 3000 });
  }

  async enterNewName(name: string): Promise<void> {
    await this.renameInput.fill(name);
  }

  async clearNewName(): Promise<void> {
    await this.renameInput.clear();
  }

  async clickConfirm(): Promise<void> {
    await this.confirmButton.click();

    // Wait for validation or submission to process by monitoring modal state
    await this.page
      .waitForFunction(
        () => {
          // Check if modal is still open or if validation errors appeared
          const modal = document.querySelector('.ant-modal-wrap');
          const validationErrors = document.querySelectorAll('.ant-form-item-explain-error, .has-error');

          // If modal closed or validation errors appeared, processing is complete
          return !modal || validationErrors.length > 0 || (modal && getComputedStyle(modal).display === 'none');
        },
        { timeout: 2000 }
      )
      .catch(() => {
        console.log('Modal state check timeout, continuing...');
      });
  }

  async clickCancel(): Promise<void> {
    await this.cancelButton.click();
  }

  async isFolderVisible(folderName: string): Promise<boolean> {
    // Use a more direct approach with count check
    const folderCount = await this.page
      .locator('.node .folder .name')
      .filter({
        hasText: new RegExp(`^${folderName}$`, 'i')
      })
      .count();
    return folderCount > 0;
  }
}
