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
import { NotebookKeyboardPage } from 'e2e/models/notebook-keyboard-page';
import { addPageAnnotationBeforeEach, performLoginIfRequired, waitForZeppelinReady, PAGES } from '../../utils';

test.describe('Monaco editor: Enter with a no-op exact-match suggestion (ZEPPELIN-6710)', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_PARAGRAPH_CODE_EDITOR);

  test('Enter inserts a newline instead of being swallowed by a completed-word suggestion', async ({ page }) => {
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);

    const notePath = `E2E_TEST_FOLDER/MonacoEnterNewline_${Date.now()}`;
    const createResponse = await page.request.post('/api/notebook', {
      data: { notePath, defaultInterpreterGroup: 'python', addingEmptyParagraph: true }
    });
    expect(createResponse.ok()).toBeTruthy();
    const noteId = (await createResponse.json()).body as string;

    try {
      await page.goto(`/#/notebook/${noteId}`);
      await expect(page).toHaveURL(new RegExp(`/notebook/${noteId}`));

      const keyboardPage = new NotebookKeyboardPage(page);
      await expect(keyboardPage.paragraphContainer.first()).toBeVisible({ timeout: 30000 });

      await test.step('Given: a running python paragraph with a variable already bound in scope', async () => {
        await keyboardPage.setCodeEditorContent('%python\nfoobar = 1\n');
        await keyboardPage.pressRunParagraph();
        await keyboardPage.waitForParagraphExecution(0);
      });

      await test.step('When: typing the bound variable in full so quickSuggestions auto-pops its exact, no-op match, then pressing Enter', async () => {
        await keyboardPage.tryFocusCodeEditor();
        await keyboardPage.pressSelectAll();
        await page.keyboard.press('ArrowRight');

        await page.keyboard.type('foobar', { delay: 100 }); // delay lets quickSuggestions fire per keystroke
        await expect(keyboardPage.autocompletePopup).toBeVisible({ timeout: 10000 });

        await page.keyboard.press('Enter');
        await page.keyboard.type('baz');
      });

      await test.step('Then: the line break survives and the new text lands on its own line', async () => {
        await expect
          .poll(async () => keyboardPage.getParagraphTextByIndex(0), { timeout: 15000 })
          .toBe('%python\nfoobar = 1\nfoobar\nbaz');
      });

      await test.step('And: Enter still accepts a genuine, non-no-op completion for a unique partial prefix (smart mode only skips no-ops)', async () => {
        await page.keyboard.press('Enter');
        await page.keyboard.type('fooba', { delay: 100 }); // no python keyword shares this prefix, so "foobar" is the only candidate

        await expect(keyboardPage.autocompletePopup).toBeVisible({ timeout: 10000 });
        await page.keyboard.press('Enter');

        await expect
          .poll(async () => keyboardPage.getParagraphTextByIndex(0), { timeout: 15000 })
          .toBe('%python\nfoobar = 1\nfoobar\nbaz\nfoobar');
      });
    } finally {
      await page.request.delete(`/api/notebook/${noteId}`);
    }
  });
});
