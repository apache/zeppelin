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
  readonly e2eTestFolder: Locator;

  constructor(page: Page) {
    this.page = page;
    this.e2eTestFolder = page.locator(`[data-testid="folder-${E2E_TEST_FOLDER}"]`);
  }

  async waitForPageLoad(): Promise<void> {
    await this.page.waitForLoadState('domcontentloaded', { timeout: 15000 });
  }
}
