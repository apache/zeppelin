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

import { expect, test, Page } from '@playwright/test';
import { addPageAnnotationBeforeEach, PAGES, waitForZeppelinReady } from '../../../utils';

// Both branches render TABLE; only the React branch has a mount host around it.
// Which branch is live is therefore a question about MOUNT, not about the table.
const TABLE = '[data-testid="configuration-table"]';
const MOUNT = '[data-testid="react-configuration-table"]';
const MOUNTED_TABLE = `${MOUNT} ${TABLE}`;

// The entries arrive from ConfigurationService after the page settles, so wait
// for the first row before reading; evaluateAll does not retry on its own.
const readRows = async (page: Page, root: string): Promise<string[][]> => {
  const rows = page.locator(`${root} tbody tr:not(.ant-table-placeholder)`);
  await expect(rows.first()).toBeVisible({ timeout: 15000 });
  return rows.evaluateAll(all =>
    all.map(row => Array.from(row.querySelectorAll('td')).map(cell => (cell.textContent ?? '').trim()))
  );
};

const openConfiguration = async (page: Page, query = ''): Promise<void> => {
  await page.goto(`/#/configuration${query}`);
  await waitForZeppelinReady(page);
};

test.describe('Configuration Page - React table behind a flag', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.CONFIGURATION);

  test('without the flag, the Angular table renders', async ({ page }) => {
    await openConfiguration(page);

    await expect(page.locator(TABLE)).toBeVisible();
    await expect(page.locator(MOUNT)).toHaveCount(0);
    expect((await readRows(page, TABLE)).length).toBeGreaterThan(0);
    await expect(page.locator(`${TABLE} thead th`)).toHaveText(['Name', 'Value']);
  });

  test('with reactConfiguration=true, the React table renders instead', async ({ page }) => {
    await openConfiguration(page, '?reactConfiguration=true');

    await expect(page.locator(MOUNTED_TABLE)).toBeVisible({ timeout: 15000 });
    // One table on the page, and it is the mounted one: the Angular branch is gone.
    await expect(page.locator(TABLE)).toHaveCount(1);
  });

  test('with a bare reactConfiguration flag, the React table renders', async ({ page }) => {
    await openConfiguration(page, '?reactConfiguration');

    await expect(page.locator(MOUNTED_TABLE)).toBeVisible({ timeout: 15000 });
    await expect(page.locator(TABLE)).toHaveCount(1);
  });

  test('both tables show the same configuration entries', async ({ page }) => {
    await openConfiguration(page);
    await expect(page.locator(TABLE)).toBeVisible();
    const angularRows = await readRows(page, TABLE);

    await openConfiguration(page, '?reactConfiguration=true');
    await expect(page.locator(MOUNTED_TABLE)).toBeVisible({ timeout: 15000 });
    const reactRows = await readRows(page, MOUNTED_TABLE);

    // Same names, same values, same order: the host still owns the fetch and
    // the sort, so the remote must not reshape what it is given.
    expect(reactRows).toEqual(angularRows);
  });

  test('the header keeps the Name and Value columns', async ({ page }) => {
    await openConfiguration(page, '?reactConfiguration=true');
    await expect(page.locator(MOUNTED_TABLE)).toBeVisible({ timeout: 15000 });

    await expect(page.locator(`${MOUNTED_TABLE} thead th`)).toHaveText(['Name', 'Value']);
  });

  test('when the remote fails to load, the Angular table renders', async ({ page }) => {
    await test.step('Given a dead remote whose entry never loads', async () => {
      await page.route('**/remoteEntry.js', route => route.abort());
    });

    await test.step('When the page opens with the React table enabled', async () => {
      // Angular is the default branch, so the assertions below pass even if the flag
      // never took. Awaiting the request is what proves this is a real fallback.
      const remoteRequested = page.waitForRequest('**/remoteEntry.js');
      await openConfiguration(page, '?reactConfiguration=true');
      await remoteRequested;
    });

    await test.step('Then the Angular table takes over, showing the host-fetched entries', async () => {
      await expect(page.locator(TABLE)).toBeVisible({ timeout: 15000 });
      await expect(page.locator(MOUNT)).toHaveCount(0);
      // JUSTIFIED: this spec uses raw selectors throughout so it can scope to the mount host; it builds no POM.
      await expect(page.locator(`${TABLE} tbody tr:not(.ant-table-placeholder)`)).not.toHaveCount(0);
    });
  });
});
