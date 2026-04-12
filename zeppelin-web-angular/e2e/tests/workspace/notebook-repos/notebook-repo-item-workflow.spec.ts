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
import { NotebookRepoItemUtil } from '../../../models/notebook-repo-item.util';
import { addPageAnnotationBeforeEach, performLoginIfRequired, waitForZeppelinReady, PAGES } from '../../../utils';

test.describe('Notebook Repository Item - Edit Workflow', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_REPOS_ITEM);

  let notebookReposPage: NotebookReposPage;
  let repoItemPage: NotebookRepoItemPage;
  let repoItemUtil: NotebookRepoItemUtil;
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
    repoItemPage = new NotebookRepoItemPage(page, firstRepoName);
    repoItemUtil = new NotebookRepoItemUtil(page, firstRepoName);
  });

  test('should complete full edit workflow with save', async () => {
    const settingRows = await repoItemPage.settingRows.count();

    await repoItemUtil.verifyDisplayMode();

    await repoItemPage.clickEdit();
    await repoItemUtil.verifyEditMode();

    let savedSettingName = '';
    let savedValue = '';
    for (let i = 0; i < settingRows; i++) {
      // JUSTIFIED: nth(i) iterates all rows deterministically to find the first INPUT-type row
      const row = repoItemPage.settingRows.nth(i);
      // JUSTIFIED: td.first() is the Name column in the fixed 2-column settings table
      const settingName = (await row.locator('td').first().textContent()) || '';

      const isInputVisible = await row.locator('input[nz-input]').isVisible();
      if (isInputVisible) {
        const currentValue = (await repoItemPage.getSettingInputValue(settingName)) || '';
        savedValue = currentValue ? `${currentValue}_edited` : 'test-value';
        await repoItemPage.fillSettingInput(settingName, savedValue);
        savedSettingName = settingName;
        break;
      }
    }

    expect(savedSettingName, 'No INPUT-type setting found — cannot verify save result').not.toBe('');
    await expect(repoItemPage.saveButton).toBeEnabled();

    await repoItemPage.clickSave();

    await repoItemUtil.verifyDisplayMode();

    // Verify the saved value is shown in display mode — not just that mode switched
    const displayValue = await repoItemPage.getSettingValue(savedSettingName);
    expect(displayValue.trim()).toBe(savedValue.trim());
  });

  test('should complete full edit workflow with cancel', async () => {
    await repoItemUtil.verifyDisplayMode();

    // JUSTIFIED: any row is representative — testing that cancel reverts all changes
    const firstRow = repoItemPage.settingRows.first();
    // JUSTIFIED: td.first() is the Name column in the fixed 2-column settings table
    const settingName = (await firstRow.locator('td').first().textContent()) || '';
    const originalValue = await repoItemPage.getSettingValue(settingName);

    await repoItemPage.clickEdit();
    await repoItemUtil.verifyEditMode();

    await repoItemPage.fillSettingInput(settingName, 'temp-modified-value');

    await repoItemPage.clickCancel();
    await repoItemUtil.verifyDisplayMode();

    const currentValue = await repoItemPage.getSettingValue(settingName);
    expect(currentValue.trim()).toBe(originalValue.trim());
  });

  test('should toggle between display and edit modes multiple times', async () => {
    await repoItemUtil.verifyDisplayMode();

    await repoItemPage.clickEdit();
    await repoItemUtil.verifyEditMode();

    await repoItemPage.clickCancel();
    await repoItemUtil.verifyDisplayMode();

    await repoItemPage.clickEdit();
    await repoItemUtil.verifyEditMode();

    await repoItemPage.clickCancel();
    await repoItemUtil.verifyDisplayMode();
  });

  test('should preserve card visibility throughout edit workflow', async () => {
    await expect(repoItemPage.repositoryCard).toBeVisible();

    await repoItemPage.clickEdit();
    await expect(repoItemPage.repositoryCard).toBeVisible();

    await repoItemPage.clickCancel();
    await expect(repoItemPage.repositoryCard).toBeVisible();
  });

  test('should maintain settings count during mode transitions', async () => {
    const initialCount = await repoItemPage.getSettingCount();

    await repoItemPage.clickEdit();
    const editModeCount = await repoItemPage.getSettingCount();
    expect(editModeCount).toBe(initialCount);

    await repoItemPage.clickCancel();
    const finalCount = await repoItemPage.getSettingCount();
    expect(finalCount).toBe(initialCount);
  });
});
