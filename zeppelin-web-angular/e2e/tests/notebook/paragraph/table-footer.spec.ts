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
import { Download, expect, Page, test } from '@playwright/test';
import { NotebookKeyboardPage } from 'e2e/models/notebook-keyboard-page';
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
const tableParagraph = (rows: string[][]): string =>
  `%sh\nprintf '%%table ${[HEADERS, ...rows].map(row => row.join('\\t')).join('\\n')}\\n'`;
const TABLE_PARAGRAPH = tableParagraph(ROWS);
const EMPTY_TABLE_PARAGRAPH = tableParagraph([]);

const readCsv = async (download: Download): Promise<string> => {
  const file = await download.path();
  if (!file) {
    throw new Error('The CSV download did not produce a local file');
  }
  return (await readFile(file, 'utf8'))
    .replace(/^\uFEFF/, '')
    .replace(/\r\n/g, '\n')
    .trimEnd();
};

const openTable = async (page: Page, columns: number, rows = ROWS) => {
  await page.goto('/#/');
  await waitForZeppelinReady(page);
  await performLoginIfRequired(page);

  const { noteId, paragraphId } = await createTestNotebook(page);
  await setParagraphText(page, noteId, paragraphId, tableParagraph(rows));
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
  await expect(visualizationPage.tableCells).toHaveText(rows.slice(0, 10).flat());
  return { visualizationPage, paragraphPage, noteId, paragraphId };
};

test.describe('Table footer interactions', () => {
  addPageAnnotationBeforeEach(PAGES.VISUALIZATIONS.TABLE);

  for (const { label, columns } of [
    { label: 'full-width', columns: 12 },
    { label: 'narrow', columns: 2 }
  ]) {
    test(`exports the displayed page from a ${label} paragraph`, async ({ page }) => {
      const { visualizationPage } = await test.step('Given a table result spanning three pages', () =>
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
        const csv = await readCsv(await visualizationPage.downloadVisibleCsv());
        expect(csv).toBe([HEADERS, ...expectedRows].map(row => row.join(',')).join('\n'));
      });
    });
  }

  test('keeps page size and a filtered last page synchronized with the table', async ({ page }) => {
    const { visualizationPage } = await test.step('Given a table result spanning three pages', () =>
      openTable(page, 12));

    await test.step('Given the user is on the last page', async () => {
      await visualizationPage.nextPage.click();
      await visualizationPage.nextPage.click();
      await expect(visualizationPage.selectedPage).toHaveText('3');
      await expect(visualizationPage.tableCells).toHaveText(ROWS.slice(20).flat());
    });

    await test.step('When a larger page size makes the current page invalid', async () => {
      await visualizationPage.selectPageSize(20);
    });

    await test.step('Then pagination moves to the last valid page with the correct rows', async () => {
      await expect(visualizationPage.selectedPage).toHaveText('2');
      await expect(visualizationPage.tableCells).toHaveText(ROWS.slice(20).flat());
    });

    await test.step('When a smaller page size keeps that page valid', async () => {
      await visualizationPage.selectPageSize(10);
    });

    await test.step('Then the page is retained and its displayed rows are updated', async () => {
      await expect(visualizationPage.selectedPage).toHaveText('2');
      await expect(visualizationPage.tableCells).toHaveText(ROWS.slice(10, 20).flat());
    });

    await test.step('Then the original fifty-row option still displays all rows on page one', async () => {
      await visualizationPage.selectPageSize(50);
      await expect(visualizationPage.selectedPage).toHaveText('1');
      await expect(visualizationPage.tableCells).toHaveText(ROWS.flat());
      await visualizationPage.selectPageSize(10);
      await expect(visualizationPage.tableCells).toHaveText(ROWS.slice(0, 10).flat());
    });

    await test.step('When a filter leaves only the final row', async () => {
      await visualizationPage.filterCity('City25');
    });

    await test.step('Then the table and pagination both return to the only remaining page', async () => {
      await expect(visualizationPage.tableCells).toHaveText(ROWS[24]);
      await expect(visualizationPage.selectedPage).toHaveText('1');
    });
  });

  test('clears visible rows and exports for an empty filter, then restores the first page', async ({ page }) => {
    // Equal city values keep this pagination check independent of the filter trigger's header sorting.
    const rows = ROWS.map(([, sales, cost]) => ['City', sales, cost]);
    const { visualizationPage } = await openTable(page, 12, rows);

    await visualizationPage.nextPage.click();
    await expect(visualizationPage.tableCells).toHaveText(rows.slice(10, 20).flat());
    await visualizationPage.filterCity('no-such-city');

    await expect(visualizationPage.noData).toBeVisible();
    await expect(visualizationPage.tableCells).toHaveCount(0);
    await expect(visualizationPage.pagination).toBeHidden();
    expect(await readCsv(await visualizationPage.downloadVisibleCsv())).toBe('');

    await visualizationPage.filterCity('');

    await expect(visualizationPage.noData).toBeHidden();
    await expect(visualizationPage.selectedPage).toHaveText('1');
    await expect(visualizationPage.tableCells).toHaveText(rows.slice(0, 10).flat());
  });

  test('clears visible rows and exports after an empty rerun, then restores new results', async ({ page }) => {
    const { visualizationPage, paragraphPage, noteId, paragraphId } = await openTable(page, 12);
    const keyboardPage = new NotebookKeyboardPage(page);

    await setParagraphText(page, noteId, paragraphId, EMPTY_TABLE_PARAGRAPH);
    await expect
      .poll(async () => (await keyboardPage.getCodeEditorContent()).replace(/\s+/g, ''))
      .toBe(EMPTY_TABLE_PARAGRAPH.replace(/\s+/g, ''));
    await expect(visualizationPage.tableCells).toHaveText(ROWS.slice(0, 10).flat());
    await visualizationPage.nextPage.click();
    await expect(visualizationPage.tableCells).toHaveText(ROWS.slice(10, 20).flat());
    await paragraphPage.runParagraph();

    await expect(visualizationPage.noData).toBeVisible();
    await expect(visualizationPage.tableCells).toHaveCount(0);
    await expect(paragraphPage.status).toHaveText('FINISHED');
    await expect(visualizationPage.pagination).toBeHidden();
    expect(await readCsv(await visualizationPage.downloadVisibleCsv())).toBe('');

    await setParagraphText(page, noteId, paragraphId, TABLE_PARAGRAPH);
    // Monaco clips long lines; the first row token proves the empty source has been replaced.
    await expect
      .poll(async () => (await keyboardPage.getCodeEditorContent()).replace(/\s+/g, ''))
      .toContain('City1\\t1\\t2\\n');
    await expect(visualizationPage.noData).toBeVisible();
    await paragraphPage.runParagraph();

    await expect(visualizationPage.noData).toBeHidden();
    await expect(visualizationPage.selectedPage).toHaveText('1');
    await expect(visualizationPage.tableCells).toHaveText(ROWS.slice(0, 10).flat());
    await expect(paragraphPage.status).toHaveText('FINISHED');
  });
});
