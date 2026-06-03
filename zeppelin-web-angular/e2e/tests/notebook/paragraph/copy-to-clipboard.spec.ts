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

test.describe('Copy table result to clipboard', () => {
  addPageAnnotationBeforeEach(PAGES.SHARE.SHARE_RESULT);

  let paragraphPage: NotebookParagraphPage;
  let testNotebook: { noteId: string; paragraphId: string };

  test.beforeEach(async ({ page, context }, testInfo) => {
    testInfo.skip(!!process.env.CI, 'Requires a running shell interpreter — skipped on CI');
    // Grant clipboard permissions so navigator.clipboard.writeText works in tests
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);

    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);

    testNotebook = await createTestNotebook(page);
    paragraphPage = new NotebookParagraphPage(page);

    await page.goto(`/#/notebook/${testNotebook.noteId}`);
    await page.waitForLoadState('networkidle');

    // Type a paragraph that outputs a TABLE result using the %sh interpreter
    await paragraphPage.doubleClickToEdit();
    await expect(paragraphPage.codeEditor).toBeVisible();

    const codeEditor = paragraphPage.codeEditor.locator('textarea, .monaco-editor .input-area').first();
    await expect(codeEditor).toBeAttached({ timeout: 10000 });
    await codeEditor.focus();

    const keyboard = new NotebookKeyboardPage(page);
    await keyboard.pressSelectAll();
    await page.keyboard.type('%sh\nprintf "name\\tcount\\na\\t12\\nb\\t24\\n"');

    await paragraphPage.runParagraph();
    await expect(paragraphPage.resultDisplay).toBeVisible({ timeout: 30000 });
  });

  test('export dropdown should contain Copy as TSV and Copy as CSV options', async ({ page }) => {
    // Open the export dropdown (down-arrow button next to the download icon)
    const exportDropdownTrigger = page
      .locator('.export-dropdown .export-dropdown-icon-btn, .export-dropdown button:last-child')
      .first();
    await expect(exportDropdownTrigger).toBeVisible({ timeout: 10000 });
    await exportDropdownTrigger.click();

    const menu = page.locator('.ant-dropdown-menu');
    await expect(menu).toBeVisible({ timeout: 5000 });

    await expect(menu.locator('li:has-text("Download as CSV")')).toBeVisible();
    await expect(menu.locator('li:has-text("Download as TSV")')).toBeVisible();
    await expect(menu.locator('li:has-text("Copy as TSV")')).toBeVisible();
    await expect(menu.locator('li:has-text("Copy as CSV")')).toBeVisible();
  });

  test('Copy as TSV should write tab-delimited data with headers to clipboard', async ({ page }) => {
    const exportDropdownTrigger = page
      .locator('.export-dropdown .export-dropdown-icon-btn, .export-dropdown button:last-child')
      .first();
    await expect(exportDropdownTrigger).toBeVisible({ timeout: 10000 });
    await exportDropdownTrigger.click();

    const menu = page.locator('.ant-dropdown-menu');
    await expect(menu).toBeVisible({ timeout: 5000 });
    await menu.locator('li:has-text("Copy as TSV")').click();

    // Read back what was written to the clipboard
    const clipboardText = await page.evaluate(() => navigator.clipboard.readText());
    const lines = clipboardText.split('\n').filter(l => l.trim().length > 0);

    // First line must be the header row
    expect(lines[0]).toBe('name\tcount');
    // Data rows follow
    expect(lines[1]).toBe('a\t12');
    expect(lines[2]).toBe('b\t24');
  });

  test('Copy as CSV should write comma-delimited data with headers to clipboard', async ({ page }) => {
    const exportDropdownTrigger = page
      .locator('.export-dropdown .export-dropdown-icon-btn, .export-dropdown button:last-child')
      .first();
    await expect(exportDropdownTrigger).toBeVisible({ timeout: 10000 });
    await exportDropdownTrigger.click();

    const menu = page.locator('.ant-dropdown-menu');
    await expect(menu).toBeVisible({ timeout: 5000 });
    await menu.locator('li:has-text("Copy as CSV")').click();

    const clipboardText = await page.evaluate(() => navigator.clipboard.readText());
    const lines = clipboardText.split('\n').filter(l => l.trim().length > 0);

    expect(lines[0]).toBe('name,count');
    expect(lines[1]).toBe('a,12');
    expect(lines[2]).toBe('b,24');
  });

  test('Copy as CSV should quote cell values that contain double quotes', async ({ page }) => {
    // Re-run the paragraph with a value containing a double quote
    const codeEditor = page.locator('.monaco-editor .input-area, textarea').first();
    await codeEditor.focus();
    const keyboard = new NotebookKeyboardPage(page);
    await keyboard.pressSelectAll();
    await page.keyboard.type('%sh\nprintf "col1\\tcol2\\nsay \\"hi\\"\\t1\\n"');
    await new NotebookParagraphPage(page).runParagraph();
    await page.waitForLoadState('networkidle');

    const exportDropdownTrigger = page
      .locator('.export-dropdown .export-dropdown-icon-btn, .export-dropdown button:last-child')
      .first();
    await expect(exportDropdownTrigger).toBeVisible({ timeout: 10000 });
    await exportDropdownTrigger.click();

    const menu = page.locator('.ant-dropdown-menu');
    await expect(menu).toBeVisible({ timeout: 5000 });
    await menu.locator('li:has-text("Copy as CSV")').click();

    const clipboardText = await page.evaluate(() => navigator.clipboard.readText());
    const lines = clipboardText.split('\n').filter(l => l.trim().length > 0);

    // 'say "hi"' contains double quotes — must be RFC 4180 quoted in CSV output
    expect(lines[1]).toBe('"say ""hi""",1');
  });
});
