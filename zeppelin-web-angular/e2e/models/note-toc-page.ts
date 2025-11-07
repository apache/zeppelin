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
import { NotebookKeyboardPage } from './notebook-keyboard-page';

export class NoteTocPage extends NotebookKeyboardPage {
  readonly tocToggleButton: Locator;
  readonly tocPanel: Locator;
  readonly tocTitle: Locator;
  readonly tocCloseButton: Locator;
  readonly tocEmptyMessage: Locator;
  readonly tocItems: Locator;

  constructor(page: Page) {
    super(page);
    this.tocToggleButton = page.getByRole('button', { name: 'Toggle Table of Contents' });
    this.tocPanel = page.locator('zeppelin-note-toc').first();
    this.tocTitle = page.getByText('Table of Contents');
    this.tocCloseButton = page
      .locator('button')
      .filter({ hasText: /close|×/ })
      .or(page.locator('[class*="close"]'))
      .first();
    this.tocEmptyMessage = page.getByText('Headings in the output show up here');
    this.tocItems = page.locator('[class*="toc"] li, [class*="heading"]');
  }

  async clickTocToggle(): Promise<void> {
    await this.tocToggleButton.click();
  }

  async clickTocClose(): Promise<void> {
    try {
      await this.tocCloseButton.click({ timeout: 5000 });
    } catch {
      // Fallback: try to click the TOC toggle again to close
      await this.tocToggleButton.click();
    }
  }

  async clickTocItem(index: number): Promise<void> {
    await this.tocItems.nth(index).click();
  }

  async getTocItemCount(): Promise<number> {
    return this.tocItems.count();
  }
}
