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

import { Locator, Page } from '@playwright/test';
import { waitForZeppelinReady } from '../utils';
import { BasePage } from './base-page';

export class NotebookReposPage extends BasePage {
  readonly pageDescription: Locator;
  readonly repositoryItems: Locator;

  constructor(page: Page) {
    super(page);
    this.pageDescription = page.locator("text=Manage your Notebook Repositories' settings.");
    // Shared id, not the Angular element: /notebook-repos is a migration seam
    // and these models have to survive the flip.
    this.repositoryItems = page.locator('[data-testid="notebook-repo-item"]');
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
