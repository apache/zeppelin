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
import { NotebookRepoItemUtil } from '../../../models/notebook-repos-page.util';
import { addPageAnnotationBeforeEach, performLoginIfRequired, waitForZeppelinReady, PAGES } from '../../../utils';

test.describe('Notebook Repository Item - Edit Mode', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_REPOS_ITEM);

  let notebookReposPage: NotebookReposPage;
  let repoItemPage: NotebookRepoItemPage;
  let repoItemUtil: NotebookRepoItemUtil;
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
    repoItemUtil = new NotebookRepoItemUtil(page, firstRepoName);
  });

  test('should enter edit mode when edit button is clicked', async () => {
    await repoItemPage.clickEdit();
    await repoItemUtil.verifyEditMode();
  });

  test('should show save and cancel buttons in edit mode', async () => {
    await repoItemPage.clickEdit();
    await expect(repoItemPage.saveButton).toBeVisible();
    await expect(repoItemPage.cancelButton).toBeVisible();
  });

  test('should hide edit button in edit mode', async () => {
    await repoItemPage.clickEdit();
    await expect(repoItemPage.editButton).toBeHidden();
  });

  test('should apply edit CSS class to card in edit mode', async () => {
    await repoItemPage.clickEdit();
    const isEditMode = await repoItemPage.isEditMode();
    expect(isEditMode).toBe(true);
  });

  test('should exit edit mode when cancel button is clicked', async () => {
    await repoItemPage.clickEdit();
    await repoItemUtil.verifyEditMode();
    await repoItemPage.clickCancel();
    await repoItemUtil.verifyDisplayMode();
  });

  test('should reset form when cancel is clicked', async () => {
    const firstRow = repoItemPage.settingRows.first();
    const settingName = (await firstRow.locator('td').first().textContent()) || '';
    const originalValue = await repoItemPage.getSettingValue(settingName);

    await repoItemPage.clickEdit();

    await repoItemPage.fillSettingInput(settingName, 'temp-value');

    await repoItemPage.clickCancel();

    const currentValue = await repoItemPage.getSettingValue(settingName);
    expect(currentValue.trim()).toBe(originalValue.trim());
  });
});
