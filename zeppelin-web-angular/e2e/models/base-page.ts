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

import { expect, Locator, Page } from '@playwright/test';

export const E2E_TEST_FOLDER = 'E2E_TEST_FOLDER';
export const BASE_URL = 'http://localhost:4200';

export class BasePage {
  readonly page: Page;

  readonly zeppelinNodeList: Locator;
  readonly zeppelinWorkspace: Locator;
  readonly zeppelinPageHeader: Locator;
  readonly zeppelinHeader: Locator;

  readonly modalTitle: Locator;

  readonly okButton: Locator;
  readonly cancelButton: Locator;
  readonly runButton: Locator;

  readonly welcomeTitle: Locator;

  constructor(page: Page) {
    this.page = page;
    this.zeppelinNodeList = page.locator('zeppelin-node-list');
    this.zeppelinWorkspace = page.locator('zeppelin-workspace');
    this.zeppelinPageHeader = page.locator('zeppelin-page-header');
    this.zeppelinHeader = page.locator('zeppelin-header');

    this.modalTitle = page.locator('.ant-modal-confirm-title, .ant-modal-title');

    this.okButton = page.locator('button:has-text("OK")');
    this.cancelButton = page.locator('button:has-text("Cancel")');
    this.runButton = page.locator('button:has-text("Run")');

    this.welcomeTitle = page.getByRole('heading', { name: 'Welcome to Zeppelin!' });
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

  async waitForUrlNotContaining(fragment: string): Promise<void> {
    await this.page.waitForURL(url => !url.toString().includes(fragment));
  }

  async getElementText(locator: Locator): Promise<string> {
    return (await locator.textContent()) || '';
  }

  async waitForFormLabels(labelTexts: string[], timeout = 10000): Promise<void> {
    const locators = labelTexts.map(text => this.page.locator('nz-form-label', { hasText: text }));
    await Promise.race(locators.map(l => l.waitFor({ state: 'attached', timeout })));
  }

  async waitForElementAttribute(
    selector: string,
    attribute: string,
    exists: boolean = true,
    timeout = 10000
  ): Promise<void> {
    const locator = this.page.locator(selector);
    if (exists) {
      await expect(locator).toHaveAttribute(attribute, { timeout });
    } else {
      await expect(locator).not.toHaveAttribute(attribute, { timeout });
    }
  }

  async fillAndVerifyInput(
    locator: Locator,
    value: string,
    options?: { timeout?: number; clearFirst?: boolean }
  ): Promise<void> {
    const { timeout = 10000 } = options || {};

    await expect(locator).toBeVisible({ timeout });
    await expect(locator).toBeEnabled({ timeout: 5000 });

    // Click first so Angular's form control is focused.
    // Then wait for Angular's async setValue cycle (e.g. "Untitled Note 1") to settle
    // before overwriting — otherwise Angular can clobber our fill() value.
    await locator.click();
    await expect(locator).not.toHaveValue('', { timeout: 5000 });
    await locator.fill(value);
    await expect(locator).toHaveValue(value, { timeout: 10000 });
  }
}
