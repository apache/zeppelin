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
import { NotebookReposPage, NotebookRepoItemPage } from '../../../models/notebook-repos-page';
import { addPageAnnotationBeforeEach, performLoginIfRequired, waitForZeppelinReady, PAGES } from '../../../utils';

test.describe('Notebook Repository Item - Settings', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_REPOS_ITEM);

  let notebookReposPage: NotebookReposPage;
  let repoItemPage: NotebookRepoItemPage;
  let firstRepoName: string;

  test.beforeEach(async ({ page }) => {
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
    notebookReposPage = new NotebookReposPage(page);
    await notebookReposPage.navigate();

    // JUSTIFIED: .first() picks the first configured repo; tests require at least one repo to be present
    const firstCard = notebookReposPage.repositoryItems.first();
    firstRepoName = (await firstCard.locator('.ant-card-head-title').textContent()) || '';
    expect(firstRepoName, 'No repository found — ensure at least one repo is configured').not.toBe('');
    repoItemPage = new NotebookRepoItemPage(page, firstRepoName);
  });

  test('should display settings table with headers', async () => {
    await expect(repoItemPage.settingTable).toBeVisible();

    const headers = repoItemPage.settingTable.locator('thead th');
    await expect(headers.filter({ hasText: 'Name' })).toBeVisible();
    await expect(headers.filter({ hasText: 'Value' })).toBeVisible();
  });

  test('should show input controls for INPUT type settings in edit mode', async ({ page }) => {
    await repoItemPage.clickEdit();

    const inputRows = repoItemPage.settingRows.filter({ has: page.locator('input[nz-input]') });
    await expect(inputRows).not.toHaveCount(0); // repo must have at least one INPUT-type setting

    const count = await inputRows.count();
    for (let i = 0; i < count; i++) {
      // JUSTIFIED: nth(i) iterates all INPUT-type rows deterministically; order matches server-defined settings
      const input = inputRows.nth(i).locator('input[nz-input]');
      await expect(input).toBeVisible();
      await expect(input).toHaveAttribute('nz-input');
    }
  });

  test('should show dropdown controls for DROPDOWN type settings in edit mode', async ({ page }) => {
    await repoItemPage.clickEdit();

    const dropdownRows = repoItemPage.settingRows.filter({ has: page.locator('nz-select') });
    const count = await dropdownRows.count();
    test.skip(count === 0, 'VFSNotebookRepo has no DROPDOWN-type settings in this environment');

    for (let i = 0; i < count; i++) {
      // JUSTIFIED: nth(i) iterates all DROPDOWN-type rows deterministically; order matches server-defined settings
      await expect(dropdownRows.nth(i).locator('nz-select')).toBeVisible();
    }
  });

  test('should update input value in edit mode', async ({ page }) => {
    await repoItemPage.clickEdit();

    const inputRows = repoItemPage.settingRows.filter({ has: page.locator('input[nz-input]') });
    await expect(inputRows).not.toHaveCount(0); // repo must have at least one INPUT-type setting

    // JUSTIFIED: any INPUT-type row works — all share the same input control structure
    const firstRow = inputRows.first();
    // JUSTIFIED: td.first() is the Name column in the fixed 2-column settings table
    const settingName = (await firstRow.locator('td').first().textContent()) || '';
    const testValue = 'test-value';
    await repoItemPage.fillSettingInput(settingName, testValue);
    expect(await repoItemPage.getSettingInputValue(settingName)).toBe(testValue);
  });

  test('should display setting name and value in display mode', async () => {
    // JUSTIFIED: any row is sufficient — testing Name/Value column structure shared by all rows
    const firstRow = repoItemPage.settingRows.first();
    // JUSTIFIED: td.first() = Name column in the fixed 2-column settings table
    const nameCell = firstRow.locator('td').first();
    // JUSTIFIED: td.nth(1) = Value column in the fixed 2-column settings table
    const valueCell = firstRow.locator('td').nth(1);

    await expect(nameCell).toBeVisible();
    await expect(valueCell).toBeVisible();

    const nameText = await nameCell.textContent();
    expect(nameText).not.toBe('');
    const valueText = await valueCell.textContent();
    expect(valueText).not.toBe('');
  });
});
