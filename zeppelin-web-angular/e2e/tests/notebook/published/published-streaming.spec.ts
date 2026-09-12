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

import { createServer } from 'node:http';

import { expect, test } from '@playwright/test';
import { NotebookParagraphPage } from '../../../models/notebook-paragraph-page';
import { PublishedParagraphPage } from '../../../models/published-paragraph-page';
import {
  addPageAnnotationBeforeEach,
  createTestNotebook,
  PAGES,
  setParagraphText,
  waitForZeppelinReady
} from '../../../utils';

test.describe('Published paragraph streaming', () => {
  // JUSTIFIED: shared owner and notebook state must remain within one worker.
  test.describe.configure({ mode: 'default' });
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.PUBLISHED_PARAGRAPH);

  let owner: NotebookParagraphPage;
  let notebook: { noteId: string; paragraphId: string };

  test.beforeEach(async ({ page }) => {
    owner = new NotebookParagraphPage(page);
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    notebook = await createTestNotebook(page);
  });

  for (const react of [false, true]) {
    test(`accumulates live output and preserves the exact terminal snapshot (${react ? 'React' : 'Angular'})`, async ({
      page,
      context
    }) => {
      const viewer = await context.newPage();
      const published = new PublishedParagraphPage(viewer);

      // The local shell waits for the viewer before emitting the next chunk.
      const release: Array<() => void> = [];
      const gates = [0, 1].map(index => new Promise<void>(resolve => (release[index] = resolve)));
      const outputGate = createServer((request, response) => {
        const gate = gates[Number(request.url?.slice(1))];
        if (!gate) {
          response.writeHead(404).end();
          return;
        }
        void gate.then(() => response.end());
      });
      await new Promise<void>((resolve, reject) => {
        outputGate.once('error', reject);
        outputGate.listen(0, '127.0.0.1', resolve);
      });

      try {
        const address = outputGate.address();
        if (!address || typeof address === 'string') {
          throw new Error('Failed to bind output gate');
        }
        const waitForViewer = `curl --noproxy '*' --fail --silent --show-error --max-time 180 http://127.0.0.1:${address.port}`;

        await test.step('Given a published viewer of a shell paragraph with controlled output', async () => {
          await setParagraphText(
            page,
            notebook.noteId,
            notebook.paragraphId,
            `%sh\nset -e\necho first\n${waitForViewer}/0\necho second\n${waitForViewer}/1\necho third`
          );
          await page.goto(`/#/notebook/${notebook.noteId}`);
          await expect(owner.paragraphContainer).toBeVisible();
          await viewer.goto(`/#/notebook/${notebook.noteId}/paragraph/${notebook.paragraphId}?react=${react}`);
          await waitForZeppelinReady(viewer);
          await expect(published.confirmationModal).toBeVisible();
          await published.cancelButton.click();
        });

        await test.step('When the owner runs the paragraph, published output accumulates while RUNNING', async () => {
          await owner.runParagraph();
          await expect(published.textOutput).toHaveText(/^first\n$/);
          await expect(published.textOutput).toBeVisible();
          await expect(owner.status).toHaveText('RUNNING');
          release[0]();
          await expect(published.textOutput).toHaveText(/^first\nsecond\n$/);
          await expect(published.textOutput).toBeVisible();
          await expect(owner.status).toHaveText('RUNNING');
          release[1]();
        });

        await test.step('Then the terminal snapshot contains every chunk exactly once', async () => {
          await expect(owner.status).toHaveText('FINISHED');
          await expect(published.textOutput).toHaveText(/^first\nsecond\nthird\n$/);
          await expect(published.reactWidget).toHaveCount(react ? 1 : 0);
        });

        await test.step('And a new run replaces the previous stream', async () => {
          await setParagraphText(page, notebook.noteId, notebook.paragraphId, '%sh\necho rerun');
          await owner.runParagraph();
          await expect(owner.status).toHaveText('FINISHED');
          await expect(published.textOutput).toHaveText(/^rerun\n$/);
        });
      } finally {
        await new Promise<void>((resolve, reject) => {
          outputGate.close(error => (error ? reject(error) : resolve()));
          outputGate.closeAllConnections();
        });
        await viewer.close();
      }
    });
  }
});
