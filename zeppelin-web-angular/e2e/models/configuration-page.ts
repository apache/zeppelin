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

import { Locator, Page } from '@playwright/test';
import { waitForZeppelinReady } from '../utils';
import { BasePage } from './base-page';

export class ConfigurationPage extends BasePage {
  readonly pageDescription: Locator;
  readonly table: Locator;
  readonly headerCells: Locator;
  readonly rows: Locator;

  constructor(page: Page) {
    super(page);
    this.pageDescription = page.locator('text=Shows current configurations for Zeppelin Server.');
    // A shared id, not the ng-zorro element: this page is a migration seam and
    // these tests have to survive the flip.
    this.table = page.locator('[data-testid="configuration-table"]');
    this.headerCells = this.table.locator('thead th');
    // Both antd and ng-zorro render the "no data" state as a row, so exclude it
    // to keep the counts about actual configuration entries.
    this.rows = this.table.locator('tbody tr:not(.ant-table-placeholder)');
  }

  async navigate(): Promise<void> {
    await this.navigateToRoute('/configuration', { timeout: 60000 });
    await this.page.waitForURL('**/#/configuration', { timeout: 60000 });
    await waitForZeppelinReady(this.page);
    await this.zeppelinPageHeader.filter({ hasText: 'Configurations' }).waitFor({ state: 'visible' });
  }

  /** `[name, value]` for every rendered entry, in the order the page shows them. */
  async readEntries(): Promise<Array<[string, string]>> {
    await this.rows.first().waitFor({ state: 'visible', timeout: 15000 });
    return this.rows.evaluateAll(rows =>
      rows.map(row => {
        const cells = Array.from(row.querySelectorAll('td')).map(cell => (cell.textContent ?? '').trim());
        return [cells[0] ?? '', cells[1] ?? ''] as [string, string];
      })
    );
  }
}
