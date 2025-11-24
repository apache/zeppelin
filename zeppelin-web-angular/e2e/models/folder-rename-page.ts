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

import { Locator, Page } from '@playwright/test';
import { BasePage } from './base-page';

export class FolderRenamePage extends BasePage {
  readonly folderList: Locator;
  readonly renameMenuItem: Locator;
  readonly renameModal: Locator;
  readonly renameInput: Locator;
  readonly confirmButton: Locator;
  readonly cancelButton: Locator;
  readonly validationError: Locator;
  readonly deleteConfirmation: Locator;
  readonly deleteConfirmButton: Locator;
  readonly deleteCancelButton: Locator;

  constructor(page: Page) {
    super(page);
    this.folderList = page.locator('zeppelin-node-list');
    this.renameMenuItem = page.locator('a[nz-tooltip][nztooltiptitle="Rename folder"]').first();
    this.renameModal = page.locator('.ant-modal');
    this.renameInput = page.locator('input[placeholder="Insert New Name"]');
    this.confirmButton = page.getByRole('button', { name: 'Rename' });
    this.cancelButton = page.locator('.ant-modal-close-x'); // Modal close button
    this.validationError = page.locator(
      '.ant-form-item-explain, .error-message, .validation-error, .ant-form-item-explain-error'
    );
    this.deleteConfirmation = page.locator('.ant-popover').filter({ hasText: 'This folder will be moved to trash.' });
    this.deleteConfirmButton = page.getByRole('button', { name: 'OK' }).last();
    this.deleteCancelButton = page.getByRole('button', { name: 'Cancel' }).last();
  }

  async hoverOverFolder(folderName: string): Promise<void> {
    // Wait for the folder list to be loaded
    await this.folderList.waitFor({ state: 'visible' });

    // Find the folder node by locating the .node that contains the specific folder name
    // Use a more reliable selector that targets the folder name exactly
    const folderNode = this.page
      .locator('.node')
      .filter({
        has: this.page.locator('.folder .name', { hasText: folderName })
      })
      .first();

    // Wait for the folder to be visible and hover over the entire .node container
    await folderNode.waitFor({ state: 'visible' });
    await folderNode.hover();

    // Wait for hover effects to take place by checking for interactive elements
    await folderNode
      .locator('a[nz-tooltip], i[nztype], button')
      .first()
      .waitFor({
        state: 'visible',
        timeout: 2000
      })
      .catch(() => {
        console.log('No interactive elements found after hover, continuing...');
      });
  }

  async clickDeleteIcon(folderName: string): Promise<void> {
    // First hover over the folder to reveal the delete icon
    await this.hoverOverFolder(folderName);

    // Find the specific folder node and its delete button
    const folderNode = this.page
      .locator('.node')
      .filter({
        has: this.page.locator('.folder .name', { hasText: folderName })
      })
      .first();

    const deleteIcon = folderNode.locator('a[nz-tooltip][nztooltiptitle="Move folder to Trash"]');
    await deleteIcon.click();
  }

  async clickRenameMenuItem(folderName: string): Promise<void> {
    if (folderName) {
      // Ensure the specific folder is hovered first
      await this.hoverOverFolder(folderName);

      // Find the specific folder node and its rename button
      const folderNode = this.page
        .locator('.node')
        .filter({
          has: this.page.locator('.folder .name', { hasText: folderName })
        })
        .first();

      const renameIcon = folderNode.locator('a[nz-tooltip][nztooltiptitle="Rename folder"]');
      await renameIcon.click();

      // Wait for modal to appear by checking for its presence
      await this.renameModal.waitFor({ state: 'visible', timeout: 3000 });
    } else {
      // Fallback to generic rename button (now using .first() to avoid strict mode)
      await this.renameMenuItem.click();
      await this.renameModal.waitFor({ state: 'visible', timeout: 3000 });
    }
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

  async isRenameModalVisible(): Promise<boolean> {
    return this.renameModal.isVisible();
  }

  async isFolderVisible(folderName: string): Promise<boolean> {
    return this.page
      .locator('.node')
      .filter({
        has: this.page.locator('.folder .name', { hasText: folderName })
      })
      .first()
      .isVisible();
  }
}
