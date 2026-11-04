/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { expect, test } from '@playwright/test';
import { NotebookRepoItemPage, NotebookReposPage } from '../../../models/notebook-repos-page';
import { addPageAnnotationBeforeEach, PAGES } from '../../../utils';

test.describe('Notebook Repository - Concurrent saves', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_REPOS);

  let reposPage: NotebookReposPage;
  let repoA: NotebookRepoItemPage;
  let repoB: NotebookRepoItemPage;

  test.beforeEach(({ page }) => {
    reposPage = new NotebookReposPage(page);
    repoA = new NotebookRepoItemPage(page, 'GitNotebookRepo');
    repoB = new NotebookRepoItemPage(page, 'VFSNotebookRepo');
  });

  test('keeps a pending card value through another save and accepts the confirming server value', async ({ page }) => {
    const repositories = [
      {
        name: 'GitNotebookRepo',
        className: 'org.apache.zeppelin.notebook.repo.GitNotebookRepo',
        settings: [{ type: 'INPUT', value: [], selected: '/original/a', name: 'Notebook Path' }]
      },
      {
        name: 'VFSNotebookRepo',
        className: 'org.apache.zeppelin.notebook.repo.VFSNotebookRepo',
        settings: [{ type: 'INPUT', value: [], selected: '/original/b', name: 'Notebook Path' }]
      }
    ];
    let releaseSaveB!: () => void;
    const saveBGate = new Promise<void>(resolve => {
      releaseSaveB = resolve;
    });
    let markSaveBStarted!: () => void;
    const saveBStarted = new Promise<void>(resolve => {
      markSaveBStarted = resolve;
    });
    const valueA = repoA.settingRows.filter({ hasText: 'Notebook Path' }).getByRole('cell').nth(1);
    const valueB = repoB.settingRows.filter({ hasText: 'Notebook Path' }).getByRole('cell').nth(1);

    await page.route('**/api/notebook-repositories', async route => {
      if (route.request().method() === 'GET') {
        await route.fulfill({ json: { status: 'OK', body: repositories } });
        return;
      }
      expect(route.request().method()).toBe('PUT');
      const payload = route.request().postDataJSON();
      const index = repositories.findIndex(repo => repo.className === payload.name);
      expect(index).toBeGreaterThanOrEqual(0);
      expect(payload.settings).toEqual({ 'Notebook Path': index === 0 ? '/submitted/a' : '/submitted/b' });
      if (index === 1) {
        markSaveBStarted();
        await saveBGate;
      }
      repositories[index].settings[0].selected = index === 0 ? '/confirmed/a' : '/confirmed/b';
      // Only GET returns the confirming values; a PUT response cannot satisfy the refetch assertions.
      const acknowledgement = structuredClone(repositories[index]);
      acknowledgement.settings[0].selected = payload.settings['Notebook Path'];
      await route.fulfill({ json: { status: 'OK', body: acknowledgement } });
    });

    try {
      await test.step('Given a mounted React list with two repositories', async () => {
        await reposPage.navigate('?reactNotebookRepos=true');
        await expect(reposPage.reactMountedList).toBeVisible();
        await expect(valueA).toHaveText('/original/a');
        await expect(valueB).toHaveText('/original/b');
      });

      await test.step('When B saves while its PUT is held before persistence', async () => {
        await repoB.clickEdit();
        await repoB.fillSettingInput('Notebook Path', '/submitted/b');
        await repoB.clickSave();
        await saveBStarted;
        await expect(valueB).toHaveText('/submitted/b');
      });

      await test.step('Then A can save and refetch without reverting B', async () => {
        const changes = await repoB.observeSettingValues('Notebook Path');
        try {
          await repoA.clickEdit();
          await repoA.fillSettingInput('Notebook Path', '/submitted/a');
          await Promise.all([
            page.waitForResponse(
              response => response.request().method() === 'GET' && response.url().endsWith('/api/notebook-repositories')
            ),
            repoA.clickSave()
          ]);
          await expect(valueA).toHaveText('/confirmed/a');
          await expect(valueB).toHaveText('/submitted/b');
          // JUSTIFIED: recorded DOM history proves the interval; a retrying text assertion only proves the final value.
          expect(await changes.evaluate(observation => observation.stop())).toEqual(['/submitted/b']);
        } finally {
          await changes.evaluate(observation => observation.stop());
          await changes.dispose();
        }
      });

      await test.step('Then B accepts a server value that differs from its submission', async () => {
        const refetched = page.waitForResponse(
          response => response.request().method() === 'GET' && response.url().endsWith('/api/notebook-repositories')
        );
        releaseSaveB();
        await refetched;
        await expect(valueB).toHaveText('/confirmed/b');
        await expect(valueA).toHaveText('/confirmed/a');
      });
    } finally {
      releaseSaveB();
    }
  });
});
