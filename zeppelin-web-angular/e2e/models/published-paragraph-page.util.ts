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
import { NotebookUtil } from './notebook.util';
import { PublishedParagraphPage } from './published-paragraph-page';

export class PublishedParagraphTestUtil {
  private page: Page;
  private publishedParagraphPage: PublishedParagraphPage;
  private notebookUtil: NotebookUtil;

  constructor(page: Page) {
    this.page = page;
    this.publishedParagraphPage = new PublishedParagraphPage(page);
    this.notebookUtil = new NotebookUtil(page);
  }

  async testConfirmationModalForNoResultParagraph({
    noteId,
    paragraphId
  }: {
    noteId: string;
    paragraphId: string;
  }): Promise<void> {
    await this.publishedParagraphPage.navigateToNotebook(noteId);

    const paragraphElement = this.page.locator('zeppelin-notebook-paragraph').first();

    const settingsButton = paragraphElement.locator('a[nz-dropdown]');
    await settingsButton.click();

    const clearOutputButton = this.page.locator('li.list-item:has-text("Clear output")');
    await clearOutputButton.click();
    await expect(paragraphElement.locator('zeppelin-notebook-paragraph-result')).toBeHidden();

    await this.publishedParagraphPage.navigateToPublishedParagraph(noteId, paragraphId);

    const modal = this.publishedParagraphPage.confirmationModal;
    await expect(modal).toBeVisible();
    await expect(this.publishedParagraphPage.modalTitle).toHaveText(
      'There is no result. Would you like to run this paragraph?'
    );

    await this.publishedParagraphPage.runButton.click();
    await expect(modal).toBeHidden();
  }

  async verifyNonExistentParagraphError(validNoteId: string, invalidParagraphId: string): Promise<void> {
    await this.publishedParagraphPage.navigateToPublishedParagraph(validNoteId, invalidParagraphId);

    const modal = this.page.locator('.ant-modal', { hasText: 'Paragraph Not Found' }).last();
    await expect(modal).toBeVisible({ timeout: 10000 });

    await expect(modal).toContainText('Paragraph Not Found');

    const content = await this.publishedParagraphPage.getErrorModalContent();
    expect(content).toContain(invalidParagraphId);
    expect(content).toContain('does not exist in notebook');
    expect(content).toContain('redirected to the home page');

    await this.publishedParagraphPage.clickErrorModalOk();

    await expect(this.publishedParagraphPage.errorModal).toBeHidden();

    expect(await this.publishedParagraphPage.isOnHomePage()).toBe(true);
  }

  async verifyClickLinkThisParagraphBehavior(noteId: string, paragraphId: string): Promise<void> {
    // 1. Navigate to the normal notebook view
    await this.page.goto(`/#/notebook/${noteId}`);
    await this.page.waitForLoadState('networkidle');

    // 2. Find the correct paragraph result element and go up to the parent paragraph container
    const paragraphElement = this.page.locator(`zeppelin-notebook-paragraph[data-testid="${paragraphId}"]`);
    await expect(paragraphElement).toBeVisible();

    // 3. Click the settings button to open the dropdown
    const settingsButton = paragraphElement.locator('a[nz-dropdown]');
    await settingsButton.click();

    // 4. Click "Link this paragraph" in the dropdown menu
    const linkParagraphButton = this.page.locator('li.list-item:has-text("Link this paragraph")');
    await expect(linkParagraphButton).toBeVisible();

    // 5. Handle the new page/tab that opens
    const [newPage] = await Promise.all([this.page.waitForEvent('popup'), linkParagraphButton.click()]);
    await newPage.waitForLoadState();

    // 6. Verify the new page URL shows published paragraph (not redirected)
    await expect(newPage).toHaveURL(new RegExp(`/notebook/${noteId}/paragraph/${paragraphId}`), { timeout: 10000 });

    const codeEditor = newPage.locator('zeppelin-notebook-paragraph-code-editor');
    await expect(codeEditor).toBeHidden();

    const controlPanel = newPage.locator('zeppelin-notebook-paragraph-control');
    await expect(controlPanel).toBeHidden();
  }

  async createTestNotebook(): Promise<{ noteId: string; paragraphId: string }> {
    const notebookName = `Test Notebook ${Date.now()}`;

    // Use existing NotebookUtil to create notebook
    await this.notebookUtil.createNotebook(notebookName);

    // Extract noteId from URL
    const url = this.page.url();
    const noteIdMatch = url.match(/\/notebook\/([^\/\?]+)/);
    if (!noteIdMatch) {
      throw new Error('Failed to extract notebook ID from URL: ' + url);
    }
    const noteId = noteIdMatch[1];

    // Get first paragraph ID
    await this.page
      .locator('zeppelin-notebook-paragraph')
      .first()
      .waitFor({ state: 'visible', timeout: 10000 });
    const paragraphContainer = this.page.locator('zeppelin-notebook-paragraph').first();
    const dropdownTrigger = paragraphContainer.locator('a[nz-dropdown]');
    await dropdownTrigger.click();

    const paragraphLink = this.page.locator('li.paragraph-id a').first();
    await paragraphLink.waitFor({ state: 'attached', timeout: 5000 });

    const paragraphId = await paragraphLink.textContent();

    if (!paragraphId || !paragraphId.startsWith('paragraph_')) {
      throw new Error(`Failed to find a valid paragraph ID. Found: ${paragraphId}`);
    }

    // Navigate back to home
    await this.page.goto('/');
    await this.page.waitForLoadState('networkidle');
    await this.page.waitForSelector('text=Welcome to Zeppelin!', { timeout: 5000 });

    return { noteId, paragraphId };
  }

  async deleteTestNotebook(noteId: string): Promise<void> {
    // Navigate to home page
    await this.page.goto('/');
    await this.page.waitForLoadState('networkidle');

    // Find the notebook in the tree by noteId and get its parent tree node
    const notebookLink = this.page.locator(`a[href*="/notebook/${noteId}"]`);

    if ((await notebookLink.count()) > 0) {
      // Hover over the tree node to make delete button visible
      const treeNode = notebookLink.locator('xpath=ancestor::nz-tree-node[1]');
      await treeNode.hover();

      // Find and click the delete button
      const deleteButton = treeNode.locator('a[nz-tooltip] i[nztype="delete"]');
      await expect(deleteButton).toBeVisible();
      await deleteButton.click();

      // Confirm deletion in popconfirm
      const confirmButton = this.page.locator('button:has-text("OK")');
      await confirmButton.click();

      // Wait for the notebook to be removed
      await expect(treeNode).toBeHidden();
    }
  }

  generateNonExistentIds(): { noteId: string; paragraphId: string } {
    const timestamp = Date.now();
    return {
      noteId: `NON_EXISTENT_NOTEBOOK_${timestamp}`,
      paragraphId: `NON_EXISTENT_PARAGRAPH_${timestamp}`
    };
  }
}
