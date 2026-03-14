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
    await this.page.waitForSelector('zeppelin-node-list', { state: 'visible' });
    const folderNode = this.getFolderNode(folderName);
    // Hover a.name (not .folder) — CSS :hover on .operation is triggered by the text link, same as clickRenameMenuItem()
    const nameLink = folderNode.locator('a.name');
    await nameLink.scrollIntoViewIfNeeded();
    await nameLink.hover({ force: true }); // JUSTIFIED: .operation buttons are CSS-:hover-revealed; force required to trigger the hover event on the text link that activates the context menu
  }

  async clickDeleteIcon(folderName: string): Promise<void> {
    await this.hoverOverFolder(folderName);
    const folderNode = this.getFolderNode(folderName);
    const deleteIcon = folderNode.locator(
      '.operation a[nztooltiptitle*="Move folder to Trash"], .operation a[nztooltiptitle*="Trash"]'
    );
    await expect(deleteIcon).toBeVisible({ timeout: 5000 });
    await deleteIcon.click({ force: true }); // JUSTIFIED: icon is only actionable after CSS-:hover; force bypasses the actionability check that fails before hover state propagates
  }

  async clickRenameMenuItem(folderName: string): Promise<void> {
    const folderNode = this.getFolderNode(folderName);
    const nameLink = folderNode.locator('a.name');

    await nameLink.scrollIntoViewIfNeeded();
    await nameLink.hover({ force: true }); // JUSTIFIED: .operation buttons are CSS-:hover-revealed; force required to trigger the hover event on the text link that activates the context menu

    const renameIcon = folderNode.locator('.operation a[nztooltiptitle="Rename folder"]');

    await expect(renameIcon).toBeVisible({ timeout: 3000 });
    await renameIcon.click({ force: true }); // JUSTIFIED: icon is only actionable after CSS-:hover; force bypasses the actionability check that fails before hover state propagates

    await this.renameModal.waitFor({ state: 'visible', timeout: 3000 });
  }

  async enterNewName(name: string): Promise<void> {
    await this.renameInput.fill(name);
  }

  async clearNewName(): Promise<void> {
    await this.renameInput.clear();
    await expect(this.renameInput).toHaveValue('');
  }

  async clickConfirm(): Promise<void> {
    // Wait for button to be enabled before clicking
    await expect(this.confirmButton).toBeEnabled({ timeout: 5000 });
    await this.confirmButton.click();

    // Wait for modal to close; if it stays open validation errors prevented submission (caller re-checks)
    await this.renameModal.waitFor({ state: 'detached', timeout: 3000 }).catch(() => {}); // JUSTIFIED: modal stays open on validation failure; caller asserts final state
  }

  async clickCancel(): Promise<void> {
    await this.cancelButton.click();
  }
}
