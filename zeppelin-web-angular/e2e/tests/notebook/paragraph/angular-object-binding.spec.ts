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

import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';
import { NotebookKeyboardPage } from '../../../models/notebook-keyboard-page';
import {
  addPageAnnotationBeforeEach,
  performLoginIfRequired,
  PAGES,
  setParagraphText,
  waitForZeppelinReady
} from '../../../utils';

test.describe('Notebook-scoped Angular objects', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_PARAGRAPH);
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.SHARE_RESULT);

  test('displays retained values, synchronizes both directions, and removes the binding', async ({ page }) => {
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
    const created = await page.request.post('/api/notebook', {
      data: {
        notePath: `E2E_TEST_FOLDER/AngularObject_${randomUUID()}`,
        defaultInterpreterGroup: 'python',
        addingEmptyParagraph: false,
        paragraphs: [{ text: '%python' }, { text: '%python' }]
      }
    });
    expect(created.ok()).toBeTruthy();
    const noteId = (await created.json()).body as string;

    try {
      const note = await page.request.get(`/api/notebook/${noteId}`);
      expect(note.ok()).toBeTruthy();
      const [model, output] = (await note.json()).body.paragraphs;
      const notebook = new NotebookKeyboardPage(page);
      const value = page.getByLabel('Notebook model', { exact: true });
      const runPython = async (code: string, paragraphId = model.id) => {
        await setParagraphText(page, noteId, paragraphId, `%python\n${code}`);
        const response = await page.request.post(`/api/notebook/run/${noteId}/${paragraphId}`);
        expect(response.ok()).toBeTruthy();
        const result = (await response.json()).body;
        expect(result.code, JSON.stringify(result)).toBe('SUCCESS');
      };
      const template = `<output aria-label="Notebook model">{{ notebookModel }}</output>
<button type="button" (click)="z.set('notebookModel', 'from-ui', '${output.id}')">Update model</button>`;
      await page.goto(`/#/notebook/${noteId}`);
      await expect(notebook.getParagraphByIndex(1)).toBeVisible();

      // Explicit note ID: the Python wrapper's default bind is not notebook-scoped.
      await runPython(`z.angularBind('notebookModel', 'initial', '${noteId}')`);
      await expect(value).toHaveCount(0);
      await runPython(`print(${JSON.stringify(`%angular\n${template}`)})`, output.id);
      await expect(value).toHaveText('initial');

      await runPython(`z.angularBind('notebookModel', 'from-server', '${noteId}')`);
      await expect(value).toHaveText('from-server');

      await setParagraphText(page, noteId, model.id, `%python\nprint('SERVER-MODEL=' + z.angular('notebookModel'))`);
      await page.getByRole('button', { name: 'Update model', exact: true }).click();
      await expect(value).toHaveText('from-ui');
      // Send the read through the same WebSocket as the edit to preserve message order.
      await notebook.focusParagraphHost(0);
      await notebook.pressRunParagraph();
      await expect(notebook.getParagraphByIndex(0).getByText('SERVER-MODEL=from-ui', { exact: true })).toBeVisible();

      // The Python wrapper calls a private overload; use the public Java context API.
      await runPython(`z.z.angularUnbind('notebookModel')`);
      await expect(value).toBeEmpty();
    } finally {
      const deleted = await page.request.delete(`/api/notebook/${noteId}`);
      expect(deleted.ok()).toBeTruthy();
    }
  });
});
