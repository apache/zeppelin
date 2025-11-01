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
import { NoteTocPage } from './note-toc-page';

export class NoteTocPageUtil {
  constructor(
    private readonly page: Page,
    private readonly noteTocPage: NoteTocPage
  ) {}

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

  async verifyTocItemsAreDisplayed(expectedCount: number): Promise<void> {
    const count = await this.noteTocPage.getTocItemCount();
    expect(count).toBeGreaterThanOrEqual(expectedCount);
  }

  async verifyTocItemClick(itemIndex: number): Promise<void> {
    const initialScrollY = await this.page.evaluate(() => window.scrollY);
    await this.noteTocPage.clickTocItem(itemIndex);
    await this.page.waitForTimeout(500);
    const finalScrollY = await this.page.evaluate(() => window.scrollY);
    expect(finalScrollY).not.toBe(initialScrollY);
  }

  async openTocAndVerifyContent(): Promise<void> {
    await this.verifyTocPanelOpens();
    await this.verifyTocTitleIsDisplayed();
  }
}
