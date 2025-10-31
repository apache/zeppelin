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

export class NoteImportModal extends BasePage {
  readonly modal: Locator;
  readonly modalTitle: Locator;
  readonly closeButton: Locator;
  readonly importAsInput: Locator;
  readonly jsonFileTab: Locator;
  readonly urlTab: Locator;
  readonly uploadArea: Locator;
  readonly uploadIcon: Locator;
  readonly uploadText: Locator;
  readonly fileSizeLimit: Locator;
  readonly urlInput: Locator;
  readonly importNoteButton: Locator;
  readonly errorAlert: Locator;

  constructor(page: Page) {
    super(page);
    this.modal = page.locator('[role="dialog"]').filter({ has: page.locator('input[name="noteImportName"]') });
    this.modalTitle = page.locator('.ant-modal-title', { hasText: 'Import New Note' });
    this.closeButton = page.getByRole('button', { name: 'Close' });
    this.importAsInput = page.locator('input[name="noteImportName"]');
    this.jsonFileTab = page.getByRole('tab', { name: 'Import From JSON File' });
    this.urlTab = page.getByRole('tab', { name: 'Import From URL' });
    this.uploadArea = page.locator('nz-upload[nztype="drag"]');
    this.uploadIcon = page.locator('.ant-upload-drag-icon i[nz-icon]');
    this.uploadText = page.getByText('Click or drag JSON file to this area to upload');
    this.fileSizeLimit = page.locator('.ant-upload-hint strong');
    this.urlInput = page.locator('input[name="importUrl"]');
    this.importNoteButton = page.getByRole('button', { name: 'Import Note' });
    this.errorAlert = page.locator('nz-alert[nztype="error"]');
  }

  async isModalVisible(): Promise<boolean> {
    return this.modal.isVisible();
  }

  async close(): Promise<void> {
    await this.closeButton.click();
  }

  async setImportAsName(name: string): Promise<void> {
    await this.importAsInput.fill(name);
  }

  async getImportAsName(): Promise<string> {
    return (await this.importAsInput.inputValue()) || '';
  }

  async switchToJsonFileTab(): Promise<void> {
    await this.jsonFileTab.click();
  }

  async switchToUrlTab(): Promise<void> {
    await this.urlTab.click();
  }

  async isJsonFileTabSelected(): Promise<boolean> {
    const ariaSelected = await this.jsonFileTab.getAttribute('aria-selected');
    return ariaSelected === 'true';
  }

  async isUrlTabSelected(): Promise<boolean> {
    const ariaSelected = await this.urlTab.getAttribute('aria-selected');
    return ariaSelected === 'true';
  }

  async setImportUrl(url: string): Promise<void> {
    await this.urlInput.fill(url);
  }

  async clickImportNote(): Promise<void> {
    await this.importNoteButton.click();
  }

  async isImportNoteButtonDisabled(): Promise<boolean> {
    return this.importNoteButton.isDisabled();
  }

  async isImportNoteButtonLoading(): Promise<boolean> {
    const loadingIcon = this.importNoteButton.locator('.anticon-loading');
    return loadingIcon.isVisible();
  }

  async isUploadAreaVisible(): Promise<boolean> {
    return this.uploadArea.isVisible();
  }

  async getFileSizeLimit(): Promise<string> {
    return (await this.fileSizeLimit.textContent()) || '';
  }

  async isErrorAlertVisible(): Promise<boolean> {
    return this.errorAlert.isVisible();
  }

  async getErrorMessage(): Promise<string> {
    return (await this.errorAlert.textContent()) || '';
  }

  async uploadFile(filePath: string): Promise<void> {
    const fileInput = this.page.locator('input[type="file"]');
    await fileInput.setInputFiles(filePath);
  }
}
