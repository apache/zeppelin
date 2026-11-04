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
import type { Interpreter } from '../../../../src/app/interfaces/interpreter';
import { NotebookActionBarPage } from '../../../models/notebook-action-bar-page';
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

  test('reconciles bindings when reloading a note after interpreter restart', async ({ page }) => {
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
    const settingsResponse = await page.request.get('/api/interpreter/setting');
    expect(settingsResponse.ok()).toBeTruthy();
    const settings = (await settingsResponse.json()).body as Interpreter[];
    const python = settings.find(setting => setting.name === 'python');
    if (!python) {
      throw new Error('The Python interpreter setting is required');
    }

    const name = `angular_reload_${randomUUID()}`;
    const settingResponse = await page.request.post('/api/interpreter/setting', {
      data: {
        name,
        group: python.group,
        properties: python.properties,
        dependencies: python.dependencies,
        option: { ...python.option, isExistingProcess: false }
      }
    });
    expect(settingResponse.ok()).toBeTruthy();
    const settingId = ((await settingResponse.json()).body as Interpreter).id;

    try {
      const created = await page.request.post('/api/notebook', {
        data: {
          notePath: `E2E_TEST_FOLDER/AngularReload_${randomUUID()}`,
          defaultInterpreterGroup: name,
          addingEmptyParagraph: false,
          paragraphs: [{ text: `%${name}` }, { text: `%${name}` }]
        }
      });
      expect(created.ok()).toBeTruthy();
      const noteId = (await created.json()).body as string;

      try {
        const note = await page.request.get(`/api/notebook/${noteId}`);
        expect(note.ok()).toBeTruthy();
        const [model, output] = (await note.json()).body.paragraphs;
        const runPython = async (code: string, paragraphId = model.id) => {
          await setParagraphText(page, noteId, paragraphId, `%${name}\n${code}`);
          const response = await page.request.post(`/api/notebook/run/${noteId}/${paragraphId}`);
          expect(response.ok()).toBeTruthy();
          const result = (await response.json()).body;
          expect(result.code, JSON.stringify(result)).toBe('SUCCESS');
        };
        const value = page.getByLabel('Reloaded model', { exact: true });
        const template = `<output aria-label="Reloaded model">{{ reloadModel }}</output>
<button type="button" (click)="z.set('reloadModel', 'from-ui', '${output.id}')">Update reloaded model</button>`;
        const renderAngular = `print(${JSON.stringify(`%angular\n${template}`)})`;
        await page.goto(`/#/notebook/${noteId}`);
        await runPython(`z.angularBind('reloadModel', 'before-restart', '${noteId}')`);
        await runPython(renderAngular, output.id);
        await expect(value).toHaveText('before-restart');
        // Persist the completed output without rerunning it.
        await setParagraphText(page, noteId, output.id, `%${name}\n${renderAngular}`);

        const restarted = await page.request.put(`/api/interpreter/setting/restart/${settingId}`, {
          data: { noteId }
        });
        expect(restarted.ok()).toBeTruthy();
        await runPython(`assert z.angular('reloadModel') is None`);
        await expect(value).toHaveText('before-restart');

        await new NotebookActionBarPage(page).reloadButton.click();
        await expect(value).toHaveCount(1);
        await expect(value).toBeEmpty();

        await runPython(`z.angularBind('reloadModel', 'after-restart', '${noteId}')`);
        await expect(value).toHaveText('after-restart');
        await setParagraphText(page, noteId, model.id, `%${name}\nprint('RELOADED-MODEL=' + z.angular('reloadModel'))`);
        await page.getByRole('button', { name: 'Update reloaded model', exact: true }).click();
        const notebook = new NotebookKeyboardPage(page);
        await notebook.focusParagraphHost(0);
        await notebook.pressRunParagraph();
        await expect(
          notebook.getParagraphByIndex(0).getByText('RELOADED-MODEL=from-ui', { exact: true })
        ).toBeVisible();
      } finally {
        const deleted = await page.request.delete(`/api/notebook/${noteId}`);
        expect(deleted.ok()).toBeTruthy();
      }
    } finally {
      const deleted = await page.request.delete(`/api/interpreter/setting/${settingId}`);
      expect(deleted.ok()).toBeTruthy();
    }
  });
});
