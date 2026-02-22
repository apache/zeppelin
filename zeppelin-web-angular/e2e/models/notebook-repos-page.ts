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
    this.repositoryItems = page.locator('zeppelin-notebook-repo-item');
  }

  async navigate(): Promise<void> {
    await this.navigateToRoute('/notebook-repos', { timeout: 60000 });
    await this.page.waitForURL('**/#/notebook-repos', { timeout: 60000 });
    await waitForZeppelinReady(this.page);
    await this.page.waitForLoadState('networkidle', { timeout: 15000 });
    await Promise.race([
      this.zeppelinPageHeader.filter({ hasText: 'Notebook Repository' }).waitFor({ state: 'visible' }),
      this.page.waitForSelector('zeppelin-notebook-repo-item', { state: 'visible' })
    ]);
  }

  async getRepositoryItemCount(): Promise<number> {
    return await this.repositoryItems.count();
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
    this.repositoryCard = page.locator('nz-card').filter({ hasText: repoName });
    this.repositoryName = this.repositoryCard.locator('.ant-card-head-title');
    this.editButton = this.repositoryCard.locator('button:has-text("Edit")');
    this.saveButton = this.repositoryCard.locator('button:has-text("Save")');
    this.cancelButton = this.repositoryCard.locator('button:has-text("Cancel")');
    this.settingTable = this.repositoryCard.locator('nz-table');
    this.settingRows = this.repositoryCard.locator('tbody tr');
  }

  async clickEdit(): Promise<void> {
    await this.editButton.click({ timeout: 15000 });
  }

  async clickSave(): Promise<void> {
    await this.saveButton.click({ timeout: 15000 });
  }

  async clickCancel(): Promise<void> {
    await this.cancelButton.click({ timeout: 15000 });
  }

  async isEditMode(): Promise<boolean> {
    return await this.repositoryCard.evaluate(el => el.classList.contains('edit'));
  }

  async isSaveButtonEnabled(): Promise<boolean> {
    return await this.saveButton.isEnabled();
  }

  async getSettingValue(settingName: string): Promise<string> {
    const row = this.repositoryCard.locator('tbody tr').filter({ hasText: settingName });
    const valueCell = row.locator('td').nth(1);
    return (await valueCell.textContent()) || '';
  }

  async fillSettingInput(settingName: string, value: string): Promise<void> {
    const row = this.repositoryCard.locator('tbody tr').filter({ hasText: settingName });
    const input = row.locator('input[nz-input]');
    await input.clear();
    await input.fill(value);
  }

  async selectSettingDropdown(settingName: string, optionValue: string): Promise<void> {
    const row = this.repositoryCard.locator('tbody tr').filter({ hasText: settingName });
    const select = row.locator('nz-select');
    await select.click({ timeout: 15000 });
    await this.page.locator(`nz-option[nzvalue="${optionValue}"]`).click({ timeout: 15000 });
  }

  async getSettingInputValue(settingName: string): Promise<string> {
    const row = this.repositoryCard.locator('tbody tr').filter({ hasText: settingName });
    const input = row.locator('input[nz-input]');
    return await input.inputValue();
  }

  async isInputVisible(settingName: string): Promise<boolean> {
    const row = this.repositoryCard.locator('tbody tr').filter({ hasText: settingName });
    const input = row.locator('input[nz-input]');
    return await input.isVisible();
  }

  async isDropdownVisible(settingName: string): Promise<boolean> {
    const row = this.repositoryCard.locator('tbody tr').filter({ hasText: settingName });
    const select = row.locator('nz-select');
    return await select.isVisible();
  }

  async getSettingCount(): Promise<number> {
    return await this.settingRows.count();
  }
}
