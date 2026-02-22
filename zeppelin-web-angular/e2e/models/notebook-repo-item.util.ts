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
import { NotebookRepoItemPage } from './notebook-repos-page';
import { BasePage } from './base-page';

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
}
