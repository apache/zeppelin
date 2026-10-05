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

import { expect, test, Page } from '@playwright/test';
import { NOTEBOOK_REPOS_BRANCHES, NotebookReposPage, NotebookRepoItemPage } from '../../../models/notebook-repos-page';
import { NodeListPage } from '../../../models/node-list-page';
import { addPageAnnotationBeforeEach, PAGES, waitForZeppelinReady } from '../../../utils';

const createNoteAtRoot = async (page: Page, name: string): Promise<string> => {
  const response = await page.request.post('/api/notebook', {
    data: { notePath: `/${name}`, addingEmptyParagraph: true },
    failOnStatusCode: false
  });
  expect(response.ok(), `Create notebook failed: ${response.status()}`).toBe(true);
  return JSON.parse(await response.text()).body as string;
};

test.describe('Notebook Repository - save reloads the note tree', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_REPOS);

  // Run on both branches of the ZEPPELIN-6631 flag. The reload is the host's
  // job either way, so a React list that swallows the save would show up here.
  for (const { label, query, mount } of NOTEBOOK_REPOS_BRANCHES) {
    test(`a repository save reloads notebooks and refreshes the shell note tree (${label})`, async ({ context }) => {
      // Two clients on purpose. The header's note tree is destroyed when the
      // dropdown closes and calls listNodes() again on every open, so it cannot
      // tell a broadcast from its own refetch. The home route keeps a tree
      // mounted, which leaves the broadcast as the only thing that can change it.
      const watcher = await context.newPage();
      const actor = await context.newPage();
      const noteName = `NotebookRepoReload_${Date.now()}`;
      let noteId = '';

      try {
        await watcher.goto('/#/');
        await waitForZeppelinReady(watcher);
        const noteTree = new NodeListPage(watcher);
        await expect(noteTree.nodeListContainer).toBeVisible();

        await actor.goto(`/#/notebook-repos${query}`);
        await waitForZeppelinReady(actor);
        const reposPage = new NotebookReposPage(actor);
        await expect(reposPage.repositoryItems.first()).toBeVisible({ timeout: 20000 });
        // Otherwise a remote that failed to load and fell back to Angular would still pass the "React list" run,
        // against Angular markup.
        await expect(reposPage.reactMountedList).toHaveCount(mount ? 1 : 0);
        // JUSTIFIED: .first() picks the first configured repo; the page requires at least one.
        const repoName = (await reposPage.repositoryItems.first().getAttribute('data-repo-name')) || '';
        const repoItem = new NotebookRepoItemPage(actor, repoName);

        await test.step('Given a note created out of band, which no broadcast has announced', async () => {
          await expect(noteTree.noteLinkByName(noteName)).toHaveCount(0);
          noteId = await createNoteAtRoot(actor, noteName);
          // Creating a note over REST does not broadcast the list, so a tree
          // that picked this up on its own would make the assertion after the
          // save meaningless.
          await expect(noteTree.noteLinkByName(noteName)).toHaveCount(0);
        });

        await test.step('When the repository settings are saved unchanged', async () => {
          await repoItem.clickEdit();
          await repoItem.clickSave();
        });

        await test.step('Then the note tree of the other client picks the note up', async () => {
          await expect(noteTree.noteLinkByName(noteName)).toHaveCount(1, { timeout: 20000 });
        });
      } finally {
        if (noteId) {
          await actor.request.delete(`/api/notebook/${noteId}`, { failOnStatusCode: false });
        }
      }
    });
  }
});
