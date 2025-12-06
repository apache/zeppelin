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
import { BasePage, E2E_TEST_FOLDER } from './base-page';

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

  private async getFolderNode(folderName: string): Promise<Locator> {
    const nameParts = folderName.split('/');
    if (nameParts.length > 1) {
      const parentName = nameParts[0];
      const childName = nameParts.slice(1).join('/');
      const parentNode = this.page.locator('a.name').filter({ hasText: new RegExp(parentName, 'i') });
      await parentNode.first().click();
      await this.page.waitForTimeout(1000); // Wait for folder to expand
      return this.page
        .locator('.node')
        .filter({
          has: this.page.locator('.folder .name').filter({ hasText: new RegExp(childName, 'i') })
        })
        .first();
    } else {
      return this.page
        .locator('.node')
        .filter({
          has: this.page.locator('.folder .name', { hasText: new RegExp(folderName, 'i') })
        })
        .first();
    }
  }

  async hoverOverFolder(folderName: string): Promise<void> {
    // Wait for the folder list to be loaded
    await this.page.waitForSelector('zeppelin-node-list', { state: 'visible' });

    const folderNode = await this.getFolderNode(folderName);

    // Wait for the folder to be visible and hover over the entire .node container
    try {
      await folderNode.isVisible();
    } catch {
      // Occasionally the E2E_TEST_FOLDER opened in beforeEach ends up being collapsed again,
      // so this check was added to handle that intermittent state.
      this.page.locator(`text=${E2E_TEST_FOLDER}`).click();
      await folderNode.isVisible();
    }

    await folderNode.hover({ force: true });

    // Wait for hover effects to take place by checking for interactive elements
    await this.page
      .waitForSelector('.node a[nz-tooltip], .node i[nztype], .node button', {
        state: 'visible',
        timeout: 5000
      })
      .catch(() => {
        console.log('No interactive elements found after hover, continuing...');
      });
  }

  async clickDeleteIcon(folderName: string): Promise<void> {
    // First hover over the folder to reveal the delete icon
    await this.hoverOverFolder(folderName);

    const folderNode = await this.getFolderNode(folderName);

    await folderNode.hover();

    const deleteIcon = folderNode.locator('.folder .operation a[nz-tooltip][nztooltiptitle="Move folder to Trash"]');
    await deleteIcon.click();
  }

  async clickRenameMenuItem(folderName: string): Promise<void> {
    // Ensure the specific folder is hovered first
    await this.hoverOverFolder(folderName);

    const folderNode = await this.getFolderNode(folderName);

    await folderNode.hover({ force: true });

    const renameIcon = folderNode.locator('.folder .operation a[nz-tooltip][nztooltiptitle="Rename folder"]');
    await renameIcon.click();

    // Wait for modal to appear by checking for its presence
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
    const folderNode = await this.getFolderNode(folderName);
    return folderNode.isVisible();
  }
}
