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

      // Enhanced wait for page to be ready and button to be visible
      await this.page.waitForLoadState('networkidle', { timeout: 45000 });

      // Wait for either zeppelin-node-list or the create button to be available
      try {
        await this.page.waitForSelector('zeppelin-node-list a, button[nz-button]', { timeout: 45000 });
      } catch (selectorError) {
        console.warn('zeppelin-node-list not found, checking for create button directly');
      }

      await expect(this.homePage.createNewNoteButton).toBeVisible({ timeout: 45000 });

      // Wait for button to be ready for interaction with additional stability checks
      await this.page.waitForLoadState('domcontentloaded');
      // Wait for button to be stable and clickable
      await this.homePage.createNewNoteButton.waitFor({ state: 'attached', timeout: 10000 });
      await this.homePage.createNewNoteButton.waitFor({ state: 'visible', timeout: 10000 });

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
        await this.page.waitForURL(url => url.toString().includes('/notebook/'), { timeout: 60000 });
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
