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
import { PublishedParagraphPage } from './published-paragraph-page';

export class PublishedParagraphTestUtil {
  private page: Page;
  private publishedParagraphPage: PublishedParagraphPage;

  constructor(page: Page) {
    this.page = page;
    this.publishedParagraphPage = new PublishedParagraphPage(page);
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
    await expect(paragraphElement.locator('[data-testid="paragraph-result"]')).toBeHidden();

    await this.publishedParagraphPage.navigateToPublishedParagraph(noteId, paragraphId);

    const modal = this.publishedParagraphPage.confirmationModal;
    await expect(modal).toBeVisible();

    // Check for the new enhanced modal content
    const modalTitle = this.page.locator('.ant-modal-confirm-title, .ant-modal-title');
    await expect(modalTitle).toContainText('Run Paragraph?');

    // Check that code preview is shown
    const modalContent = this.page.locator('.ant-modal-confirm-content, .ant-modal-body').first();
    await expect(modalContent).toContainText('This paragraph contains the following code:');
    await expect(modalContent).toContainText('Would you like to execute this code?');

    // Verify that the code preview area exists
    const codePreview = modalContent.locator('pre, code, .code-preview, .highlight, [class*="code"]').first();
    await expect(codePreview).toBeVisible();

    // Check for Run and Cancel buttons
    const runButton = this.page.locator('.ant-modal button:has-text("Run"), .ant-btn:has-text("Run")');
    const cancelButton = this.page.locator('.ant-modal button:has-text("Cancel"), .ant-btn:has-text("Cancel")');
    await expect(runButton).toBeVisible();
    await expect(cancelButton).toBeVisible();

    // Click the Run button in the modal
    await runButton.click();
    await expect(modal).toBeHidden();
  }

  async verifyNonExistentParagraphError(validNoteId: string, invalidParagraphId: string): Promise<void> {
    await this.publishedParagraphPage.navigateToPublishedParagraph(validNoteId, invalidParagraphId);

    // Expect a specific error modal - fail fast if not found
    const errorModal = this.page.locator('.ant-modal', { hasText: /Paragraph Not Found|not found|Error/i });
    await expect(errorModal).toBeVisible({ timeout: 10000 });

    // Verify modal content includes the invalid paragraph ID
    const content = await this.publishedParagraphPage.getErrorModalContent();
    expect(content).toBeDefined();
    expect(content).toContain(invalidParagraphId);

    await this.publishedParagraphPage.clickErrorModalOk();

    // Wait for redirect to home page instead of checking modal state
    await expect(this.page).toHaveURL(/\/#\/$/, { timeout: 10000 });

    expect(await this.publishedParagraphPage.isOnHomePage()).toBe(true);
  }

  async verifyClickLinkThisParagraphBehavior(noteId: string, paragraphId: string): Promise<void> {
    // 1. Navigate to the normal notebook view
    await this.page.goto(`/#/notebook/${noteId}`);
    await this.page.waitForLoadState('networkidle');

    // 2. Find the correct paragraph result element and go up to the parent paragraph container
    // First try with data-testid, then fallback to first paragraph if not found
    let paragraphElement = this.page.locator(`zeppelin-notebook-paragraph[data-testid="${paragraphId}"]`);

    if ((await paragraphElement.count()) === 0) {
      // Fallback to first paragraph if specific ID not found
      paragraphElement = this.page.locator('zeppelin-notebook-paragraph').first();
    }

    await expect(paragraphElement).toBeVisible({ timeout: 10000 });

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

  generateNonExistentIds(): { noteId: string; paragraphId: string } {
    const timestamp = Date.now();
    return {
      noteId: `NON_EXISTENT_NOTEBOOK_${timestamp}`,
      paragraphId: `NON_EXISTENT_PARAGRAPH_${timestamp}`
    };
  }
}
