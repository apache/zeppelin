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
    await page.goto('/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
    notebookReposPage = new NotebookReposPage(page);
    await notebookReposPage.navigate();

    const firstCard = notebookReposPage.repositoryItems.first();
    firstRepoName = (await firstCard.locator('.ant-card-head-title').textContent()) || '';
    repoItemPage = new NotebookRepoItemPage(page, firstRepoName);
  });

  test('should display settings table with headers', async () => {
    await expect(repoItemPage.settingTable).toBeVisible();

    const headers = repoItemPage.settingTable.locator('thead th');
    await expect(headers.nth(0)).toContainText('Name');
    await expect(headers.nth(1)).toContainText('Value');
  });

  test('should display all setting rows', async () => {
    const settingCount = await repoItemPage.getSettingCount();
    expect(settingCount).toBeGreaterThan(0);
  });

  test('should show input controls for INPUT type settings in edit mode', async () => {
    const settingRows = await repoItemPage.settingRows.count();

    await repoItemPage.clickEdit();

    for (let i = 0; i < settingRows; i++) {
      const row = repoItemPage.settingRows.nth(i);
      const settingName = (await row.locator('td').first().textContent()) || '';

      const isInputVisible = await repoItemPage.isInputVisible(settingName);
      if (isInputVisible) {
        const input = row.locator('input[nz-input]');
        await expect(input).toBeVisible();
        await expect(input).toHaveAttribute('nz-input');
      }
    }
  });

  test('should show dropdown controls for DROPDOWN type settings in edit mode', async () => {
    const settingRows = await repoItemPage.settingRows.count();

    await repoItemPage.clickEdit();

    for (let i = 0; i < settingRows; i++) {
      const row = repoItemPage.settingRows.nth(i);
      const settingName = (await row.locator('td').first().textContent()) || '';

      const isDropdownVisible = await repoItemPage.isDropdownVisible(settingName);
      if (isDropdownVisible) {
        const select = row.locator('nz-select');
        await expect(select).toBeVisible();
      }
    }
  });

  test('should update input value in edit mode', async () => {
    const settingRows = await repoItemPage.settingRows.count();

    await repoItemPage.clickEdit();

    let foundInput = false;
    for (let i = 0; i < settingRows; i++) {
      const row = repoItemPage.settingRows.nth(i);
      const settingName = (await row.locator('td').first().textContent()) || '';

      const isInputVisible = await repoItemPage.isInputVisible(settingName);
      if (isInputVisible) {
        const testValue = 'test-value';
        await repoItemPage.fillSettingInput(settingName, testValue);
        const inputValue = await repoItemPage.getSettingInputValue(settingName);
        expect(inputValue).toBe(testValue);
        foundInput = true;
        break;
      }
    }
  });

  test('should display setting name and value in display mode', async () => {
    const firstRow = repoItemPage.settingRows.first();
    const nameCell = firstRow.locator('td').first();
    const valueCell = firstRow.locator('td').nth(1);

    await expect(nameCell).toBeVisible();
    await expect(valueCell).toBeVisible();

    const nameText = await nameCell.textContent();
    expect(nameText).toBeTruthy();
  });
});
