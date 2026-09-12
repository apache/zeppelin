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

import { Locator, Page } from '@playwright/test';
import { BasePage } from './base-page';

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
