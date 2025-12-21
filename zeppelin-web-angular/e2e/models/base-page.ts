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

  // Common Zeppelin component locators
  readonly zeppelinNodeList: Locator;
  readonly zeppelinWorkspace: Locator;
  readonly zeppelinHeader: Locator;
  readonly zeppelinPageHeader: Locator;

  constructor(page: Page) {
    this.page = page;
    this.zeppelinNodeList = page.locator('zeppelin-node-list');
    this.zeppelinWorkspace = page.locator('zeppelin-workspace');
    this.zeppelinHeader = page.locator('zeppelin-header');
    this.zeppelinPageHeader = page.locator('zeppelin-page-header');
  }

  async waitForPageLoad(): Promise<void> {
    await this.page.waitForLoadState('domcontentloaded', { timeout: 15000 });
  }

  // Common navigation patterns
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

  // Common form interaction patterns
  async fillInput(locator: Locator, value: string, options?: { timeout?: number; force?: boolean }): Promise<void> {
    await locator.fill(value, { timeout: 15000, ...options });
  }

  async clickElement(locator: Locator, options?: { timeout?: number; force?: boolean }): Promise<void> {
    await locator.click({ timeout: 15000, ...options });
  }

  async getInputValue(locator: Locator): Promise<string> {
    return await locator.inputValue();
  }

  async isElementVisible(locator: Locator): Promise<boolean> {
    return await locator.isVisible();
  }

  async isElementEnabled(locator: Locator): Promise<boolean> {
    return await locator.isEnabled();
  }

  async getElementText(locator: Locator): Promise<string> {
    return (await locator.textContent()) || '';
  }
}
