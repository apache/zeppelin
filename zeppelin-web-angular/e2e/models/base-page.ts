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

  readonly zeppelinNodeList: Locator;
  readonly zeppelinWorkspace: Locator;
  readonly zeppelinPageHeader: Locator;
  readonly zeppelinHeader: Locator;

  constructor(page: Page) {
    this.page = page;
    this.zeppelinNodeList = page.locator('zeppelin-node-list');
    this.zeppelinWorkspace = page.locator('zeppelin-workspace');
    this.zeppelinPageHeader = page.locator('zeppelin-page-header');
    this.zeppelinHeader = page.locator('zeppelin-header');
  }

  async waitForPageLoad(): Promise<void> {
    await this.page.waitForLoadState('domcontentloaded', { timeout: 15000 });
  }

  async navigateToRoute(
    route: string,
    options?: { timeout?: number; waitUntil?: 'load' | 'domcontentloaded' | 'networkidle' }
  ): Promise<void> {
    await this.page.goto(`/#${route}`, {
      waitUntil: 'domcontentloaded',
      timeout: 60000,
      ...options
    });
    await this.waitForPageLoad();
  }

  async navigateToHome(): Promise<void> {
    await this.navigateToRoute('/');
  }

  getCurrentPath(): string {
    const url = new URL(this.page.url());
    return url.hash || url.pathname;
  }

  async waitForUrlNotContaining(fragment: string): Promise<void> {
    await this.page.waitForURL(url => !url.toString().includes(fragment));
  }

  async getElementText(locator: Locator): Promise<string> {
    return (await locator.textContent()) || '';
  }
}
