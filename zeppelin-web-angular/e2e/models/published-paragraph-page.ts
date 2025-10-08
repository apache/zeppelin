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

export class PublishedParagraphPage extends BasePage {
  readonly publishedParagraphContainer: Locator;
  readonly dynamicForms: Locator;
  readonly paragraphResult: Locator;
  readonly errorModal: Locator;
  readonly errorModalTitle: Locator;
  readonly errorModalContent: Locator;
  readonly errorModalOkButton: Locator;
  readonly confirmationModal: Locator;
  readonly modalTitle: Locator;
  readonly runButton: Locator;

  constructor(page: Page) {
    super(page);
    this.publishedParagraphContainer = page.locator('zeppelin-publish-paragraph');
    this.dynamicForms = page.locator('zeppelin-notebook-paragraph-dynamic-forms');
    this.paragraphResult = page.locator('zeppelin-notebook-paragraph-result');
    this.errorModal = page.locator('.ant-modal');
    this.errorModalTitle = page.locator('.ant-modal-title');
    this.errorModalContent = this.page.locator('.ant-modal-body', { hasText: 'Paragraph Not Found' }).last();
    this.errorModalOkButton = page.getByRole('button', { name: 'OK' }).last();
    this.confirmationModal = page.locator('div.ant-modal-confirm').last();
    this.modalTitle = this.confirmationModal.locator('.ant-modal-confirm-title');
    this.runButton = this.confirmationModal.locator('button', { hasText: 'Run' });
  }

  async navigateToNotebook(noteId: string): Promise<void> {
    await this.page.goto(`/#/notebook/${noteId}`);
    await this.waitForPageLoad();
  }

  async navigateToPublishedParagraph(noteId: string, paragraphId: string): Promise<void> {
    await this.page.goto(`/#/notebook/${noteId}/paragraph/${paragraphId}`);
    await this.waitForPageLoad();
  }

  async isPublishedParagraphVisible(): Promise<boolean> {
    return await this.publishedParagraphContainer.isVisible();
  }

  async getErrorModalTitle(): Promise<string> {
    return (await this.errorModalTitle.textContent()) || '';
  }

  async getErrorModalContent(): Promise<string> {
    return (await this.errorModalContent.textContent()) || '';
  }

  async clickErrorModalOk(): Promise<void> {
    await this.errorModalOkButton.click();
  }

  async isDynamicFormsVisible(): Promise<boolean> {
    return await this.dynamicForms.isVisible();
  }

  async isResultVisible(): Promise<boolean> {
    return await this.paragraphResult.isVisible();
  }

  async getCurrentUrl(): Promise<string> {
    return this.page.url();
  }

  async isOnHomePage(): Promise<boolean> {
    const url = await this.getCurrentUrl();
    return url.includes('/#/') && !url.includes('/notebook/');
  }
}
