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
import { addPageAnnotationBeforeEach, PAGES, waitForZeppelinReady } from '../../../utils';

// Both branches render REPO_ITEM; only the React branch has a mount host around
// it. Which branch is live is therefore a question about MOUNT.
const REPO_ITEM = '[data-testid="notebook-repo-item"]';
const MOUNT = '[data-testid="react-notebook-repo-list"]';
const MOUNTED_LIST = `${MOUNT} [data-testid="notebook-repo-list"]`;

const openRepos = async (page: Page, query = ''): Promise<void> => {
  await page.goto(`/#/notebook-repos${query}`);
  await waitForZeppelinReady(page);
};

const settingRows = (page: Page, root: string) => page.locator(`${root} tbody tr:not(.ant-table-placeholder)`);

test.describe('Notebook Repository - React list behind a flag', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_REPOS);

  test('without the flag, the Angular list renders', async ({ page }) => {
    await openRepos(page);

    await expect(page.locator(REPO_ITEM).first()).toBeVisible();
    await expect(page.locator(MOUNT)).toHaveCount(0);
    await expect(page.locator(`${REPO_ITEM} button:has-text("Edit")`).first()).toBeVisible();
  });

  test('with reactNotebookRepos=true, the React list renders instead', async ({ page }) => {
    await openRepos(page, '?reactNotebookRepos=true');

    await expect(page.locator(MOUNTED_LIST)).toBeVisible({ timeout: 15000 });
    await expect(page.locator(REPO_ITEM).first()).toBeVisible();
  });

  test('with a bare reactNotebookRepos flag, the React list renders', async ({ page }) => {
    await openRepos(page, '?reactNotebookRepos');

    await expect(page.locator(MOUNTED_LIST)).toBeVisible({ timeout: 15000 });
  });

  test('both lists show the same repositories and settings', async ({ page }) => {
    await openRepos(page);
    await expect(page.locator(REPO_ITEM).first()).toBeVisible();
    const angularNames = await page
      .locator(`${REPO_ITEM}`)
      .evaluateAll(cards => cards.map(card => card.getAttribute('data-repo-name')));
    const angularRows = await settingRows(page, REPO_ITEM).allInnerTexts();

    await openRepos(page, '?reactNotebookRepos=true');
    await expect(page.locator(MOUNTED_LIST)).toBeVisible({ timeout: 15000 });
    const reactNames = await page
      .locator(`${MOUNT} ${REPO_ITEM}`)
      .evaluateAll(cards => cards.map(card => card.getAttribute('data-repo-name')));
    // JUSTIFIED: prefer-web-first-assertions. Both sides must be read the same
    // way to compare, and toHaveText() reads textContent, which loses the cell
    // separator innerText adds.
    const reactRows = await settingRows(page, `${MOUNT} ${REPO_ITEM}`).allInnerTexts();

    // The host still owns the fetch and the sort, so the remote must not
    // reshape what it is handed.
    expect(reactNames).toEqual(angularNames);
    expect(reactRows).toEqual(angularRows);
  });

  test('the React card switches to inputs on edit and back on cancel', async ({ page }) => {
    await openRepos(page, '?reactNotebookRepos=true');
    await expect(page.locator(MOUNTED_LIST)).toBeVisible({ timeout: 15000 });

    const card = page.locator(`${MOUNT} ${REPO_ITEM}`).first();
    const value = (await settingRows(page, `${MOUNT} ${REPO_ITEM}`).first().locator('td').nth(1).innerText()).trim();

    await card.getByRole('button', { name: 'Edit' }).click();
    // JUSTIFIED: inline rather than NotebookRepoItemPage - this spec locates the card via
    // MOUNT/REPO_ITEM constants rather than that Page Object, so reusing its selector alone
    // without its `repositoryCard` root would be inconsistent with the rest of the file.
    // .ant-input, not a bare 'input': a DROPDOWN row's Select also renders an
    // <input role="combobox"> that this would otherwise match instead.
    const input = card.locator('input.ant-input').first();
    await expect(input).toBeVisible();
    await expect(input).toHaveValue(value);

    await card.getByRole('button', { name: 'Cancel' }).click();
    await expect(card.getByRole('button', { name: 'Edit' })).toBeVisible();
    await expect(card.locator('input.ant-input')).toHaveCount(0);
  });

  test('when the remote fails to load, the Angular list renders', async ({ page }) => {
    await test.step('Given a dead remote whose entry never loads', async () => {
      await page.route('**/remoteEntry.js', route => route.abort());
    });

    await test.step('When the page opens with the React list enabled', async () => {
      // Angular is the default branch, so the assertions below pass even if the
      // flag never took. Awaiting the request is what proves this is a fallback.
      const remoteRequested = page.waitForRequest('**/remoteEntry.js');
      await openRepos(page, '?reactNotebookRepos=true');
      await remoteRequested;
    });

    await test.step('Then the Angular list takes over', async () => {
      await expect(page.locator(REPO_ITEM).first()).toBeVisible({ timeout: 15000 });
      await expect(page.locator(MOUNT)).toHaveCount(0);
    });
  });
});
