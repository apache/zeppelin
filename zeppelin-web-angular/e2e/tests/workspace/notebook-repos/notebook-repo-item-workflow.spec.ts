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

    for (let i = 0; i < settingRows; i++) {
      const row = repoItemPage.settingRows.nth(i);
      const settingName = (await row.locator('td').first().textContent()) || '';

      const isInputVisible = await repoItemPage.isInputVisible(settingName);
      if (isInputVisible) {
        const originalValue = await repoItemPage.getSettingInputValue(settingName);
        await repoItemPage.fillSettingInput(settingName, originalValue || 'test-value');
        break;
      }
    }

    const isSaveEnabled = await repoItemPage.isSaveButtonEnabled();
    expect(isSaveEnabled).toBe(true);

    await repoItemPage.clickSave();

    await repoItemUtil.verifyDisplayMode();
  });

  test('should complete full edit workflow with cancel', async () => {
    await repoItemUtil.verifyDisplayMode();

    const firstRow = repoItemPage.settingRows.first();
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
