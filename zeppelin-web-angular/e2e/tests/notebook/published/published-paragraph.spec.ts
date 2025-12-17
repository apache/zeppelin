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

import { expect, test } from '@playwright/test';
import { PublishedParagraphPage } from 'e2e/models/published-paragraph-page';
import { PublishedParagraphTestUtil } from '../../../models/published-paragraph-page.util';
import {
  addPageAnnotationBeforeEach,
  performLoginIfRequired,
  waitForNotebookLinks,
  waitForZeppelinReady,
  PAGES,
  createTestNotebook
} from '../../../utils';

test.describe('Published Paragraph', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.PUBLISHED_PARAGRAPH);

  let publishedParagraphPage: PublishedParagraphPage;
  let testUtil: PublishedParagraphTestUtil;
  let testNotebook: { noteId: string; paragraphId: string };

  test.beforeEach(async ({ page }) => {
    publishedParagraphPage = new PublishedParagraphPage(page);
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
    await waitForNotebookLinks(page);

    const cancelButton = page.locator('.ant-modal-root button', { hasText: 'Cancel' });
    if ((await cancelButton.count()) > 0) {
      await cancelButton.click();
      await cancelButton.waitFor({ state: 'detached', timeout: 5000 });
    }

    testUtil = new PublishedParagraphTestUtil(page);
    testNotebook = await createTestNotebook(page);
  });

  test.describe('Error Handling', () => {
    test('should show error modal when notebook does not exist', async ({ page }) => {
      const nonExistentIds = testUtil.generateNonExistentIds();

      await publishedParagraphPage.navigateToPublishedParagraph(nonExistentIds.noteId, nonExistentIds.paragraphId);

      // Directly assert that the modal appears and contains the expected text
      const modal = page.locator('.ant-modal:has-text("Notebook not found")').last();
      await expect(modal).toBeVisible({ timeout: 10000 }); // Expect the modal to be visible

      const modalContent = await modal.textContent();
      expect(modalContent?.toLowerCase()).toContain('not found');
    });

    test('should show error modal when paragraph does not exist in valid notebook', async () => {
      const validNoteId = testNotebook.noteId;
      const nonExistentParagraphId = testUtil.generateNonExistentIds().paragraphId;

      await testUtil.verifyNonExistentParagraphError(validNoteId, nonExistentParagraphId);
    });

    test('should redirect to home page after error modal dismissal', async ({ page }) => {
      const nonExistentIds = testUtil.generateNonExistentIds();

      await publishedParagraphPage.navigateToPublishedParagraph(nonExistentIds.noteId, nonExistentIds.paragraphId);

      const modal = page.locator('.ant-modal', { hasText: 'Paragraph Not Found' }).last();
      const isModalVisible = await modal.isVisible();

      if (isModalVisible) {
        const okButton = page.locator('button:has-text("OK"), button:has-text("확인"), [role="button"]:has-text("OK")');
        await okButton.click();

        await expect(page).toHaveURL(/\/#\/$/, { timeout: 10000 });
      } else {
        await expect(page).toHaveURL(/\/#\/$/, { timeout: 5000 });
      }
    });
  });

  test.describe('Navigation and URL Patterns', () => {
    test('should enter published paragraph by clicking link', async () => {
      await testUtil.verifyClickLinkThisParagraphBehavior(testNotebook.noteId, testNotebook.paragraphId);
    });

    test('should enter published paragraph by direct URL navigation', async ({ page }) => {
      await page.goto(`/#/notebook/${testNotebook.noteId}/paragraph/${testNotebook.paragraphId}`);
      await page.waitForLoadState('networkidle');
      await expect(page).toHaveURL(`/#/notebook/${testNotebook.noteId}/paragraph/${testNotebook.paragraphId}`, {
        timeout: 10000
      });
    });

    test('should display published paragraph component and preserve URL context after modal interaction', async ({
      page
    }) => {
      const { noteId, paragraphId } = testNotebook;

      // Given: Navigate to a specific paragraph's published URL
      await page.goto(`/#/notebook/${noteId}/paragraph/${paragraphId}`);
      await page.waitForLoadState('networkidle');

      // Then: URL should correctly preserve both notebook and paragraph identifiers
      await expect(page).toHaveURL(new RegExp(`/notebook/${noteId}/paragraph/${paragraphId}`), { timeout: 15000 });

      // Verify URL contains the specific notebook and paragraph context
      expect(page.url()).toContain(noteId);
      expect(page.url()).toContain(paragraphId);

      // Then: Published paragraph component should be loaded (indicating published mode is active)
      const publishedContainer = page.locator('zeppelin-publish-paragraph');
      await publishedContainer.waitFor({ state: 'attached', timeout: 10000 });

      // Then: Confirmation modal should appear for paragraph execution
      const modal = page.locator('.ant-modal');
      await expect(modal).toBeVisible({ timeout: 5000 });

      // Handle the execution confirmation to complete the published mode setup
      const runModalButton = modal.locator('button:has-text("Run")');
      await expect(runModalButton).toBeVisible();
      await runModalButton.click();
      await expect(modal).not.toBeVisible({ timeout: 10000 });

      // Then: Published container should remain attached and page should be in published mode
      await expect(publishedContainer).toBeAttached({ timeout: 10000 });

      // Verify we're in published mode by checking for the published component
      const isPublishedMode = await page.evaluate(() => document.querySelector('zeppelin-publish-paragraph') !== null);
      expect(isPublishedMode).toBe(true);

      // Verify only the specific paragraph is displayed, not the entire notebook
      const notebookContainer = page.locator('zeppelin-notebook');
      const paragraphContainer = page.locator('zeppelin-publish-paragraph');

      // Published component should be present
      await expect(paragraphContainer).toBeAttached();
      // Full notebook editing interface should not be visible
      const isFullNotebookMode = await notebookContainer.isVisible().catch(() => false);
      expect(isFullNotebookMode).toBe(false);
    });
  });

  test.describe('Published Mode Functionality', () => {
    test('should hide editing controls in published mode', async ({ page }) => {
      const { noteId, paragraphId } = testNotebook;

      await page.goto(`/#/notebook/${noteId}/paragraph/${paragraphId}`);
      await page.waitForLoadState('networkidle');

      // In published mode, code editor and control panel should be hidden
      const codeEditor = page.locator('zeppelin-notebook-paragraph-code-editor');
      const controlPanel = page.locator('zeppelin-notebook-paragraph-control');

      await expect(codeEditor).toBeHidden();
      await expect(controlPanel).toBeHidden();
    });
  });

  test.describe('Confirmation Modal and Execution', () => {
    test('should show confirmation modal and allow running the paragraph', async ({ page }) => {
      const { noteId, paragraphId } = testNotebook;

      await publishedParagraphPage.navigateToNotebook(noteId);

      const paragraphElement = page.locator('zeppelin-notebook-paragraph').first();
      const paragraphResult = paragraphElement.locator('zeppelin-notebook-paragraph-result');

      // Only clear output if result exists
      if (await paragraphResult.isVisible()) {
        const settingsButton = paragraphElement.locator('a[nz-dropdown]');
        await settingsButton.click();

        const clearOutputButton = page.locator('li.list-item:has-text("Clear output")');
        await clearOutputButton.click();
        await expect(paragraphResult).toBeHidden();
      }

      await publishedParagraphPage.navigateToPublishedParagraph(noteId, paragraphId);

      await expect(page).toHaveURL(new RegExp(`/paragraph/${paragraphId}`));

      const modal = publishedParagraphPage.confirmationModal;
      await expect(modal).toBeVisible();

      // Check for the enhanced modal content
      await expect(publishedParagraphPage.modalTitle).toHaveText('Run Paragraph?');

      // Verify that the modal shows code preview
      const modalContent = publishedParagraphPage.confirmationModal.locator('.ant-modal-confirm-content');
      await expect(modalContent).toContainText('This paragraph contains the following code:');
      await expect(modalContent).toContainText('Would you like to execute this code?');

      // Click the Run button in the modal (OK button in confirmation modal)
      const runButton = modal.locator('.ant-modal-confirm-btns .ant-btn-primary');
      await expect(runButton).toBeVisible();
      await runButton.click();
      await expect(modal).toBeHidden();
    });

    test('should show confirmation modal for paragraphs without results', async () => {
      const { noteId, paragraphId } = testNotebook;

      // Test confirmation modal for paragraph without results
      await testUtil.testConfirmationModalForNoResultParagraph({ noteId, paragraphId });
    });
  });
});
