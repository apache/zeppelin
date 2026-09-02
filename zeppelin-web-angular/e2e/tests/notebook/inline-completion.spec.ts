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

import { expect, Page, test } from '@playwright/test';
import { NotebookKeyboardPage } from 'e2e/models/notebook-keyboard-page';
import { addPageAnnotationBeforeEach, performLoginIfRequired, waitForZeppelinReady, PAGES } from '../../utils';

const openInlineCompletionEditor = async (page: Page) => {
  await page.goto('/#/');
  await waitForZeppelinReady(page);
  await performLoginIfRequired(page);

  const notePath = `E2E_TEST_FOLDER/InlineCompletion_${Date.now()}`;
  const createResponse = await page.request.post('/api/notebook', {
    data: { notePath, defaultInterpreterGroup: 'python', addingEmptyParagraph: true }
  });
  expect(createResponse.ok()).toBeTruthy();
  const noteId = (await createResponse.json()).body as string;
  const noteResponse = await page.request.get(`/api/notebook/${noteId}`);
  expect(noteResponse.ok()).toBeTruthy();

  await page.goto(`/#/notebook/${noteId}?aiInlineComplete=true`);
  await expect(page).toHaveURL(/#\/notebook\/[^?]+\?aiInlineComplete=true/);

  const keyboardPage = new NotebookKeyboardPage(page);
  await expect(keyboardPage.paragraphContainer.first()).toBeVisible({ timeout: 30000 });
  await keyboardPage.setCodeEditorContent('%python\nprint("history")');
  await keyboardPage.tryFocusCodeEditor();
  await keyboardPage.pressSelectAll();
  await page.keyboard.press('ArrowRight');
  await page.keyboard.press('Enter');
  await page.keyboard.type('prin');

  const viewLines = page.locator('.monaco-editor .view-line');
  await expect
    .poll(async () => (await viewLines.last().textContent())?.replace(/\s+/g, ' ') ?? '', { timeout: 15000 })
    .toContain('print("history")');

  return { noteId, inputArea: page.locator('.monaco-editor textarea.inputarea').first() };
};

test.describe('Inline completion', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_PARAGRAPH_CODE_EDITOR);

  test('[NB-PARITY-010] shows history completion and preserves focus when dismissed', async ({ page }) => {
    const { noteId, inputArea } = await openInlineCompletionEditor(page);

    try {
      await expect(inputArea).toBeFocused();
      await page.keyboard.press('Escape');
      await expect(inputArea).toBeFocused();
    } finally {
      await page.request.delete(`/api/notebook/${noteId}`);
    }
  });

  test('[NB-PARITY-011] blurs the editor on the second Escape after dismissing completion', async ({
    page,
    browserName
  }) => {
    test.skip(browserName !== 'chromium', 'Monaco handles the second Escape differently in Firefox and WebKit');
    const { noteId, inputArea } = await openInlineCompletionEditor(page);

    try {
      await expect(inputArea).toBeFocused();
      await page.keyboard.press('Escape');
      await expect(inputArea).toBeFocused();
      await page.keyboard.press('Escape');
      await expect(inputArea).not.toBeFocused();
    } finally {
      await page.request.delete(`/api/notebook/${noteId}`);
    }
  });
});
