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

test.describe('Notebook Repository Item - Display Mode', () => {
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

  test('should display repository card with name', async () => {
    await expect(repoItemPage.repositoryCard).toBeVisible();
    await expect(repoItemPage.repositoryName).toContainText(firstRepoName);
  });

  test('should show edit button in display mode', async () => {
    await expect(repoItemPage.editButton).toBeVisible();
    await expect(repoItemPage.editButton).toBeEnabled();
    await expect(repoItemPage.editButton).toContainText('Edit');
  });
});
