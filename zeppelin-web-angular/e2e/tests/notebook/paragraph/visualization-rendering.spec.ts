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

import { expect, Locator, test } from '@playwright/test';
import { NotebookParagraphPage } from 'e2e/models/notebook-paragraph-page';
import { NotebookVisualizationPage } from 'e2e/models/notebook-visualization-page';
import {
  addPageAnnotation,
  addPageAnnotationBeforeEach,
  createTestNotebook,
  PAGES,
  performLoginIfRequired,
  setParagraphText,
  waitForZeppelinReady
} from '../../../utils';

const TABLE_PARAGRAPH = `%sh
printf '%%table city\\tsales\\tcost\\nSeoul\\t30\\t12\\nBusan\\t20\\t8\\nIncheon\\t10\\t5\\n'`;
const TABLE_HEADERS = ['city', 'sales', 'cost'];
const TABLE_CELLS = ['Seoul', '30', '12', 'Busan', '20', '8', 'Incheon', '10', '5'];

test.describe('Notebook Visualization Rendering', () => {
  addPageAnnotationBeforeEach(PAGES.VISUALIZATIONS.TABLE);

  let paragraphPage: NotebookParagraphPage;
  let visualizationPage: NotebookVisualizationPage;

  test.beforeEach(async ({ page }) => {
    await test.step('Given a notebook paragraph with deterministic table output', async () => {
      await page.goto('/#/');
      await waitForZeppelinReady(page);
      await performLoginIfRequired(page);

      const { noteId, paragraphId } = await createTestNotebook(page);
      await setParagraphText(page, noteId, paragraphId, TABLE_PARAGRAPH);

      paragraphPage = new NotebookParagraphPage(page);
      visualizationPage = new NotebookVisualizationPage(page);
      await page.goto(`/#/notebook/${noteId}`);
      await expect(paragraphPage.paragraphContainer).toBeVisible({ timeout: 30000 });

      await paragraphPage.runParagraph();
      await expect(visualizationPage.dataTable).toBeVisible({ timeout: 30000 });
    });
  });

  test('renders the exact table headers and rows', async () => {
    await test.step('Then the table presents every output field and value', async () => {
      await expect(visualizationPage.tableMode.locator('input[type="radio"]')).toBeChecked();
      await expect(visualizationPage.tableHeaders).toHaveText(TABLE_HEADERS);
      await expect(visualizationPage.tableCells).toHaveText(TABLE_CELLS);
    });
  });

  test('renders every G2 chart and preserves table data after switching back', async ({}, testInfo) => {
    const charts: Array<{ name: string; page: string; mode: Locator; canvas: Locator }> = [
      {
        name: 'Bar Chart',
        page: PAGES.VISUALIZATIONS.BAR_CHART,
        mode: visualizationPage.barChartMode,
        canvas: visualizationPage.barChartCanvas
      },
      {
        name: 'Pie Chart',
        page: PAGES.VISUALIZATIONS.PIE_CHART,
        mode: visualizationPage.pieChartMode,
        canvas: visualizationPage.pieChartCanvas
      },
      {
        name: 'Line Chart',
        page: PAGES.VISUALIZATIONS.LINE_CHART,
        mode: visualizationPage.lineChartMode,
        canvas: visualizationPage.lineChartCanvas
      },
      {
        name: 'Area Chart',
        page: PAGES.VISUALIZATIONS.AREA_CHART,
        mode: visualizationPage.areaChartMode,
        canvas: visualizationPage.areaChartCanvas
      },
      {
        name: 'Scatter Chart',
        page: PAGES.VISUALIZATIONS.SCATTER_CHART,
        mode: visualizationPage.scatterChartMode,
        canvas: visualizationPage.scatterChartCanvas
      }
    ];

    for (const chart of charts) {
      await test.step(`When selecting ${chart.name}`, async () => {
        addPageAnnotation(chart.page, testInfo);
        await expect(async () => {
          await chart.mode.click();
          await expect(chart.mode.locator('input[type="radio"]')).toBeChecked({ timeout: 1000 });
        }).toPass({ timeout: 10000 });
      });

      await test.step(`Then ${chart.name} draws visible canvas pixels`, async () => {
        await expect(chart.canvas).toBeVisible();
        await expect
          .poll(() => visualizationPage.renderedPixelCount(chart.canvas), {
            message: `${chart.name} canvas should contain rendered pixels`
          })
          .toBeGreaterThan(100);
      });
    }

    await test.step('When switching from the final chart back to Table', async () => {
      await expect(async () => {
        await visualizationPage.tableMode.click();
        await expect(visualizationPage.tableMode.locator('input[type="radio"]')).toBeChecked({ timeout: 1000 });
      }).toPass({ timeout: 10000 });
    });

    await test.step('Then the original table data remains intact', async () => {
      await expect(visualizationPage.dataTable).toBeVisible();
      await expect(visualizationPage.tableCells).toHaveText(TABLE_CELLS);
    });
  });
});
