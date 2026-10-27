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

import { Download, expect, Locator, Page } from '@playwright/test';
import { BasePage } from './base-page';

export const TABLE_PARAGRAPH = `%sh
printf '%%table city\\tsales\\tcost\\nSeoul\\t30\\t12\\nBusan\\t20\\t8\\nIncheon\\t10\\t5\\n'`;

export class NotebookVisualizationPage extends BasePage {
  readonly tableMode: Locator;
  readonly barChartMode: Locator;
  readonly pieChartMode: Locator;
  readonly lineChartMode: Locator;
  readonly areaChartMode: Locator;
  readonly scatterChartMode: Locator;
  readonly dataTable: Locator;
  readonly tableHeaders: Locator;
  readonly tableCells: Locator;
  readonly barChartCanvas: Locator;
  readonly pieChartCanvas: Locator;
  readonly lineChartCanvas: Locator;
  readonly areaChartCanvas: Locator;
  readonly scatterChartCanvas: Locator;
  readonly settingTrigger: Locator;
  readonly pivotSetting: Locator;
  readonly scatterSetting: Locator;
  readonly pagination: Locator;
  readonly selectedPage: Locator;
  readonly nextPage: Locator;
  readonly tableExportButton: Locator;
  readonly noData: Locator;
  private readonly sizeChanger: Locator;
  private readonly cityFilterTrigger: Locator;
  private readonly resultDisplay: Locator;

  constructor(page: Page) {
    super(page);
    this.resultDisplay = page.locator('zeppelin-notebook-paragraph-result');
    this.tableMode = this.resultDisplay.locator('label.viz-icon:has(.anticon-table)');
    this.barChartMode = this.resultDisplay.locator('label.viz-icon:has(.anticon-bar-chart)');
    this.pieChartMode = this.resultDisplay.locator('label.viz-icon:has(.anticon-pie-chart)');
    this.lineChartMode = this.resultDisplay.locator('label.viz-icon:has(.anticon-line-chart)');
    this.areaChartMode = this.resultDisplay.locator('label.viz-icon:has(.anticon-area-chart)');
    this.scatterChartMode = this.resultDisplay.locator('label.viz-icon:has(.anticon-dot-chart)');
    this.dataTable = this.resultDisplay.getByRole('table');
    this.tableHeaders = this.dataTable.locator('thead th');
    this.tableCells = this.dataTable.locator('tbody tr.ant-table-row td');
    this.barChartCanvas = this.resultDisplay.locator('zeppelin-bar-chart-visualization canvas');
    this.pieChartCanvas = this.resultDisplay.locator('zeppelin-pie-chart-visualization canvas');
    this.lineChartCanvas = this.resultDisplay.locator('zeppelin-line-chart-visualization canvas');
    this.areaChartCanvas = this.resultDisplay.locator('zeppelin-area-chart-visualization canvas');
    this.scatterChartCanvas = this.resultDisplay.locator('zeppelin-scatter-chart-visualization canvas');
    this.settingTrigger = this.resultDisplay.getByText('Setting', { exact: true });
    this.pivotSetting = this.resultDisplay.locator('zeppelin-visualization-pivot-setting');
    this.scatterSetting = this.resultDisplay.locator('zeppelin-visualization-scatter-setting');
    this.pagination = this.resultDisplay.locator('.ant-pagination');
    this.selectedPage = this.pagination.locator('.ant-pagination-item-active');
    this.nextPage = this.pagination.getByRole('listitem', { name: 'Next Page', exact: true });
    this.tableExportButton = this.resultDisplay.getByRole('button', { name: 'Export', exact: true });
    this.noData = this.resultDisplay.getByText('No Data', { exact: true });
    this.sizeChanger = this.pagination.locator('.ant-select-selector');
    // JUSTIFIED: ng-zorro's icon-only filter trigger has no accessible label.
    this.cityFilterTrigger = this.tableHeaders.filter({ hasText: 'city' }).locator('.filter-icon');
  }

  async downloadVisibleCsv(): Promise<Download> {
    await this.tableExportButton.hover();
    const exportAction = this.page.getByText('Export visible data as csv', { exact: true });
    await expect(exportAction).toBeVisible();
    const download = this.page.waitForEvent('download');
    await exportAction.click();
    return download;
  }

  async selectPageSize(size: number): Promise<void> {
    await this.sizeChanger.click();
    await this.page.getByText(`${size} / page`, { exact: true }).click();
  }

  async filterCity(term: string): Promise<void> {
    await this.cityFilterTrigger.click();
    const search = this.page.getByPlaceholder('Search...', { exact: true });
    await search.fill(term);
    await search.press('Enter');
    await search.press('Escape');
    await expect(search).toBeHidden();
  }

  modeRadio(mode: Locator): Locator {
    return mode.locator('input[type="radio"]');
  }

  settingCards(setting: Locator): Locator {
    return setting.locator('.ant-card');
  }

  settingCardHeads(setting: Locator): Locator {
    return setting.locator('.ant-card-head');
  }

  settingCardTitles(setting: Locator): Locator {
    return setting.locator('.ant-card-head-title');
  }

  settingFieldTags(setting: Locator): Locator {
    return setting.locator('.drag-tag');
  }

  async renderedPixelCount(canvas: Locator): Promise<number> {
    return canvas.evaluate((element: HTMLCanvasElement) => {
      const context = element.getContext('2d');
      if (!context || element.width === 0 || element.height === 0) {
        return 0;
      }

      const pixels = context.getImageData(0, 0, element.width, element.height).data;
      let count = 0;
      for (let index = 3; index < pixels.length; index += 4) {
        if (pixels[index] > 0) {
          count += 1;
        }
      }
      return count;
    });
  }
}
