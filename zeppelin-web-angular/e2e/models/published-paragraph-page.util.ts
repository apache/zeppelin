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
import { NOTEBOOK_PATTERNS } from '../utils';
import { NotebookUtil } from './notebook.util';
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

  async createTestNotebook(): Promise<{ noteId: string; paragraphId: string }> {
    const notebookName = `Test Notebook ${Date.now()}`;

    // Use existing NotebookUtil to create notebook
    await this.notebookUtil.createNotebook(notebookName);

    // Wait for navigation to notebook page - try direct wait first, then fallback
    let noteId = '';
    try {
      await this.page.waitForURL(/\/notebook\/[^\/\?]+/, { timeout: 30000 });
    } catch (error) {
      // Extract noteId if available, then use fallback navigation
      const currentUrl = this.page.url();
      let tempNoteId = '';

      if (currentUrl.includes('/notebook/')) {
        const match = currentUrl.match(/\/notebook\/([^\/\?]+)/);
        tempNoteId = match ? match[1] : '';
      }

      if (tempNoteId) {
        // Use the reusable fallback navigation function
        await navigateToNotebookWithFallback(this.page, tempNoteId, notebookName);
      } else {
        // Manual fallback if no noteId found - try to find notebook via API first
        const foundNoteId = await this.page.evaluate(async targetName => {
          try {
            const response = await fetch('/api/notebook');
            const data = await response.json();
            if (data.body && Array.isArray(data.body)) {
              // Find the most recently created notebook with matching name pattern
              const testNotebooks = data.body.filter(
                (nb: { path?: string }) => nb.path && nb.path.includes(targetName)
              );
              if (testNotebooks.length > 0) {
                // Sort by creation time and get the latest
                testNotebooks.sort(
                  (a: { dateUpdated?: string }, b: { dateUpdated?: string }) =>
                    new Date(b.dateUpdated || 0).getTime() - new Date(a.dateUpdated || 0).getTime()
                );
                return testNotebooks[0].id;
              }
            }
          } catch (apiError) {
            console.log('API call failed:', apiError);
          }
          return null;
        }, notebookName);

        if (foundNoteId) {
          console.log(`Found notebook ID via API: ${foundNoteId}`);
          await this.page.goto(`/#/notebook/${foundNoteId}`);
          await this.page.waitForLoadState('networkidle', { timeout: 15000 });
        } else {
          // Final fallback: try to find in the home page
          await this.page.goto('/');
          await this.page.waitForLoadState('networkidle', { timeout: 15000 });
          await this.page.waitForSelector('zeppelin-node-list', { timeout: 15000 });

          // Try to find any test notebook (not necessarily the exact one)
          const testNotebookLinks = this.page.locator(`a[href*="/notebook/"]`).filter({ hasText: /Test Notebook/ });
          const linkCount = await testNotebookLinks.count();

          if (linkCount > 0) {
            console.log(`Found ${linkCount} test notebooks, using the first one`);
            await testNotebookLinks.first().click();
            await this.page.waitForURL(/\/notebook\/[^\/\?]+/, { timeout: 20000 });
          } else {
            throw new Error(`No test notebooks found in the home page`);
          }
        }
      }
    }

    // Extract noteId from URL
    const url = this.page.url();
    const noteIdMatch = url.match(NOTEBOOK_PATTERNS.URL_EXTRACT_NOTEBOOK_ID_REGEX);
    if (!noteIdMatch) {
      throw new Error(`Failed to extract notebook ID from URL: ${url}`);
    }
    noteId = noteIdMatch[1];

    // Wait for notebook page to be fully loaded
    await this.page.waitForLoadState('networkidle', { timeout: 15000 });

    // Wait for paragraph elements to be available
    await this.page.locator('zeppelin-notebook-paragraph').first().waitFor({ state: 'visible', timeout: 15000 });

    // Get first paragraph ID with enhanced error handling
    const paragraphContainer = this.page.locator('zeppelin-notebook-paragraph').first();
    const dropdownTrigger = paragraphContainer.locator('a[nz-dropdown]');

    // Wait for dropdown to be clickable
    await dropdownTrigger.waitFor({ state: 'visible', timeout: 10000 });
    await dropdownTrigger.click();

    const paragraphLink = this.page.locator('li.paragraph-id a').first();
    await paragraphLink.waitFor({ state: 'attached', timeout: 10000 });

    const paragraphId = await paragraphLink.textContent();

    if (!paragraphId || !paragraphId.startsWith('paragraph_')) {
      throw new Error(`Failed to find a valid paragraph ID. Found: ${paragraphId}`);
    }

    // Navigate back to home with enhanced waiting
    await this.page.goto('/');
    await this.page.waitForLoadState('networkidle', { timeout: 30000 });

    // Wait for the loading indicator to disappear and home page to be ready
    try {
      await this.page.waitForFunction(
        () => {
          const loadingText = document.body.textContent || '';
          const hasWelcome = loadingText.includes('Welcome to Zeppelin');
          const noLoadingTicket = !loadingText.includes('Getting Ticket Data');
          return hasWelcome && noLoadingTicket;
        },
        { timeout: 20000 }
      );
    } catch {
      // Fallback: just check that we're on the home page and node list is available
      await this.page.waitForURL(/\/#\/$/, { timeout: 5000 });
      await this.page.waitForSelector('zeppelin-node-list', { timeout: 10000 });
    }

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

        // Wait for delete button to become visible after hover
        const deleteButtonLocator = treeNode.locator('i[nztype="delete"], i.anticon-delete');
        await expect(deleteButtonLocator).toBeVisible({ timeout: 5000 });

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
