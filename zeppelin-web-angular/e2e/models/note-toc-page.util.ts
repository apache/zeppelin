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

import { expect } from '@playwright/test';
import { NoteTocPage } from './note-toc-page';

export class NoteTocPageUtil {
  constructor(private readonly noteTocPage: NoteTocPage) {}

  async verifyTocPanelOpens(): Promise<void> {
    await this.noteTocPage.clickTocToggle();
    await expect(this.noteTocPage.tocPanel).toBeVisible();
  }

  async verifyTocTitleIsDisplayed(): Promise<void> {
    await expect(this.noteTocPage.tocTitle).toBeVisible();
    const titleText = await this.noteTocPage.tocTitle.textContent();
    expect(titleText).toBe('Table of Contents');
  }

  async verifyEmptyMessageIsDisplayed(): Promise<void> {
    await expect(this.noteTocPage.tocEmptyMessage).toBeVisible();
  }

  async verifyTocPanelCloses(): Promise<void> {
    await this.noteTocPage.clickTocClose();
    await expect(this.noteTocPage.tocPanel).not.toBeVisible();
  }
}
