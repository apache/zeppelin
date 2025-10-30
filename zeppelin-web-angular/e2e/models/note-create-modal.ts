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
  readonly closeButton: Locator;
  readonly noteNameInput: Locator;
  readonly interpreterDropdown: Locator;
  readonly folderInfoAlert: Locator;
  readonly createButton: Locator;

  constructor(page: Page) {
    super(page);
    this.modal = page.locator('[role="dialog"]').filter({ has: page.locator('input[name="noteName"]') });
    this.closeButton = page.getByRole('button', { name: 'Close' });
    this.noteNameInput = page.locator('input[name="noteName"]');
    this.interpreterDropdown = page.locator('nz-select[name="defaultInterpreter"]');
    this.folderInfoAlert = page.getByText("Use '/' to create folders");
    this.createButton = page.getByRole('button', { name: 'Create' });
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

  async clickCreate(): Promise<void> {
    await this.createButton.click();
  }

  async isFolderInfoVisible(): Promise<boolean> {
    return this.folderInfoAlert.isVisible();
  }
}
