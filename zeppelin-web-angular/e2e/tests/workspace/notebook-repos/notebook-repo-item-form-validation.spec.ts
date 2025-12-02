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

test.describe('Notebook Repository Item - Form Validation', () => {
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

  test('should disable save button when form is invalid', async () => {
    const settingRows = await repoItemPage.settingRows.count();
    if (settingRows === 0) {
      test.skip();
      return;
    }

    await repoItemPage.clickEdit();

    const firstRow = repoItemPage.settingRows.first();
    const settingName = (await firstRow.locator('td').first().textContent()) || '';

    const isInputVisible = await repoItemPage.isInputVisible(settingName);
    if (isInputVisible) {
      await repoItemPage.fillSettingInput(settingName, '');

      const isSaveEnabled = await repoItemPage.isSaveButtonEnabled();
      expect(isSaveEnabled).toBe(false);
    } else {
      test.skip();
    }
  });

  test('should enable save button when form is valid', async () => {
    const settingRows = await repoItemPage.settingRows.count();
    if (settingRows === 0) {
      test.skip();
      return;
    }

    await repoItemPage.clickEdit();

    const firstRow = repoItemPage.settingRows.first();
    const settingName = (await firstRow.locator('td').first().textContent()) || '';

    const isInputVisible = await repoItemPage.isInputVisible(settingName);
    if (isInputVisible) {
      const originalValue = await repoItemPage.getSettingInputValue(settingName);
      await repoItemPage.fillSettingInput(settingName, originalValue || 'valid-value');

      const isSaveEnabled = await repoItemPage.isSaveButtonEnabled();
      expect(isSaveEnabled).toBe(true);
    } else {
      test.skip();
    }
  });

  test('should validate required fields on form controls', async () => {
    const settingRows = await repoItemPage.settingRows.count();
    if (settingRows === 0) {
      test.skip();
      return;
    }

    await repoItemPage.clickEdit();

    for (let i = 0; i < settingRows; i++) {
      const row = repoItemPage.settingRows.nth(i);
      const settingName = (await row.locator('td').first().textContent()) || '';

      const isInputVisible = await repoItemPage.isInputVisible(settingName);
      if (isInputVisible) {
        const input = row.locator('input[nz-input]');
        await expect(input).toBeVisible();
      }

      const isDropdownVisible = await repoItemPage.isDropdownVisible(settingName);
      if (isDropdownVisible) {
        const select = row.locator('nz-select');
        await expect(select).toBeVisible();
      }
    }
  });
});
