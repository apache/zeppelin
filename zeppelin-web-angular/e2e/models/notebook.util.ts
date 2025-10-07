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
import { BasePage } from './base-page';
import { HomePage } from './home-page';

export class NotebookUtil extends BasePage {
  private homePage: HomePage;

  constructor(page: Page) {
    super(page);
    this.homePage = new HomePage(page);
  }

  async createNotebook(notebookName: string): Promise<void> {
    await this.homePage.navigateToHome();
    await this.homePage.createNewNoteButton.click();

    // Wait for the modal to appear and fill the notebook name
    const notebookNameInput = this.page.locator('input[ng-reflect-name="noteName"]');
    await expect(notebookNameInput).toBeVisible({ timeout: 10000 });

    // Fill notebook name
    await notebookNameInput.fill(notebookName);

    // Click the 'Create' button in the modal
    const createButton = this.page.locator('button', { hasText: 'Create' });
    await createButton.click();

    // Wait for the notebook to be created and navigate to it
    await this.page.waitForURL(`**/${notebookName}`);
    await this.waitForPageLoad();
  }
}
