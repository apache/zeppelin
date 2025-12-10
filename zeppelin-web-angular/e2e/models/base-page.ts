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

export const E2E_TEST_FOLDER = 'E2E_TEST_FOLDER';
export const BASE_URL = 'http://localhost:4200';

export class BasePage {
  readonly page: Page;
  readonly loadingScreen: Locator;
  readonly e2eTestFolder: Locator;

  constructor(page: Page) {
    this.page = page;
    this.loadingScreen = page.locator('section.spin');
    this.e2eTestFolder = page.locator(`a.name:has-text("${E2E_TEST_FOLDER}")`);
  }

  async waitForPageLoad(): Promise<void> {
    await this.page.waitForLoadState('domcontentloaded', { timeout: 15000 });
  }

  async clickE2ETestFolder(): Promise<void> {
    await this.e2eTestFolder.waitFor({ state: 'visible', timeout: 30000 });

    // Check if already open
    const openSwitcher = this.e2eTestFolder.locator('.ant-tree-switcher_open');

    const isVisible = await openSwitcher.isVisible();
    if (!isVisible) {
      // Wait for any loading to complete before interaction
      await this.page.waitForLoadState('networkidle', { timeout: 10000 });

      // Click without waiting for completion, then verify the result
      await this.e2eTestFolder
        .click({
          force: true,
          timeout: 5000 // Short timeout for the click action itself
        })
        .catch(() => {
          console.log('Click action timeout - continuing anyway');
        });

      // Wait for the folder to expand by checking for child nodes
      await this.page
        .waitForSelector('.node', {
          state: 'visible',
          timeout: 15000
        })
        .catch(() => {
          console.log('Folder expansion timeout - continuing anyway');
        });
    }

    await this.page.waitForLoadState('networkidle', { timeout: 15000 });
  }
}
