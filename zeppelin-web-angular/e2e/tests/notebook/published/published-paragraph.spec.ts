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
  createNotebookIfListEmpty,
  performLoginIfRequired,
  waitForZeppelinReady,
  PAGES
} from '../../../utils';

test.describe('Published Paragraph', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.PUBLISHED_PARAGRAPH);

  let publishedParagraphPage: PublishedParagraphPage;
  let testUtil: PublishedParagraphTestUtil;
  let testNotebook: { noteId: string; paragraphId: string };

  test.beforeEach(async ({ page }) => {
    publishedParagraphPage = new PublishedParagraphPage(page);
    await page.goto('/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
    await createNotebookIfListEmpty(page);

    // Handle the welcome modal if it appears
    const cancelButton = page.locator('.ant-modal-root button', { hasText: 'Cancel' });
    if ((await cancelButton.count()) > 0) {
      await cancelButton.click();
    }

    testUtil = new PublishedParagraphTestUtil(page);
    testNotebook = await testUtil.createTestNotebook();
  });

  test.afterEach(async () => {
    if (testNotebook?.noteId) {
      await testUtil.deleteTestNotebook(testNotebook.noteId);
    }
  });

  test.describe('Error Handling', () => {
    test('should show error modal when notebook does not exist', async ({ page }) => {
      const nonExistentIds = testUtil.generateNonExistentIds();

      await publishedParagraphPage.navigateToPublishedParagraph(nonExistentIds.noteId, nonExistentIds.paragraphId);

      const modal = page.locator('.ant-modal:has-text("Notebook not found")').last();
      const isModalVisible = await modal.isVisible({ timeout: 10000 });

      if (isModalVisible) {
        const modalContent = await modal.textContent();
        expect(modalContent?.toLowerCase()).toContain('not found');
      } else {
        await expect(page).toHaveURL(/\/#\/$/, { timeout: 5000 });
      }
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

  test.describe('Valid Paragraph Display', () => {
    test('should enter published paragraph by clicking', async () => {
      await testUtil.verifyClickLinkThisParagraphBehavior(testNotebook.noteId, testNotebook.paragraphId);
    });

    test('should enter published paragraph by URL', async ({ page }) => {
      await page.goto(`/#/notebook/${testNotebook.noteId}/paragraph/${testNotebook.paragraphId}`);
      await page.waitForLoadState('networkidle');
      await expect(page).toHaveURL(`/#/notebook/${testNotebook.noteId}/paragraph/${testNotebook.paragraphId}`, {
        timeout: 10000
      });
    });
  });

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

    const modal = publishedParagraphPage.confirmationModal;
    await expect(modal).toBeVisible();
    await expect(publishedParagraphPage.modalTitle).toHaveText(
      'There is no result. Would you like to run this paragraph?'
    );

    await publishedParagraphPage.runButton.click();
    await expect(modal).toBeHidden();
  });
});
