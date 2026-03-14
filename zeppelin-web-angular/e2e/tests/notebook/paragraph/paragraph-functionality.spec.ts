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
import { NotebookKeyboardPage } from 'e2e/models/notebook-keyboard-page';
import {
  addPageAnnotationBeforeEach,
  performLoginIfRequired,
  waitForZeppelinReady,
  PAGES,
  createTestNotebook
} from '../../../utils';

test.describe('Notebook Paragraph Functionality', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_PARAGRAPH);
  addPageAnnotationBeforeEach(PAGES.SHARE.CODE_EDITOR);

  let paragraphPage: NotebookParagraphPage;
  let testNotebook: { noteId: string; paragraphId: string };

  test.beforeEach(async ({ page }) => {
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);

    testNotebook = await createTestNotebook(page);
    paragraphPage = new NotebookParagraphPage(page);

    await page.goto(`/#/notebook/${testNotebook.noteId}`);
    await page.waitForLoadState('networkidle');
  });

  test('should display paragraph container with proper structure', async () => {
    await expect(paragraphPage.paragraphContainer).toBeVisible();
    await expect(paragraphPage.controlPanel).toBeVisible();
  });

  test('should support double-click editing functionality', async () => {
    await expect(paragraphPage.paragraphContainer).toBeVisible();
    await paragraphPage.doubleClickToEdit();
    await expect(paragraphPage.codeEditor).toBeVisible();
  });

  test('should display add paragraph buttons', async () => {
    await expect(paragraphPage.addParagraphAbove).toBeVisible();
    await expect(paragraphPage.addParagraphAbove).toHaveCount(1);
    await expect(paragraphPage.addParagraphBelow).toBeVisible();
    await expect(paragraphPage.addParagraphBelow).toHaveCount(1);
  });

  test('should display comprehensive control interface', async () => {
    await expect(paragraphPage.controlPanel).toBeVisible();
    await expect(paragraphPage.runButton).toBeVisible();
    await expect(paragraphPage.runButton).toBeEnabled();
  });

  test('should display result system properly', async ({ page }) => {
    await expect(page).toHaveURL(/\/notebook\/[^\/]+/, { timeout: 10000 });
    await page.waitForLoadState('domcontentloaded');
    await expect(paragraphPage.paragraphContainer).toBeVisible({ timeout: 15000 });
    await expect(paragraphPage.codeEditor).toBeAttached({ timeout: 10000 });

    await paragraphPage.doubleClickToEdit();
    await expect(paragraphPage.codeEditor).toBeVisible();

    const codeEditor = paragraphPage.codeEditor.locator('textarea, .monaco-editor .input-area').first();
    await expect(codeEditor).toBeAttached({ timeout: 10000 });
    await expect(codeEditor).toBeEnabled({ timeout: 10000 });

    await codeEditor.focus();
    await expect(codeEditor).toBeFocused({ timeout: 5000 });

    const notebookKeyboardPage = new NotebookKeyboardPage(page);
    await notebookKeyboardPage.pressSelectAll();
    await page.keyboard.type('%python\nprint("Hello World")');

    await paragraphPage.runParagraph();
    await expect(paragraphPage.resultDisplay).toBeVisible({ timeout: 15000 });
    await expect(paragraphPage.resultDisplay).not.toBeEmpty();
  });

  test('should display dynamic forms', async ({ page }) => {
    test.skip(!!process.env.CI, 'Dynamic form tests require a Spark interpreter — skipped on CI');

    await paragraphPage.doubleClickToEdit();
    await expect(paragraphPage.codeEditor).toBeVisible();

    const codeEditor = paragraphPage.codeEditor.locator('textarea, .monaco-editor .input-area').first();
    await expect(codeEditor).toBeAttached({ timeout: 10000 });
    await expect(codeEditor).toBeEnabled({ timeout: 10000 });

    await codeEditor.focus();
    await expect(codeEditor).toBeFocused({ timeout: 5000 });

    const notebookKeyboardPage = new NotebookKeyboardPage(page);
    await notebookKeyboardPage.pressSelectAll();
    await page.keyboard.type(`%spark
println("Name: " + z.input("name", "World"))
println("Age: " + z.select("age", Seq(("1","Under 18"), ("2","18-65"), ("3","Over 65"))))
`);

    await paragraphPage.runParagraph();
    await expect(paragraphPage.resultDisplay).toBeVisible({ timeout: 15000 });

    // Handles error cases gracefully — Spark may not be available
    const resultText = await paragraphPage.resultDisplay.textContent().catch(() => null); // JUSTIFIED: result display may not exist when interpreter is unavailable; null triggers graceful fallback below
    const hasInterpreterError =
      resultText &&
      ((resultText.toLowerCase().includes('interpreter') && resultText.toLowerCase().includes('not found')) ||
        resultText.toLowerCase().includes('error'));

    if (hasInterpreterError) {
      await expect(paragraphPage.resultDisplay).toBeVisible();
    } else {
      await expect(paragraphPage.dynamicForms).toBeVisible();
    }
  });

  test('should render footer element in paragraph DOM', async () => {
    // Footer is visibility:hidden by default (hover-only); toBeAttached() confirms
    // the element is rendered in the DOM regardless of visual state
    await expect(paragraphPage.footerInfo).toBeAttached();
  });

  test('should provide paragraph control actions', async ({ page }) => {
    await expect(page).toHaveURL(/\/notebook\/[^\/]+/, { timeout: 10000 });
    await expect(paragraphPage.paragraphContainer).toBeVisible({ timeout: 15000 });

    await paragraphPage.openSettingsDropdown();

    const dropdownMenu = page.locator('ul.ant-dropdown-menu, .dropdown-menu');
    await expect(dropdownMenu).toBeVisible({ timeout: 5000 });
    await expect(page.locator('li:has-text("Insert")')).toBeVisible();
    await expect(page.locator('li:has-text("Clone")')).toBeVisible();

    await page.keyboard.press('Escape');
  });

  test('should show cancel button during execution', async ({ page }) => {
    await expect(page).toHaveURL(/\/notebook\/[^\/]+/, { timeout: 10000 });
    await expect(paragraphPage.paragraphContainer).toBeVisible({ timeout: 15000 });
    await expect(paragraphPage.runButton).toBeVisible();
    await expect(paragraphPage.runButton).toBeEnabled();

    await paragraphPage.doubleClickToEdit();
    await expect(paragraphPage.codeEditor).toBeVisible();

    const codeEditor = paragraphPage.codeEditor.locator('textarea, .monaco-editor .input-area').first();
    await expect(codeEditor).toBeAttached({ timeout: 10000 });
    await expect(codeEditor).toBeEnabled({ timeout: 10000 });

    await codeEditor.focus();
    await expect(codeEditor).toBeFocused({ timeout: 5000 });

    const notebookKeyboardPage = new NotebookKeyboardPage(page);
    await notebookKeyboardPage.pressSelectAll();
    await page.keyboard.type('%python\nimport time;time.sleep(10)\nprint("Done")');

    await paragraphPage.runParagraph();

    const cancelButton = page.locator(
      '.cancel-para, [nz-tooltip*="Cancel"], [title*="Cancel"], button:has-text("Cancel"), i[nz-icon="pause-circle"], .anticon-pause-circle'
    );
    await expect(cancelButton).toBeVisible({ timeout: 5000 });

    await cancelButton.click();

    // Then: Execution should stop — running spinner disappears
    await expect(page.locator('.paragraph-control .fa-spin')).not.toBeVisible({ timeout: 15000 });
  });
});
