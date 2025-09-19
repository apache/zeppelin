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

export class BasePage {
  readonly page: Page;
  readonly loadingScreen: Locator;

  constructor(page: Page) {
    this.page = page;
    this.loadingScreen = page.locator('.spin-text');
  }

  async waitForPageLoad(): Promise<void> {
    await this.page.waitForLoadState('domcontentloaded');
    try {
      await this.loadingScreen.waitFor({ state: 'hidden', timeout: 5000 });
    } catch {
      console.log('Loading screen not found');
    }
  }
}
