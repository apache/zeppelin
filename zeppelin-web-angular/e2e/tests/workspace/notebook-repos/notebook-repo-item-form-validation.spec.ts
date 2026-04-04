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

  test('should disable save button when form is invalid', async () => {
    await repoItemPage.clickEdit();

    // JUSTIFIED: any row is sufficient — all rows share the same save-disable-on-empty behavior
    const firstRow = repoItemPage.settingRows.first();
    // JUSTIFIED: td.first() is the Name column in the fixed 2-column settings table
    const settingName = (await firstRow.locator('td').first().textContent()) || '';

    await repoItemPage.fillSettingInput(settingName, '');

    await expect(repoItemPage.saveButton).not.toBeEnabled();
  });

  test('should enable save button when form is valid', async () => {
    await repoItemPage.clickEdit();

    // JUSTIFIED: any row is sufficient — all rows share the same save-enable-on-valid behavior
    const firstRow = repoItemPage.settingRows.first();
    // JUSTIFIED: td.first() is the Name column in the fixed 2-column settings table
    const settingName = (await firstRow.locator('td').first().textContent()) || '';

    const originalValue = await repoItemPage.getSettingInputValue(settingName);
    await repoItemPage.fillSettingInput(settingName, originalValue || 'valid-value');

    await expect(repoItemPage.saveButton).toBeEnabled();
  });

  test('should have editable controls for every setting row in edit mode', async () => {
    const settingRows = await repoItemPage.settingRows.count();

    await repoItemPage.clickEdit();

    for (let i = 0; i < settingRows; i++) {
      // JUSTIFIED: nth(i) iterates all rows deterministically; order matches server-defined settings
      const row = repoItemPage.settingRows.nth(i);
      const input = row.locator('input[nz-input]');
      const select = row.locator('nz-select');
      await expect(input.or(select), `Row ${i} must have an editable control in edit mode`).toBeVisible();
      const isInputRow = await input.isVisible();
      if (isInputRow) {
        // JUSTIFIED: attribute check only applies to INPUT-type rows; DROPDOWN-type rows use nz-select and have no nz-input
        await expect(input).toHaveAttribute('nz-input');
      }
    }
  });
});
