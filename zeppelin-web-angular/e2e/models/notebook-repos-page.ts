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

import { expect, Locator, Page } from '@playwright/test';
import { waitForZeppelinReady } from '../utils';
import { BasePage } from './base-page';

// Shared by every spec that runs the same assertions against both branches of the ZEPPELIN-6631 flag, using the
// `{label, query}` loop documented in e2e/AGENTS.md, so the flag's query string has one place to change. `mount` lets
// a caller assert which branch actually rendered (see `reactMountedList` below) - without it, a spec whose assertions
// pass on both branches' markup would still report a "React list" pass if the remote failed to load and the host fell
// back to Angular.
export const NOTEBOOK_REPOS_BRANCHES = [
  { label: 'Angular list', query: '', mount: false },
  { label: 'React list', query: '?reactNotebookRepos=true', mount: true }
] as const;

export class NotebookReposPage extends BasePage {
  readonly pageDescription: Locator;
  readonly repositoryItems: Locator;
  // The outer [data-testid="react-notebook-repo-list"] div is rendered as soon as the flag is on
  // (notebook-repos.component.html's @if), before ReactMountDirective has even started loading the remote, so it
  // alone can't tell "mounted" from "flag on, still loading, or loading failed and fell back". The nested
  // [data-testid="notebook-repo-list"] only exists once NotebookRepoList.tsx itself has actually rendered inside that
  // host, so this locator is scoped to it, matching react-notebook-repo-list.spec.ts's MOUNTED_LIST.
  readonly reactMountedList: Locator;

  constructor(page: Page) {
    super(page);
    this.pageDescription = page.locator("text=Manage your Notebook Repositories' settings.");
    // Shared id, not the Angular element: /notebook-repos is a migration seam
    // and these models have to survive the flip.
    this.repositoryItems = page.locator('[data-testid="notebook-repo-item"]');
    this.reactMountedList = page.locator('[data-testid="react-notebook-repo-list"] [data-testid="notebook-repo-list"]');
  }

  // `query` carries the ZEPPELIN-6631 React flag (e.g. '?reactNotebookRepos=true'),
  // so callers can drive the same workflow through either branch.
  async navigate(query = ''): Promise<void> {
    await this.navigateToRoute(`/notebook-repos${query}`, { timeout: 60000 });
    await this.page.waitForURL('**/#/notebook-repos*', { timeout: 60000 });
    await waitForZeppelinReady(this.page);
    // [data-testid="notebook-repo-item"], not the Angular-only zeppelin-notebook-repo-item element,
    // since that tag never exists on the React branch, so the race would silently lose all its
    // coverage there the moment the header arm ever got slow.
    await Promise.race([
      this.zeppelinPageHeader.filter({ hasText: 'Notebook Repository' }).waitFor({ state: 'visible' }),
      this.repositoryItems.first().waitFor({ state: 'visible' })
    ]);
  }

  // For specs looping NOTEBOOK_REPOS_BRANCHES: folds the mount assertion into the navigation itself, rather than
  // leaving it to each call site. A spec that destructures only `{ label, query }` and calls `navigate(query)`
  // directly would compile and run fine on a remote that failed to load - that's the exact gap a prior review round
  // found and fixed; this method exists so a future branch-parametrized spec can't reopen it by omission.
  async navigateBranch(branch: (typeof NOTEBOOK_REPOS_BRANCHES)[number]): Promise<void> {
    await this.navigate(branch.query);
    await expect(this.reactMountedList).toHaveCount(branch.mount ? 1 : 0);
  }
}

export class NotebookRepoItemPage extends BasePage {
  readonly repositoryCard: Locator;
  readonly repositoryName: Locator;
  readonly editButton: Locator;
  readonly saveButton: Locator;
  readonly cancelButton: Locator;
  readonly settingTable: Locator;
  readonly settingRows: Locator;

  constructor(page: Page, repoName: string) {
    super(page);
    this.repositoryCard = page.locator(`[data-testid="notebook-repo-item"][data-repo-name="${repoName}"]`);
    this.repositoryName = this.repositoryCard.locator('.ant-card-head-title');
    this.editButton = this.repositoryCard.locator('button:has-text("Edit")');
    this.saveButton = this.repositoryCard.locator('button:has-text("Save")');
    this.cancelButton = this.repositoryCard.locator('button:has-text("Cancel")');
    // .ant-table is what both ng-zorro and antd render.
    this.settingTable = this.repositoryCard.locator('.ant-table');
    this.settingRows = this.repositoryCard.locator('tbody tr:not(.ant-table-placeholder)');
  }

  async clickEdit(): Promise<void> {
    await this.editButton.click({ timeout: 15000 });
    // Wait for Angular to swap to edit mode before returning. Without this,
    // a follow-up assertion like `expect(editButton).not.toBeVisible()` races
    // against the re-render and intermittently sees the button still present.
    await this.saveButton.waitFor({ state: 'visible', timeout: 10000 });
  }

  async clickSave(): Promise<void> {
    await this.saveButton.click({ timeout: 15000 });
    await this.editButton.waitFor({ state: 'visible', timeout: 10000 });
  }

  async clickCancel(): Promise<void> {
    await this.cancelButton.click({ timeout: 15000 });
    await this.editButton.waitFor({ state: 'visible', timeout: 10000 });
  }

  async fillSettingInput(settingName: string, value: string): Promise<void> {
    const row = this.repositoryCard.locator('tbody tr').filter({ hasText: settingName });
    // .ant-input, not [nz-input], since ng-zorro's nz-input directive renders that class too,
    // and it excludes a DROPDOWN row's Select search input.
    const input = row.locator('input.ant-input');
    await this.fillAndVerifyInput(input, value);
  }

  async getSettingInputValue(settingName: string): Promise<string> {
    const row = this.repositoryCard.locator('tbody tr').filter({ hasText: settingName });
    const input = row.locator('input.ant-input');
    return await input.inputValue();
  }

  async getSettingValue(settingName: string): Promise<string> {
    const row = this.repositoryCard.locator('tbody tr').filter({ hasText: settingName });
    return (await row.locator('td').nth(1).textContent()) || '';
  }

  async getSettingCount(): Promise<number> {
    return await this.settingRows.count();
  }
}
