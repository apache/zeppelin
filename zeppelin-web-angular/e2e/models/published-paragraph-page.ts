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
import { navigateToNotebookWithFallback } from '../utils';
import { BasePage } from './base-page';

export class PublishedParagraphPage extends BasePage {
  readonly dynamicForms: Locator;
  readonly paragraphResult: Locator;
  readonly errorModalContent: Locator;
  readonly errorModalOkButton: Locator;
  readonly confirmationModal: Locator;

  constructor(page: Page) {
    super(page);
    this.dynamicForms = page.locator('zeppelin-notebook-paragraph-dynamic-forms');
    this.paragraphResult = page.locator('zeppelin-notebook-paragraph-result');
    this.errorModalContent = this.page.locator('.ant-modal-body', { hasText: 'Paragraph Not Found' }).last();
    this.errorModalOkButton = page.getByRole('button', { name: 'OK' }).last();
    this.confirmationModal = page.locator('div.ant-modal-confirm').last();
  }

  async navigateToNotebook(noteId: string): Promise<void> {
    await navigateToNotebookWithFallback(this.page, noteId);
  }

  async navigateToPublishedParagraph(noteId: string, paragraphId: string): Promise<void> {
    await this.navigateToRoute(`/notebook/${noteId}/paragraph/${paragraphId}`);
  }

  async getErrorModalContent(): Promise<string> {
    return await this.getElementText(this.errorModalContent);
  }

  async clickErrorModalOk(): Promise<void> {
    await this.errorModalOkButton.click({ timeout: 15000 });
  }

  async isOnHomePage(): Promise<boolean> {
    try {
      await this.welcomeTitle.waitFor({ state: 'visible', timeout: 5000 });
      return true;
    } catch (e) {
      return false;
    }
  }
}
