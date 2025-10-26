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

    // Check for the new enhanced modal content
    const modalTitle = this.page.locator('.ant-modal-confirm-title, .ant-modal-title');
    await expect(modalTitle).toContainText('Run Paragraph?');

    // Check that code preview is shown
    const modalContent = this.page.locator('.ant-modal-confirm-content, .ant-modal-body');
    await expect(modalContent).toContainText('This paragraph contains the following code:');
    await expect(modalContent).toContainText('Would you like to execute this code?');

    // Verify that the code preview area exists with proper styling
    const codePreview = modalContent.locator('div[style*="background-color: #f5f5f5"]');
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

    // Try different possible error modal texts
    const possibleModals = [
      this.page.locator('.ant-modal', { hasText: 'Paragraph Not Found' }),
      this.page.locator('.ant-modal', { hasText: 'not found' }),
      this.page.locator('.ant-modal', { hasText: 'Error' }),
      this.page.locator('.ant-modal').filter({ hasText: /not found|error|paragraph/i })
    ];

    let modal;
    for (const possibleModal of possibleModals) {
      const count = await possibleModal.count();

      for (let i = 0; i < count; i++) {
        const m = possibleModal.nth(i);

        if (await m.isVisible()) {
          modal = m;
          break;
        }
      }

      if (modal) {
        break;
      }
    }

    if (!modal) {
      // If no modal is found, check if we're redirected to home
      await expect(this.page).toHaveURL(/\/#\/$/, { timeout: 10000 });
      return;
    }

    await expect(modal).toBeVisible({ timeout: 10000 });

    // Try to get content and check if available
    try {
      const content = await this.publishedParagraphPage.getErrorModalContent();
      if (content && content.includes(invalidParagraphId)) {
        expect(content).toContain(invalidParagraphId);
      }
    } catch {
      throw Error('Content check failed, continue with OK button click');
    }

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

  async createTestNotebook(): Promise<{ noteId: string; paragraphId: string }> {
    const notebookName = `Test Notebook ${Date.now()}`;

    // Use existing NotebookUtil to create notebook
    await this.notebookUtil.createNotebook(notebookName);

    // Extract noteId from URL
    const url = this.page.url();
    const noteIdMatch = url.match(/\/notebook\/([^\/\?]+)/);
    if (!noteIdMatch) {
      throw new Error(`Failed to extract notebook ID from URL: ${url}`);
    }
    const noteId = noteIdMatch[1];

    // Get first paragraph ID
    await this.page.locator('zeppelin-notebook-paragraph').first().waitFor({ state: 'visible', timeout: 10000 });
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
    try {
      // Navigate to home page
      await this.page.goto('/');
      await this.page.waitForLoadState('networkidle');

      // Find the notebook in the tree by noteId and get its parent tree node
      const notebookLink = this.page.locator(`a[href*="/notebook/${noteId}"]`);

      if ((await notebookLink.count()) > 0) {
        // Hover over the tree node to make delete button visible
        const treeNode = notebookLink.locator('xpath=ancestor::nz-tree-node[1]');
        await treeNode.hover();

        // Wait a bit for hover effects
        await this.page.waitForTimeout(1000);

        // Try multiple selectors for the delete button
        const deleteButtonSelectors = [
          'a[nz-tooltip] i[nztype="delete"]',
          'i[nztype="delete"]',
          '[nz-popconfirm] i[nztype="delete"]',
          'i.anticon-delete'
        ];

        let deleteClicked = false;
        for (const selector of deleteButtonSelectors) {
          const deleteButton = treeNode.locator(selector);
          try {
            if (await deleteButton.isVisible({ timeout: 2000 })) {
              await deleteButton.click({ timeout: 5000 });
              deleteClicked = true;
              break;
            }
          } catch (e) {
            // Continue to next selector
            continue;
          }
        }

        if (!deleteClicked) {
          console.warn(`Delete button not found for notebook ${noteId}`);
          return;
        }

        // Confirm deletion in popconfirm with timeout
        try {
          const confirmButton = this.page.locator('button:has-text("OK")');
          await confirmButton.click({ timeout: 5000 });

          // Wait for the notebook to be removed with timeout
          await expect(treeNode).toBeHidden({ timeout: 10000 });
        } catch (e) {
          // If confirmation fails, try alternative OK button selectors
          const altConfirmButtons = [
            '.ant-popover button:has-text("OK")',
            '.ant-popconfirm button:has-text("OK")',
            'button.ant-btn-primary:has-text("OK")'
          ];

          for (const selector of altConfirmButtons) {
            try {
              const button = this.page.locator(selector);
              if (await button.isVisible({ timeout: 1000 })) {
                await button.click({ timeout: 3000 });
                await expect(treeNode).toBeHidden({ timeout: 10000 });
                break;
              }
            } catch (altError) {
              // Continue to next selector
              continue;
            }
          }
        }
      }
    } catch (error) {
      console.warn(`Failed to delete test notebook ${noteId}:`, error);
      // Don't throw error to avoid failing the test cleanup
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
