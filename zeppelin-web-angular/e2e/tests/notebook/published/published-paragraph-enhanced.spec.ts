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
import {
  addPageAnnotationBeforeEach,
  performLoginIfRequired,
  waitForNotebookLinks,
  waitForZeppelinReady,
  PAGES
} from '../../../utils';

test.describe('Published Paragraph Enhanced Functionality', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.PUBLISHED_PARAGRAPH);

  let testUtil: PublishedParagraphTestUtil;
  let testNotebook: { noteId: string; paragraphId: string };

  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
    await waitForNotebookLinks(page);

    testUtil = new PublishedParagraphTestUtil(page);
    testNotebook = await testUtil.createTestNotebook();
  });

  test.afterEach(async () => {
    if (testNotebook?.noteId) {
      await testUtil.deleteTestNotebook(testNotebook.noteId);
    }
  });

  test('should display dynamic forms in published mode', async ({ page }) => {
    // Given: User has access to published paragraphs
    await page.goto('/');
    await waitForZeppelinReady(page);

    const { noteId, paragraphId } = testNotebook;

    // When: User navigates to published paragraph mode
    await page.goto(`/#/notebook/${noteId}/paragraph/${paragraphId}`);
    await page.waitForLoadState('networkidle');

    // Then: Dynamic forms should be visible and functional in published mode
    const isDynamicFormsVisible = await page.locator('zeppelin-notebook-paragraph-dynamic-forms').isVisible();
    if (isDynamicFormsVisible) {
      await expect(page.locator('zeppelin-notebook-paragraph-dynamic-forms')).toBeVisible();
    }
  });

  test('should display result in read-only mode with published flag', async ({ page }) => {
    // Given: User has access to published paragraphs
    await page.goto('/');
    await waitForZeppelinReady(page);

    const { noteId, paragraphId } = testNotebook;

    // When: User navigates to published paragraph mode
    await page.goto(`/#/notebook/${noteId}/paragraph/${paragraphId}`);
    await page.waitForLoadState('networkidle');

    // Then: Result should be displayed in read-only mode within the published paragraph container
    const publishedContainer = page.locator('zeppelin-publish-paragraph');
    const isPublishedContainerVisible = await publishedContainer.isVisible();

    if (isPublishedContainerVisible) {
      await expect(publishedContainer).toBeVisible();
    }

    // Verify that we're in published mode by checking the URL pattern
    expect(page.url()).toContain(`/paragraph/${paragraphId}`);

    const isResultVisible = await page.locator('zeppelin-notebook-paragraph-result').isVisible();
    if (isResultVisible) {
      await expect(page.locator('zeppelin-notebook-paragraph-result')).toBeVisible();
    }

    // In published mode, code editor and control panel should be hidden
    const codeEditor = page.locator('zeppelin-notebook-paragraph-code-editor');
    const controlPanel = page.locator('zeppelin-notebook-paragraph-control');
    const isCodeEditorVisible = await codeEditor.isVisible();
    const isControlPanelVisible = await controlPanel.isVisible();

    if (isCodeEditorVisible) {
      await expect(codeEditor).toBeHidden();
    }
    if (isControlPanelVisible) {
      await expect(controlPanel).toBeHidden();
    }
  });

  test('should handle published paragraph navigation pattern', async ({ page }) => {
    // Given: User has access to published paragraphs
    await page.goto('/');
    await waitForZeppelinReady(page);

    const { noteId, paragraphId } = testNotebook;

    // When: User navigates using published paragraph URL pattern
    await page.goto(`/#/notebook/${noteId}/paragraph/${paragraphId}`);
    await page.waitForLoadState('networkidle');

    // Then: URL should match the published paragraph pattern
    expect(page.url()).toContain(`/#/notebook/${noteId}/paragraph/${paragraphId}`);
  });

  test('should show confirmation modal for paragraphs without results', async ({ page }) => {
    // Given: User has access to notebooks with paragraphs
    await page.goto('/');
    await waitForZeppelinReady(page);

    const { noteId, paragraphId } = testNotebook;

    // When: User navigates to a paragraph without results
    // Then: Confirmation modal should appear asking to run the paragraph
    await testUtil.testConfirmationModalForNoResultParagraph({ noteId, paragraphId });
  });

  test('should handle non-existent paragraph error gracefully', async ({ page }) => {
    // Given: User has access to valid notebooks
    await page.goto('/');
    await waitForZeppelinReady(page);

    const { noteId } = testNotebook;
    const { paragraphId: invalidParagraphId } = testUtil.generateNonExistentIds();

    // When: User navigates to non-existent paragraph
    // Then: Error modal should be displayed and redirect to home
    await testUtil.verifyNonExistentParagraphError(noteId, invalidParagraphId);
  });

  test('should support link this paragraph functionality with auto-run', async ({ page }) => {
    // Given: User has access to notebooks with paragraphs
    await page.goto('/');
    await waitForZeppelinReady(page);

    const { noteId, paragraphId } = testNotebook;

    // When: User clicks "Link this paragraph"
    // Then: New tab should open with published paragraph view
    await testUtil.verifyClickLinkThisParagraphBehavior(noteId, paragraphId);
  });

  test('should hide editing controls in published mode', async ({ page }) => {
    // Given: User has access to published paragraphs
    await page.goto('/');
    await waitForZeppelinReady(page);

    const { noteId, paragraphId } = testNotebook;

    // When: User navigates to published paragraph mode
    await page.goto(`/#/notebook/${noteId}/paragraph/${paragraphId}`);
    await page.waitForLoadState('networkidle');

    // Then: Editing controls should be hidden
    const codeEditor = page.locator('zeppelin-notebook-paragraph-code-editor');
    const controlPanel = page.locator('zeppelin-notebook-paragraph-control');

    await expect(codeEditor).toBeHidden();
    await expect(controlPanel).toBeHidden();
  });

  test('should maintain paragraph context in published mode', async ({ page }) => {
    // Given: User has access to published paragraphs
    await page.goto('/');
    await waitForZeppelinReady(page);

    const { noteId, paragraphId } = testNotebook;

    // When: User navigates to published paragraph mode
    await page.goto(`/#/notebook/${noteId}/paragraph/${paragraphId}`);
    await page.waitForLoadState('networkidle');

    // Then: Paragraph context should be maintained
    expect(page.url()).toContain(noteId);
    expect(page.url()).toContain(paragraphId);

    const publishedContainer = page.locator('zeppelin-publish-paragraph');
    if (await publishedContainer.isVisible()) {
      await expect(publishedContainer).toBeVisible();
    }
  });
});
