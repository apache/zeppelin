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
import { DarkModePage } from 'e2e/models/dark-mode-page';
import { NotebookParagraphPage } from 'e2e/models/notebook-paragraph-page';
import { NotebookVisualizationPage, TABLE_PARAGRAPH } from 'e2e/models/notebook-visualization-page';
import {
  addPageAnnotationBeforeEach,
  createTestNotebook,
  PAGES,
  performLoginIfRequired,
  setParagraphText,
  waitForZeppelinReady
} from '../../utils';

// Resolved values of the theme variables the panels use: @component-background,
// @background-color-light, @heading-color, @text-color and @border-color-base.
const THEMES = [
  {
    theme: 'light',
    card: 'rgb(255, 255, 255)',
    head: 'rgb(250, 250, 250)',
    title: 'rgba(0, 0, 0, 0.85)',
    tagBackground: 'rgb(250, 250, 250)',
    tagText: 'rgba(0, 0, 0, 0.65)',
    tagBorder: 'rgb(217, 217, 217)'
  },
  {
    theme: 'dark',
    card: 'rgb(31, 31, 31)',
    head: 'rgb(38, 38, 38)',
    title: 'rgba(255, 255, 255, 0.95)',
    tagBackground: 'rgb(38, 38, 38)',
    tagText: 'rgba(255, 255, 255, 0.85)',
    tagBorder: 'rgb(67, 67, 67)'
  }
] as const;

const PANELS = [
  {
    name: 'Pivot',
    page: PAGES.VISUALIZATIONS.COMMON.PIVOT_SETTING,
    titles: ['Available Fields', 'Keys', 'Groups', 'Values'],
    chartMode: (visualizationPage: NotebookVisualizationPage) => visualizationPage.barChartMode,
    setting: (visualizationPage: NotebookVisualizationPage) => visualizationPage.pivotSetting
  },
  {
    name: 'Scatter',
    page: PAGES.VISUALIZATIONS.COMMON.SCATTER_SETTING,
    titles: ['Available Fields', 'XAxis', 'YAxis', 'Group', 'Size'],
    chartMode: (visualizationPage: NotebookVisualizationPage) => visualizationPage.scatterChartMode,
    setting: (visualizationPage: NotebookVisualizationPage) => visualizationPage.scatterSetting
  }
];

for (const panel of PANELS) {
  test.describe(`${panel.name} Settings Theme`, () => {
    addPageAnnotationBeforeEach(panel.page);

    let darkModePage: DarkModePage;
    let paragraphPage: NotebookParagraphPage;
    let visualizationPage: NotebookVisualizationPage;

    test.beforeEach(async ({ page }) => {
      await test.step('Given a notebook paragraph with deterministic table output', async () => {
        await page.goto('/#/');
        await waitForZeppelinReady(page);
        await performLoginIfRequired(page);

        const { noteId, paragraphId } = await createTestNotebook(page);
        await setParagraphText(page, noteId, paragraphId, TABLE_PARAGRAPH);

        darkModePage = new DarkModePage(page);
        paragraphPage = new NotebookParagraphPage(page);
        visualizationPage = new NotebookVisualizationPage(page);
        await page.goto(`/#/notebook/${noteId}`);
        await expect(paragraphPage.paragraphContainer).toBeVisible({ timeout: 30000 });

        await paragraphPage.runParagraph();
        await expect(visualizationPage.dataTable).toBeVisible({ timeout: 30000 });
      });
    });

    for (const colors of THEMES) {
      test(`uses the ${colors.theme} theme colors for card headers and field labels`, async ({ page }) => {
        const setting = panel.setting(visualizationPage);

        await test.step(`Given the ${colors.theme} theme`, async () => {
          await darkModePage.setThemeInLocalStorage(colors.theme);
          await page.reload();
          await waitForZeppelinReady(page);
          await expect(darkModePage.rootElement).toHaveAttribute('data-theme', colors.theme);
          await expect(visualizationPage.dataTable).toBeVisible({ timeout: 30000 });
        });

        await test.step(`When opening the ${panel.name} settings`, async () => {
          const chartMode = panel.chartMode(visualizationPage);
          await expect(async () => {
            await chartMode.click();
            await expect(visualizationPage.modeRadio(chartMode)).toBeChecked({ timeout: 1000 });
          }).toPass({ timeout: 10000 });
          await visualizationPage.settingTrigger.click();
          await expect(setting).toBeVisible();
        });

        await test.step('Then every card and its header use the theme colors', async () => {
          await expect(visualizationPage.settingCardTitles(setting)).toHaveText(panel.titles);
          for (let index = 0; index < panel.titles.length; index++) {
            await expect(visualizationPage.settingCards(setting).nth(index)).toHaveCSS('background-color', colors.card);
            await expect(visualizationPage.settingCardHeads(setting).nth(index)).toHaveCSS(
              'background-color',
              colors.head
            );
            await expect(visualizationPage.settingCardTitles(setting).nth(index)).toHaveCSS('color', colors.title);
          }
        });

        await test.step('And the field labels use the theme colors', async () => {
          const fieldTag = visualizationPage.settingFieldTags(setting).filter({ hasText: 'city' }).first();
          await expect(fieldTag).toHaveCSS('background-color', colors.tagBackground);
          await expect(fieldTag).toHaveCSS('color', colors.tagText);
          await expect(fieldTag).toHaveCSS('border-top-color', colors.tagBorder);
        });
      });
    }
  });
}
