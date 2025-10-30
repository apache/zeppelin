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

export class NoteCreateModal extends BasePage {
  readonly modal: Locator;
  readonly modalTitle: Locator;
  readonly closeButton: Locator;
  readonly noteNameInput: Locator;
  readonly interpreterDropdown: Locator;
  readonly interpreterTextbox: Locator;
  readonly interpreterOptions: Locator;
  readonly folderInfoAlert: Locator;
  readonly createButton: Locator;
  readonly cloneButton: Locator;

  constructor(page: Page) {
    super(page);
    this.modal = page.locator('[role="dialog"]').filter({ has: page.locator('input[name="noteName"]') });
    this.modalTitle = page.locator('.ant-modal-title');
    this.closeButton = page.getByRole('button', { name: 'Close' });
    this.noteNameInput = page.locator('input[name="noteName"]');
    this.interpreterDropdown = page.locator('nz-select[name="defaultInterpreter"]');
    this.interpreterTextbox = this.interpreterDropdown.locator('input');
    this.interpreterOptions = page.locator('nz-option-item');
    this.folderInfoAlert = page.getByText("Use '/' to create folders");
    this.createButton = page.getByRole('button', { name: 'Create' });
    this.cloneButton = page.getByRole('button', { name: 'Clone' });
  }

  async isModalVisible(): Promise<boolean> {
    return this.modal.isVisible();
  }

  async close(): Promise<void> {
    await this.closeButton.click();
  }

  async getNoteName(): Promise<string> {
    return (await this.noteNameInput.inputValue()) || '';
  }

  async setNoteName(name: string): Promise<void> {
    await this.noteNameInput.clear();
    await this.noteNameInput.fill(name);
  }

  async openInterpreterDropdown(): Promise<void> {
    await this.interpreterDropdown.click();
  }

  async selectInterpreter(interpreterName: string): Promise<void> {
    await this.openInterpreterDropdown();

    // Handle browser-specific differences
    const browserName = this.page.context().browser()?.browserType().name();

    if (browserName === 'webkit') {
      // WebKit needs more specific targeting
      await this.page.locator('.ant-select-item-option-content').filter({ hasText: interpreterName }).first().click();
    } else {
      // Chrome/Firefox - use nz-option-item
      await this.page.locator('nz-option-item').filter({ hasText: interpreterName }).first().click();
    }
  }

  async searchInterpreter(searchTerm: string): Promise<void> {
    await this.openInterpreterDropdown();
    await this.interpreterTextbox.fill(searchTerm);
  }

  async getAvailableInterpreters(): Promise<string[]> {
    await this.openInterpreterDropdown();
    const options = await this.interpreterOptions.allTextContents();
    await this.page.keyboard.press('Escape');
    return options;
  }

  async clickCreate(): Promise<void> {
    await this.createButton.click();
  }

  async clickClone(): Promise<void> {
    await this.cloneButton.click();
  }

  async isCreateButtonVisible(): Promise<boolean> {
    return this.createButton.isVisible();
  }

  async isCloneButtonVisible(): Promise<boolean> {
    return this.cloneButton.isVisible();
  }

  async isInterpreterDropdownVisible(): Promise<boolean> {
    return this.interpreterDropdown.isVisible();
  }

  async isFolderInfoVisible(): Promise<boolean> {
    return this.folderInfoAlert.isVisible();
  }

  async getModalTitle(): Promise<string> {
    return (await this.modalTitle.textContent()) || '';
  }
}
