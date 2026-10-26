/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { readFile } from 'node:fs/promises';
import { expect, Page, test } from '@playwright/test';
import { NotebookParagraphPage } from 'e2e/models/notebook-paragraph-page';
import { NotebookVisualizationPage } from 'e2e/models/notebook-visualization-page';
import {
  addPageAnnotationBeforeEach,
  createTestNotebook,
  PAGES,
  performLoginIfRequired,
  setParagraphText,
  waitForZeppelinReady
} from '../../../utils';

const HEADERS = ['city', 'sales', 'cost'];
const ROWS = Array.from({ length: 25 }, (_, index) => [`City${index + 1}`, String(index + 1), String((index + 1) * 2)]);
const TABLE_PARAGRAPH = `%sh\nprintf '%%table ${HEADERS.join('\\t')}\\n${ROWS.map(row => row.join('\\t')).join('\\n')}\\n'`;

const openTable = async (page: Page, columns: number): Promise<NotebookVisualizationPage> => {
  await page.goto('/#/');
  await waitForZeppelinReady(page);
  await performLoginIfRequired(page);

  const { noteId, paragraphId } = await createTestNotebook(page);
  await setParagraphText(page, noteId, paragraphId, TABLE_PARAGRAPH);
  await page.request.put(`/api/notebook/${noteId}/paragraph/${paragraphId}/config`, {
    data: { colWidth: columns },
    failOnStatusCode: true
  });
  const paragraphPage = new NotebookParagraphPage(page);
  const visualizationPage = new NotebookVisualizationPage(page);
  await page.goto(`/#/notebook/${noteId}`);
  await expect(paragraphPage.paragraphContainer).toBeVisible({ timeout: 30000 });
  await paragraphPage.runParagraph();
  // A streamed table can appear before the final result replaces it.
  await expect(paragraphPage.status).toHaveText('FINISHED', { timeout: 30000 });
  await expect(visualizationPage.tableCells).toHaveText(ROWS.slice(0, 10).flat());
  return visualizationPage;
};

test.describe('Table footer interactions', () => {
  addPageAnnotationBeforeEach(PAGES.VISUALIZATIONS.TABLE);

  for (const { label, columns } of [
    { label: 'full-width', columns: 12 },
    { label: 'narrow', columns: 2 }
  ]) {
    test(`exports the displayed page from a ${label} paragraph`, async ({ page }) => {
      const visualizationPage = await test.step('Given a table result spanning three pages', () =>
        openTable(page, columns));

      await test.step('When the user opens page two', async () => {
        await visualizationPage.nextPage.click();
      });

      const expectedRows = ROWS.slice(10, 20);

      await test.step('Then the table displays exactly the second page', async () => {
        await expect(visualizationPage.selectedPage).toHaveText('2');
        await expect(visualizationPage.tableCells).toHaveText(expectedRows.flat());
      });

      await test.step('Then the bottom Export menu downloads only those rows', async () => {
        const download = await visualizationPage.downloadVisibleCsv();
        const file = await download.path();
        if (!file) {
          throw new Error('The CSV download did not produce a local file');
        }
        const csv = (await readFile(file, 'utf8'))
          .replace(/^\uFEFF/, '')
          .replace(/\r\n/g, '\n')
          .trimEnd();
        expect(csv).toBe([HEADERS, ...expectedRows].map(row => row.join(',')).join('\n'));
      });
    });
  }

  test('keeps page size and a filtered last page synchronized with the table', async ({ page }) => {
    const visualizationPage = await test.step('Given a table result spanning three pages', () => openTable(page, 12));

    await test.step('When the user selects twenty rows per page', async () => {
      await visualizationPage.selectPageSize(20);
    });

    await test.step('Then the first page contains twenty rows and the next contains the remaining five', async () => {
      await expect(visualizationPage.tableCells).toHaveText(ROWS.slice(0, 20).flat());
      await visualizationPage.nextPage.click();
      await expect(visualizationPage.selectedPage).toHaveText('2');
      await expect(visualizationPage.tableCells).toHaveText(ROWS.slice(20).flat());
    });

    await test.step('When a filter leaves only the final row', async () => {
      await visualizationPage.filterCity('City25');
    });

    await test.step('Then the table and pagination both return to the only remaining page', async () => {
      await expect(visualizationPage.tableCells).toHaveText(ROWS[24]);
      await expect(visualizationPage.selectedPage).toHaveText('1');
    });
  });
});
