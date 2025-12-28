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

import { expect, Page } from '@playwright/test';
import { NotebookReposPage, NotebookRepoItemPage } from './notebook-repos-page';
import { BasePage } from './base-page';

export class NotebookReposPageUtil extends BasePage {
  private notebookReposPage: NotebookReposPage;

  constructor(page: Page) {
    super(page);
    this.notebookReposPage = new NotebookReposPage(page);
  }

  async verifyPageStructure(): Promise<void> {
    await expect(this.notebookReposPage.zeppelinPageHeader).toBeVisible();
    await expect(this.notebookReposPage.pageDescription).toBeVisible();
  }

  async verifyRepositoryListDisplayed(): Promise<void> {
    const count = await this.notebookReposPage.getRepositoryItemCount();
    expect(count).toBeGreaterThan(0);
  }

  async verifyAllRepositoriesRendered(): Promise<number> {
    const count = await this.notebookReposPage.getRepositoryItemCount();
    expect(count).toBeGreaterThan(0);
    return count;
  }

  async getRepositoryItem(repoName: string): Promise<NotebookRepoItemPage> {
    return new NotebookRepoItemPage(this.page, repoName);
  }

  async verifyRepositoryCardDisplayed(repoName: string): Promise<void> {
    const repoItem = await this.getRepositoryItem(repoName);
    await expect(repoItem.repositoryCard).toBeVisible();
    await expect(repoItem.repositoryName).toContainText(repoName);
  }
}

export class NotebookRepoItemUtil extends BasePage {
  private repoItemPage: NotebookRepoItemPage;

  constructor(page: Page, repoName: string) {
    super(page);
    this.repoItemPage = new NotebookRepoItemPage(page, repoName);
  }

  async verifyDisplayMode(): Promise<void> {
    await expect(this.repoItemPage.editButton).toBeVisible();
    const isEditMode = await this.repoItemPage.isEditMode();
    expect(isEditMode).toBe(false);
  }

  async verifyEditMode(): Promise<void> {
    await expect(this.repoItemPage.saveButton).toBeVisible();
    await expect(this.repoItemPage.cancelButton).toBeVisible();
    const isEditMode = await this.repoItemPage.isEditMode();
    expect(isEditMode).toBe(true);
  }

  async enterEditMode(): Promise<void> {
    await this.repoItemPage.clickEdit();
    await this.verifyEditMode();
  }

  async exitEditModeByCancel(): Promise<void> {
    await this.repoItemPage.clickCancel();
    await this.verifyDisplayMode();
  }

  async exitEditModeBySave(): Promise<void> {
    await this.repoItemPage.clickSave();
    await this.verifyDisplayMode();
  }

  async verifySettingsDisplayed(): Promise<void> {
    const settingCount = await this.repoItemPage.getSettingCount();
    expect(settingCount).toBeGreaterThan(0);
  }

  async verifyInputTypeSettingInEditMode(settingName: string): Promise<void> {
    const isVisible = await this.repoItemPage.isInputVisible(settingName);
    expect(isVisible).toBe(true);
  }

  async verifyDropdownTypeSettingInEditMode(settingName: string): Promise<void> {
    const isVisible = await this.repoItemPage.isDropdownVisible(settingName);
    expect(isVisible).toBe(true);
  }

  async updateInputSetting(settingName: string, value: string): Promise<void> {
    await this.repoItemPage.fillSettingInput(settingName, value);
    const inputValue = await this.repoItemPage.getSettingInputValue(settingName);
    expect(inputValue).toBe(value);
  }

  async updateDropdownSetting(settingName: string, optionValue: string): Promise<void> {
    await this.repoItemPage.selectSettingDropdown(settingName, optionValue);
  }

  async verifySaveButtonDisabled(): Promise<void> {
    const isEnabled = await this.repoItemPage.isSaveButtonEnabled();
    expect(isEnabled).toBe(false);
  }

  async verifySaveButtonEnabled(): Promise<void> {
    const isEnabled = await this.repoItemPage.isSaveButtonEnabled();
    expect(isEnabled).toBe(true);
  }

  async verifyFormReset(settingName: string, originalValue: string): Promise<void> {
    const currentValue = await this.repoItemPage.getSettingValue(settingName);
    expect(currentValue.trim()).toBe(originalValue.trim());
  }

  async performCompleteEditWorkflow(settingName: string, newValue: string, isInput: boolean = true): Promise<void> {
    await this.enterEditMode();
    if (isInput) {
      await this.updateInputSetting(settingName, newValue);
    } else {
      await this.updateDropdownSetting(settingName, newValue);
    }
    await this.verifySaveButtonEnabled();
    await this.exitEditModeBySave();
  }
}
