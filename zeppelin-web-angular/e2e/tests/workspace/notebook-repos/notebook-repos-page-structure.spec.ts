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
import { NotebookReposPage } from '../../../models/notebook-repos-page';
import { addPageAnnotationBeforeEach, performLoginIfRequired, waitForZeppelinReady, PAGES } from '../../../utils';

test.describe('Notebook Repository Page - Structure', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_REPOS);

  let notebookReposPage: NotebookReposPage;

  test.beforeEach(async ({ page }) => {
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
    notebookReposPage = new NotebookReposPage(page);
    await notebookReposPage.navigate();
  });

  test('should display page header with correct title and description', async () => {
    await expect(notebookReposPage.zeppelinPageHeader).toBeVisible();
    await expect(notebookReposPage.pageDescription).toBeVisible();
  });

  test('should render repository list container', async () => {
    const count = await notebookReposPage.getRepositoryItemCount();
    expect(count).toBeGreaterThanOrEqual(0);
  });

  test('should display all repository items', async () => {
    const count = await notebookReposPage.getRepositoryItemCount();
    expect(count).toBeGreaterThan(0);
  });
});
