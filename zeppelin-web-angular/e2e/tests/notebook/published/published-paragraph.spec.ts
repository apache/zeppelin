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
import { PublishedParagraphTestUtil } from '../../../models/published-paragraph-page.util';
import { addPageAnnotationBeforeEach, performLoginIfRequired, waitForZeppelinReady, PAGES } from '../../../utils';

test.describe('Published Paragraph', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.PUBLISHED_PARAGRAPH);

  let testUtil: PublishedParagraphTestUtil;
  let testNotebook: { noteId: string; paragraphId: string };

  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);

    testUtil = new PublishedParagraphTestUtil(page);
    testNotebook = await testUtil.openFirstNotebook();
  });

  test.describe('Error Handling', () => {
    test('should show error modal when notebook does not exist', async ({ page }) => {
      const nonExistentIds = testUtil.generateNonExistentIds();

      await page.goto(`/#/notebook/${nonExistentIds.noteId}/paragraph/${nonExistentIds.paragraphId}`);
      await page.waitForLoadState('networkidle');

      const modal = page.locator('.ant-modal');
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

      await page.goto(`/#/notebook/${nonExistentIds.noteId}/paragraph/${nonExistentIds.paragraphId}`);
      await page.waitForLoadState('networkidle');

      const modal = page.locator('.ant-modal');
      const isModalVisible = await modal.isVisible({ timeout: 5000 });

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
});
