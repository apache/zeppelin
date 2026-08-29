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
import { ConfigurationPage } from '../../../models/configuration-page';
import { addPageAnnotationBeforeEach, PAGES, waitForZeppelinReady } from '../../../utils';

test.describe('Configuration Page - Structure', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.CONFIGURATION);

  let configurationPage: ConfigurationPage;

  test.beforeEach(async ({ page }) => {
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    configurationPage = new ConfigurationPage(page);
    await configurationPage.navigate();
  });

  test('should display page header with correct title and description', async () => {
    await expect(configurationPage.zeppelinPageHeader).toBeVisible();
    await expect(configurationPage.zeppelinPageHeader).toContainText('Configurations');
    await expect(configurationPage.pageDescription).toBeVisible();
    await expect(configurationPage.zeppelinPageHeader).toContainText(
      'Note: For security reasons, some key/value pairs including passwords would not be shown.'
    );
  });

  test('should display the entries in a Name and Value table', async () => {
    await expect(configurationPage.table).toBeVisible();
    await expect(configurationPage.headerCells).toHaveText(['Name', 'Value']);
    expect((await configurationPage.readEntries()).length).toBeGreaterThan(0);
  });

  test('should sort the entries by name', async () => {
    const names = (await configurationPage.readEntries()).map(([name]) => name);

    expect(names).toEqual([...names].sort((a, b) => a.localeCompare(b)));
  });

  test('should name every entry, allowing an empty value', async () => {
    const entries = await configurationPage.readEntries();

    // A configuration key is always present; its value can legitimately be
    // empty, either unset or withheld as a secret.
    expect(entries.every(([name]) => name.length > 0)).toBe(true);
  });

  test('should keep the table on a reload', async ({ page }) => {
    const before = await configurationPage.readEntries();

    await page.reload();
    await waitForZeppelinReady(page);

    await expect(configurationPage.table).toBeVisible();
    expect(await configurationPage.readEntries()).toEqual(before);
  });

  test('should reach the page from a direct URL without going through the menu', async ({ page }) => {
    await page.goto('/#/configuration');
    await waitForZeppelinReady(page);

    await expect(configurationPage.zeppelinPageHeader).toContainText('Configurations');
    await expect(configurationPage.table).toBeVisible();
  });
});
