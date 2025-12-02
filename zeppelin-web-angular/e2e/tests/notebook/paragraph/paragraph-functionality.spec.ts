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
import { NotebookParagraphPage } from 'e2e/models/notebook-paragraph-page';
import { NotebookParagraphUtil } from '../../../models/notebook-paragraph-page.util';
import {
  addPageAnnotationBeforeEach,
  performLoginIfRequired,
  waitForZeppelinReady,
  PAGES,
  createTestNotebook,
  deleteTestNotebook
} from '../../../utils';

test.describe('Notebook Paragraph Functionality', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_PARAGRAPH);
  addPageAnnotationBeforeEach(PAGES.SHARE.CODE_EDITOR);

  let testNotebook: { noteId: string; paragraphId: string };

  test.beforeEach(async ({ page }) => {
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);

    testNotebook = await createTestNotebook(page);

    // Navigate to the test notebook
    await page.goto(`/#/notebook/${testNotebook.noteId}`);
    await page.waitForLoadState('networkidle');
  });

  test.afterEach(async ({ page }) => {
    if (testNotebook?.noteId) {
      await deleteTestNotebook(page, testNotebook.noteId);
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
    // Given: Navigate to notebook and set up code
    const paragraphUtil = new NotebookParagraphUtil(page);

    // Ensure we're on the correct notebook page and wait for it to load
    await expect(page).toHaveURL(/\/notebook\/[^\/]+/, { timeout: 10000 });
    await page.waitForLoadState('domcontentloaded');

    // Wait for the paragraph container to exist and be visible
    const paragraphPage = new NotebookParagraphPage(page);
    await expect(paragraphPage.paragraphContainer).toBeVisible({ timeout: 15000 });

    // Wait for the code editor component to be present before interacting
    await expect(paragraphPage.codeEditor).toBeAttached({ timeout: 10000 });

    // Activate editing mode
    await paragraphPage.doubleClickToEdit();
    await expect(paragraphPage.codeEditor).toBeVisible();

    // Wait for Monaco editor to be ready and focusable
    const codeEditor = paragraphPage.codeEditor.locator('textarea, .monaco-editor .input-area').first();
    await expect(codeEditor).toBeAttached({ timeout: 10000 });

    // Wait for editor to be editable by checking if it's enabled
    await expect(codeEditor).toBeEnabled({ timeout: 10000 });

    // Focus the editor and wait for it to actually gain focus
    await codeEditor.focus();
    await expect(codeEditor).toBeFocused({ timeout: 5000 });

    // Clear and input code
    await page.keyboard.press('Control+a');
    await page.keyboard.type('%python\nprint("Hello World")');

    // When: Execute the paragraph
    await paragraphPage.runParagraph();

    // Wait for execution to complete by checking for result
    await expect(paragraphPage.resultDisplay).toBeVisible({ timeout: 15000 });

    // Then: Result display system should work properly
    await paragraphUtil.verifyResultDisplaySystem();
  });

  test('should display dynamic forms', async ({ page }) => {
    const paragraphPage = new NotebookParagraphPage(page);
    const paragraphUtil = new NotebookParagraphUtil(page);

    // Create dynamic forms by using form syntax
    await paragraphPage.doubleClickToEdit();
    await expect(paragraphPage.codeEditor).toBeVisible();

    // Wait for Monaco editor to be ready
    const codeEditor = paragraphPage.codeEditor.locator('textarea, .monaco-editor .input-area').first();
    await expect(codeEditor).toBeAttached({ timeout: 10000 });
    await expect(codeEditor).toBeEnabled({ timeout: 10000 });

    // Focus and add dynamic form code
    await codeEditor.focus();
    await expect(codeEditor).toBeFocused({ timeout: 5000 });

    await page.keyboard.press('Control+a');
    await page.keyboard.type(`%spark
println("Name: " + z.input("name", "World"))
println("Age: " + z.select("age", Seq(("1","Under 18"), ("2","18-65"), ("3","Over 65"))))
`);

    // Run the paragraph to generate dynamic forms
    await paragraphPage.runParagraph();

    // Wait for execution to complete by checking for result first
    await expect(paragraphPage.resultDisplay).toBeVisible({ timeout: 15000 });

    // Then: Dynamic forms should be displayed (handles error cases gracefully)
    await paragraphUtil.verifyDynamicForms();
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

  test('should show cancel button during execution', async ({ page }) => {
    // Then: Cancel button should be visible during execution
    const paragraphUtil = new NotebookParagraphUtil(page);
    await paragraphUtil.verifyCancelParagraphButton();
  });

  test('should provide advanced paragraph operations', async ({ page }) => {
    // Then: Advanced operations should be available
    const paragraphUtil = new NotebookParagraphUtil(page);
    await paragraphUtil.verifyAdvancedParagraphOperations();
  });
});
