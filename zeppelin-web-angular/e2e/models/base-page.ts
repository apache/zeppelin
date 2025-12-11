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
    if (this.page.isClosed()) {
      console.log('Page is closed, cannot click E2E test folder');
      return;
    }

    try {
      await this.e2eTestFolder.waitFor({ state: 'visible', timeout: 30000 });

      // Check if already expanded
      const openSwitcher = this.e2eTestFolder.locator('.ant-tree-switcher_open');
      const isAlreadyOpen = await openSwitcher.isVisible({ timeout: 2000 });

      if (!isAlreadyOpen) {
        // Try multiple click strategies
        const clickStrategies = [
          // Strategy 1: Simple click
          async () => await this.e2eTestFolder.click({ timeout: 5000 }),

          // Strategy 2: Force click
          async () => await this.e2eTestFolder.click({ force: true, timeout: 5000 }),

          // Strategy 3: Click switcher directly
          async () => {
            const switcher = this.e2eTestFolder.locator('.ant-tree-switcher');
            await switcher.click({ timeout: 5000 });
          },

          // Strategy 4: JavaScript click
          async () => await this.e2eTestFolder.evaluate(el => (el as HTMLElement).click()),

          // Strategy 5: Dispatch click event
          async () => await this.e2eTestFolder.dispatchEvent('click')
        ];

        let success = false;
        for (let i = 0; i < clickStrategies.length && !success; i++) {
          try {
            console.log(`Trying click strategy ${i + 1}`);
            await clickStrategies[i]();

            // Check if folder expanded
            await this.page.waitForSelector('.node', { state: 'visible', timeout: 3000 });
            success = true;
            console.log(`Click strategy ${i + 1} succeeded`);
          } catch (error) {
            console.log(`Click strategy ${i + 1} failed:`, error);
          }
        }

        if (!success) {
          throw new Error('All click strategies failed');
        }
      }

      await this.page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {
        console.log('Network idle timeout - continuing anyway');
      });
    } catch (error) {
      if (this.page.isClosed()) {
        console.log('Page closed during E2E folder click operation');
        return;
      }
      console.log('Error during E2E folder click:', error);
      throw error;
    }
  }
}
