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

    if ((await publishedParagraphPage.cancelButton.count()) > 0) {
      await publishedParagraphPage.cancelButton.click();
      await publishedParagraphPage.cancelButton.waitFor({ state: 'detached', timeout: 5000 });
    }

    testUtil = new PublishedParagraphTestUtil(page);
    testNotebook = await createTestNotebook(page);
  });

  test.describe('Error Handling', () => {
    test('should show error modal when notebook does not exist', async ({ page }) => {
      const nonExistentIds = testUtil.generateNonExistentIds();

      await publishedParagraphPage.navigateToPublishedParagraph(nonExistentIds.noteId, nonExistentIds.paragraphId);

      const modal = page.locator('.ant-modal', { hasText: /not found/i }).last();
      await expect(modal).toBeVisible({ timeout: 10000 });
      await expect(modal).toContainText(/not found/i);
    });

    test('should show error modal when paragraph does not exist in valid notebook', async ({ page }) => {
      const validNoteId = testNotebook.noteId;
      const nonExistentParagraphId = testUtil.generateNonExistentIds().paragraphId;

      await testUtil.navigateToPublishedParagraph(validNoteId, nonExistentParagraphId);

      const errorModal = page.locator('.ant-modal', { hasText: /Paragraph Not Found|not found|Error/i });
      await expect(errorModal).toBeVisible({ timeout: 10000 });
      await expect(errorModal).toContainText(nonExistentParagraphId);

      await testUtil.clickErrorModalOk();

      await expect(page).toHaveURL(/\/#\/$/, { timeout: 10000 });
    });

    test('should redirect to home page after error modal dismissal', async ({ page }) => {
      const nonExistentIds = testUtil.generateNonExistentIds();

      await publishedParagraphPage.navigateToPublishedParagraph(nonExistentIds.noteId, nonExistentIds.paragraphId);

      // Modal must appear — we navigated to non-existent IDs
      const modal = page.locator('.ant-modal').last();
      await expect(modal).toBeVisible({ timeout: 10000 });

      await publishedParagraphPage.okButton.click();

      await expect(page).toHaveURL(/\/#\/$/, { timeout: 10000 });
    });
  });

  test.describe('Navigation and URL Patterns', () => {
    test('should enter published paragraph by clicking link', async ({ page }) => {
      const { noteId, paragraphId } = testNotebook;

      await page.goto(`/#/notebook/${noteId}`);
      await page.waitForLoadState('networkidle');

      // createTestNotebook creates a single paragraph, so .first() is the target
      const paragraphElement = page.locator('zeppelin-notebook-paragraph').first();
      await expect(paragraphElement).toBeVisible({ timeout: 10000 });

      const settingsButton = paragraphElement.locator('a[nz-dropdown]');
      await settingsButton.click();

      const linkParagraphButton = page.locator('li.list-item', { hasText: 'Link this paragraph' });
      await expect(linkParagraphButton).toBeVisible();

      const [newPage] = await Promise.all([page.waitForEvent('popup'), linkParagraphButton.click()]);
      await newPage.waitForLoadState();

      await expect(newPage).toHaveURL(new RegExp(`/notebook/${noteId}/paragraph/${paragraphId}`), { timeout: 10000 });

      // Published mode hides editing controls
      await expect(newPage.locator('zeppelin-notebook-paragraph-code-editor')).toBeHidden();
      await expect(newPage.locator('zeppelin-notebook-paragraph-control')).toBeHidden();
    });

    test('should load published paragraph component by direct URL navigation', async ({ page }) => {
      await page.goto(`/#/notebook/${testNotebook.noteId}/paragraph/${testNotebook.paragraphId}`);
      await page.waitForLoadState('networkidle');

      await expect(page).toHaveURL(
        new RegExp(`/notebook/${testNotebook.noteId}/paragraph/${testNotebook.paragraphId}`)
      );
      await expect(page.locator('zeppelin-publish-paragraph')).toBeAttached({ timeout: 10000 });
    });

    test('should load published paragraph and keep component attached after modal confirmation', async ({ page }) => {
      const { noteId, paragraphId } = testNotebook;

      await page.goto(`/#/notebook/${noteId}/paragraph/${paragraphId}`);
      await page.waitForLoadState('networkidle');

      const publishedContainer = page.locator('zeppelin-publish-paragraph');
      await expect(publishedContainer).toBeAttached({ timeout: 10000 });

      // Confirmation modal should appear for paragraph execution
      const modal = page.locator('.ant-modal');
      await expect(modal).toBeVisible({ timeout: 20000 });

      await publishedParagraphPage.runButton.click();
      await expect(modal).not.toBeVisible({ timeout: 10000 });

      // Published container should remain attached after modal dismissal
      await expect(publishedContainer).toBeAttached({ timeout: 10000 });
    });

    test('should render React micro-frontend instead of Angular result component', async ({ page }) => {
      await test.step('Given I navigate to React mode URL', async () => {
        await page.goto(`/#/notebook/${testNotebook.noteId}/paragraph/${testNotebook.paragraphId}?react=true`);
        await waitForZeppelinReady(page);
      });

      await test.step('Then Angular result component should not be rendered', async () => {
        await expect(page.locator('zeppelin-notebook-paragraph-result')).toHaveCount(0, { timeout: 10000 });
      });

      await test.step('And React widget should be mounted in the container', async () => {
        // React mount() renders <div data-testid="react-published-paragraph"> or <Empty> (Alert)
        const reactContent = page.locator('[data-testid="react-published-paragraph"], .ant-alert');
        await expect(reactContent).toBeAttached({ timeout: 15000 });
      });
    });
  });

  test.describe('Published Mode Functionality', () => {
    test('should hide editing controls in published mode', async ({ page }) => {
      const { noteId, paragraphId } = testNotebook;

      await page.goto(`/#/notebook/${noteId}/paragraph/${paragraphId}`);
      await page.waitForLoadState('networkidle');

      await expect(page.locator('zeppelin-publish-paragraph')).toBeAttached({ timeout: 10000 });
      await expect(page.locator('zeppelin-notebook-paragraph-code-editor')).toBeHidden();
      await expect(page.locator('zeppelin-notebook-paragraph-control')).toBeHidden();
    });
  });

  test.describe('Confirmation Modal and Execution', () => {
    test('should show confirmation modal with code preview and allow running', async ({ page }) => {
      const { noteId, paragraphId } = testNotebook;

      await publishedParagraphPage.navigateToNotebook(noteId);

      // Verify paragraph has no results yet
      const paragraphElement = page.locator('zeppelin-notebook-paragraph').first();
      await expect(paragraphElement.locator('zeppelin-notebook-paragraph-result')).toBeHidden();

      await publishedParagraphPage.navigateToPublishedParagraph(noteId, paragraphId);

      await expect(page).toHaveURL(new RegExp(`/paragraph/${paragraphId}`));

      const modal = publishedParagraphPage.confirmationModal;
      await expect(modal).toBeVisible();

      // Modal title
      await expect(publishedParagraphPage.modalTitle).toHaveText('Run Paragraph?');

      // Code preview content
      const modalContent = modal.locator('.ant-modal-confirm-content');
      await expect(modalContent).toContainText('This paragraph contains the following code:');
      await expect(modalContent).toContainText('Would you like to execute this code?');

      // Code preview element
      const codePreview = modalContent.locator('pre, code, .code-preview, [class*="code"]').first();
      await expect(codePreview).toBeVisible();

      // Run and Cancel buttons
      await expect(publishedParagraphPage.runButton).toBeVisible();
      await expect(publishedParagraphPage.cancelButton).toBeVisible();

      // Execute and verify modal dismissal
      await publishedParagraphPage.runButton.click();
      await expect(modal).toBeHidden();
    });

    test('should show confirmation modal in React mode and allow running', async ({ page }) => {
      const { noteId, paragraphId } = testNotebook;

      await test.step('Given paragraph has no results in normal notebook view', async () => {
        await publishedParagraphPage.navigateToNotebook(noteId);

        const paragraphElement = page.locator('zeppelin-notebook-paragraph').first();
        await expect(paragraphElement.locator('zeppelin-notebook-paragraph-result')).toBeHidden();
      });

      await test.step('When I navigate to React mode published paragraph URL', async () => {
        await page.goto(`/#/notebook/${noteId}/paragraph/${paragraphId}?react=true`);
        await waitForZeppelinReady(page);
      });

      await test.step('Then confirmation modal should appear and allow execution', async () => {
        const modal = publishedParagraphPage.confirmationModal;
        await expect(modal).toBeVisible({ timeout: 30000 });

        await expect(publishedParagraphPage.modalTitle).toHaveText('Run Paragraph?');

        const modalContent = modal.locator('.ant-modal-confirm-content');
        await expect(modalContent).toContainText('This paragraph contains the following code:');
        await expect(modalContent).toContainText('Would you like to execute this code?');

        await publishedParagraphPage.runButton.click();
        await expect(modal).toBeHidden();
      });
    });
  });
});
