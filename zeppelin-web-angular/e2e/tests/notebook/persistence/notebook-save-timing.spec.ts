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
import { NotebookKeyboardPage } from '../../../models/notebook-keyboard-page';
import { CommitParagraphSocketProbe, installCommitParagraphProbe } from '../../../models/notebook-save-timing.util';
import { addPageAnnotationBeforeEach, createTestNotebook, PAGES, waitForZeppelinReady } from '../../../utils';

const PERSISTENCE_TIMEOUT_MS = 15000;
const INITIAL_PARAGRAPH_TEXT = 'E2E save baseline';

test.describe('Notebook editor save timing', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK);
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_PARAGRAPH_CODE_EDITOR);

  let notebookPage: NotebookKeyboardPage;
  let commitProbe: CommitParagraphSocketProbe;
  let noteId: string | undefined;

  test.beforeEach(async ({ page }) => {
    noteId = undefined;
    notebookPage = new NotebookKeyboardPage(page);
    commitProbe = await installCommitParagraphProbe(page);

    await test.step('Given a disposable notebook with an editable paragraph', async () => {
      await page.goto('/#/');
      await waitForZeppelinReady(page);
      const notebook = await createTestNotebook(page);
      noteId = notebook.noteId;
      await page.goto(`/#/notebook/${notebook.noteId}`);
      await expect(notebookPage.firstParagraph).toBeVisible({ timeout: 30000 });
      await notebookPage.waitForEditorRendered(0);
      await notebookPage.setCodeEditorContent(INITIAL_PARAGRAPH_TEXT);
      await expect.poll(() => notebookPage.getParagraphTextByIndex(0)).toBe(INITIAL_PARAGRAPH_TEXT);
      await expect
        .poll(() => notebookPage.getCodeEditorContentByIndex(0), { timeout: PERSISTENCE_TIMEOUT_MS })
        .toBe(INITIAL_PARAGRAPH_TEXT);
      await expect(notebookPage.firstEditorInput).toBeFocused();
      expect(commitProbe.commitCount()).toBe(0);
    });
  });

  test.afterEach(async ({ page }) => {
    if (noteId) {
      const response = await page.request.delete(`/api/notebook/${noteId}`);
      expect(response.ok(), 'Delete the disposable notebook').toBe(true);
    }
  });

  test('persists the latest paragraph text after typing stops', { tag: '@NB-PARITY-050' }, async ({ page }) => {
    const text = '%md Idle save keeps the latest text';

    await test.step('When typing stops with the editor still focused', async () => {
      await notebookPage.pressSelectAll();
      await page.keyboard.insertText(text);
      await expect(notebookPage.firstEditorInput).toBeFocused();
      await expect
        .poll(() => notebookPage.getCodeEditorContentByIndex(0), { timeout: PERSISTENCE_TIMEOUT_MS })
        .toBe(text);
    });

    await test.step('Then an automatic save persists the text without a blur or run action', async () => {
      const [commit] = await commitProbe.waitForCommitCount(1);
      expect(commit.data.paragraph).toBe(text);
      await commitProbe.waitForForwardedResponse(commit.msgId);
      await expect(notebookPage.firstEditorInput).toBeFocused();
      await expect.poll(() => notebookPage.getParagraphTextByIndex(0), { timeout: PERSISTENCE_TIMEOUT_MS }).toBe(text);
    });

    await test.step('Then reopening the notebook renders the saved text', async () => {
      await page.reload();
      await waitForZeppelinReady(page);
      await expect
        .poll(() => notebookPage.getCodeEditorContentByIndex(0), { timeout: PERSISTENCE_TIMEOUT_MS })
        .toBe(text);
      await expect.poll(() => notebookPage.getParagraphTextByIndex(0), { timeout: PERSISTENCE_TIMEOUT_MS }).toBe(text);
    });
  });

  test('keeps an edit made while an earlier save is still pending', { tag: '@NB-PARITY-051' }, async ({ page }) => {
    const firstText = '%md First pending save';
    const latestEdit = '; latest edit wins';
    const latestText = `${firstText}${latestEdit}`;
    const delayedResponseTitle = 'Delayed save response delivered';
    let firstMsgId: string;
    let secondMsgId: string;

    await test.step('When the real server response to the first save is delayed', async () => {
      commitProbe.holdFirstCommitParagraphResponse();
      await notebookPage.pressSelectAll();
      await page.keyboard.insertText(firstText);
      await expect
        .poll(() => notebookPage.getCodeEditorContentByIndex(0), { timeout: PERSISTENCE_TIMEOUT_MS })
        .toBe(firstText);
      const [firstCommit] = await commitProbe.waitForCommitCount(1);
      expect(firstCommit.data.paragraph).toBe(firstText);
      firstMsgId = firstCommit.msgId;
      await commitProbe.waitForHeldResponse(firstMsgId);
      expect(commitProbe.forwardedResponseCount(firstMsgId)).toBe(0);
    });

    await test.step('When another edit is made before the earlier response arrives', async () => {
      await page.keyboard.insertText(latestEdit);
      await expect
        .poll(() => notebookPage.getCodeEditorContentByIndex(0), { timeout: PERSISTENCE_TIMEOUT_MS })
        .toBe(latestText);
      expect(commitProbe.forwardedResponseCount(firstMsgId)).toBe(0);
    });

    await test.step('Then receiving the first save response preserves the unsaved newer edit', async () => {
      commitProbe.releaseHeldResponseWithParagraphTitle(firstMsgId, delayedResponseTitle);
      await expect(notebookPage.firstParagraph.locator('zeppelin-elastic-input')).toHaveText(delayedResponseTitle);
      await expect
        .poll(() => notebookPage.getCodeEditorContentByIndex(0), { timeout: PERSISTENCE_TIMEOUT_MS })
        .toBe(latestText);
    });

    await test.step('Then the newer edit is saved exactly once with the latest text', async () => {
      const [, secondCommit] = await commitProbe.waitForCommitCount(2);
      expect(secondCommit.data.paragraph).toBe(latestText);
      secondMsgId = secondCommit.msgId;
      await commitProbe.expectCommitCountToStay(2);
      commitProbe.releaseQueuedResponses();
    });

    await test.step('Then the newer automatic save response persists the latest edit', async () => {
      await commitProbe.waitForForwardedResponse(secondMsgId);
      await expect
        .poll(() => notebookPage.getParagraphTextByIndex(0), { timeout: PERSISTENCE_TIMEOUT_MS })
        .toBe(latestText);
    });

    await test.step('Then reopening the notebook preserves the latest edit', async () => {
      await page.reload();
      await waitForZeppelinReady(page);
      await expect
        .poll(() => notebookPage.getCodeEditorContentByIndex(0), { timeout: PERSISTENCE_TIMEOUT_MS })
        .toBe(latestText);
      await expect
        .poll(() => notebookPage.getParagraphTextByIndex(0), { timeout: PERSISTENCE_TIMEOUT_MS })
        .toBe(latestText);
    });
  });
});
