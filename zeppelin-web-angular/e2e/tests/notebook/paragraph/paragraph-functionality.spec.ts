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

import { test } from '@playwright/test';
import { NotebookParagraphUtil } from '../../../models/notebook-paragraph-page.util';
import { PublishedParagraphTestUtil } from '../../../models/published-paragraph-page.util';
import { addPageAnnotationBeforeEach, performLoginIfRequired, waitForZeppelinReady, PAGES } from '../../../utils';

test.describe('Notebook Paragraph Functionality', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_PARAGRAPH);

  let testUtil: PublishedParagraphTestUtil;
  let testNotebook: { noteId: string; paragraphId: string };

  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);

    testUtil = new PublishedParagraphTestUtil(page);
    testNotebook = await testUtil.createTestNotebook();

    // Navigate to the test notebook
    await page.goto(`/#/notebook/${testNotebook.noteId}`);
    await page.waitForLoadState('networkidle');
  });

  test.afterEach(async () => {
    if (testNotebook?.noteId) {
      await testUtil.deleteTestNotebook(testNotebook.noteId);
    }
  });

  test('should display paragraph container with proper structure', async ({ page }) => {
    // Then: Paragraph container should be visible with proper structure
    const paragraphUtil = new NotebookParagraphUtil(page);
    await paragraphUtil.verifyParagraphContainerStructure();
  });

  test('should support double-click editing functionality', async ({ page }) => {
    // Then: Editing functionality should be activated
    const paragraphUtil = new NotebookParagraphUtil(page);
    await paragraphUtil.verifyDoubleClickEditingFunctionality();
  });

  test('should display add paragraph buttons', async ({ page }) => {
    // Then: Add paragraph buttons should be visible
    const paragraphUtil = new NotebookParagraphUtil(page);
    await paragraphUtil.verifyAddParagraphButtons();
  });

  test('should display comprehensive control interface', async ({ page }) => {
    // Then: Control interface should be comprehensive
    const paragraphUtil = new NotebookParagraphUtil(page);
    await paragraphUtil.verifyParagraphControlInterface();
  });

  test('should support code editor functionality', async ({ page }) => {
    // Then: Code editor should be functional
    const paragraphUtil = new NotebookParagraphUtil(page);
    await paragraphUtil.verifyCodeEditorFunctionality();
  });

  test('should display result system properly', async ({ page }) => {
    // Then: Result display system should work properly
    const paragraphUtil = new NotebookParagraphUtil(page);
    await paragraphUtil.verifyResultDisplaySystem();
  });

  test('should support title editing when present', async ({ page }) => {
    // Then: Title editing should be functional if present
    const paragraphUtil = new NotebookParagraphUtil(page);
    await paragraphUtil.verifyTitleEditingIfPresent();
  });

  test('should display dynamic forms when present', async ({ page }) => {
    // Then: Dynamic forms should be displayed if present
    const paragraphUtil = new NotebookParagraphUtil(page);
    await paragraphUtil.verifyDynamicFormsIfPresent();
  });

  test('should display footer information', async ({ page }) => {
    // Then: Footer information should be displayed
    const paragraphUtil = new NotebookParagraphUtil(page);
    await paragraphUtil.verifyFooterInformation();
  });

  test('should provide paragraph control actions', async ({ page }) => {
    // Then: Control actions should be available
    const paragraphUtil = new NotebookParagraphUtil(page);
    await paragraphUtil.verifyParagraphControlActions();
  });

  test('should support paragraph execution workflow', async ({ page }) => {
    // Then: Execution workflow should work properly
    const paragraphUtil = new NotebookParagraphUtil(page);
    await paragraphUtil.verifyParagraphExecutionWorkflow();
  });

  test('should provide advanced paragraph operations', async ({ page }) => {
    // Then: Advanced operations should be available
    const paragraphUtil = new NotebookParagraphUtil(page);
    await paragraphUtil.verifyAdvancedParagraphOperations();
  });
});
