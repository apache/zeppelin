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
    try {
      await this.homePage.navigateToHome();

      // WebKit-specific handling for loading issues
      const browserName = this.page.context().browser()?.browserType().name();
      if (browserName === 'webkit') {
        // Wait for Zeppelin to finish loading ticket data in WebKit
        await this.page.waitForFunction(() => !document.body.textContent?.includes('Getting Ticket Data'), {
          timeout: 60000
        });

        // Wait for home page content to load
        await this.page.waitForLoadState('networkidle', { timeout: 30000 });

        // Wait specifically for the notebook list element
        await this.page.waitForSelector('zeppelin-node-list', { timeout: 45000 });
      }

      await expect(this.homePage.notebookList).toBeVisible({ timeout: 45000 });
      await expect(this.homePage.createNewNoteButton).toBeVisible({ timeout: 45000 });
      await this.homePage.createNewNoteButton.click({ timeout: 30000 });

      // Wait for the modal to appear and fill the notebook name
      const notebookNameInput = this.page.locator('input[name="noteName"]');
      await expect(notebookNameInput).toBeVisible({ timeout: 30000 });

      // Fill notebook name
      await notebookNameInput.fill(notebookName);

      // Click the 'Create' button in the modal
      const createButton = this.page.locator('button', { hasText: 'Create' });
      await expect(createButton).toBeVisible({ timeout: 30000 });
      await createButton.click({ timeout: 30000 });

      // Wait for the notebook to be created and navigate to it with enhanced error handling
      try {
        await this.page.waitForURL(url => url.toString().includes('/notebook/'), { timeout: 90000 });
        const notebookTitleLocator = this.page.locator('.notebook-title-editor');
        await expect(notebookTitleLocator).toHaveText(notebookName, { timeout: 15000 });
      } catch (urlError) {
        console.warn('URL change timeout, checking current URL:', this.page.url());
        // If URL didn't change as expected, check if we're already on a notebook page
        if (!this.page.url().includes('/notebook/')) {
          throw new Error(`Failed to navigate to notebook page. Current URL: ${this.page.url()}`);
        }
      }
      await this.waitForPageLoad();
    } catch (error) {
      console.error('Failed to create notebook:', error);
      throw error;
    }
  }
}
