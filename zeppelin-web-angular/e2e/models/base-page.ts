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
  readonly modalBody: Locator;
  readonly modalContent: Locator;

  readonly okButton: Locator;
  readonly cancelButton: Locator;
  readonly runButton: Locator;

  readonly deleteIcon: Locator;

  constructor(page: Page) {
    this.page = page;
    this.zeppelinNodeList = page.locator('zeppelin-node-list');
    this.zeppelinWorkspace = page.locator('zeppelin-workspace');
    this.zeppelinPageHeader = page.locator('zeppelin-page-header');
    this.zeppelinHeader = page.locator('zeppelin-header');

    this.modalTitle = page.locator('.ant-modal-confirm-title, .ant-modal-title');
    this.modalBody = page.locator('.ant-modal-confirm-content, .ant-modal-body');
    this.modalContent = page.locator('.ant-modal-body');

    this.okButton = page.locator('button:has-text("OK")');
    this.cancelButton = page.locator('button:has-text("Cancel")');
    this.runButton = page.locator('button:has-text("Run")');

    this.deleteIcon = page.locator('i[nztype="delete"], i.anticon-delete');
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

  async clickFirstVisibleElement(
    selectors: string[],
    options?: {
      parent?: Locator;
      visibilityTimeout?: number;
      clickTimeout?: number;
    }
  ): Promise<boolean> {
    const { parent, visibilityTimeout = 2000, clickTimeout = 5000 } = options || {};
    const baseLocator = parent || this.page;

    for (const selector of selectors) {
      try {
        const element = baseLocator.locator(selector);
        if (await element.isVisible({ timeout: visibilityTimeout })) {
          await element.click({ timeout: clickTimeout });
          return true;
        }
      } catch (e) {
        continue;
      }
    }
    return false;
  }

  async waitForFormLabels(labelTexts: string[], timeout = 10000): Promise<void> {
    await this.page.waitForFunction(
      texts => {
        const labels = Array.from(document.querySelectorAll('nz-form-label'));
        return texts.some(text => labels.some(l => l.textContent?.includes(text)));
      },
      labelTexts,
      { timeout }
    );
  }

  async waitForElementAttribute(
    selector: string,
    attribute: string,
    hasAttribute: boolean = true,
    timeout = 10000
  ): Promise<void> {
    await this.page.waitForFunction(
      ({ sel, attr, has }) => {
        const el = document.querySelector(sel);
        return el && (has ? el.hasAttribute(attr) : !el.hasAttribute(attr));
      },
      { sel: selector, attr: attribute, has: hasAttribute },
      { timeout }
    );
  }

  async waitForRouterOutletChild(timeout = 10000): Promise<void> {
    await this.page.waitForFunction(
      () => {
        const workspace = document.querySelector('zeppelin-workspace');
        const outlet = workspace?.querySelector('router-outlet');
        return outlet && outlet.nextElementSibling !== null;
      },
      { timeout }
    );
  }

  async verifyMultipleElementsVisible(locators: Locator[], options?: { timeout?: number }): Promise<void> {
    const { timeout } = options || {};
    for (const locator of locators) {
      await expect(locator).toBeVisible(timeout ? { timeout } : undefined);
    }
  }

  async waitForComponentAppears(selector: string, options?: { timeout?: number }): Promise<void> {
    const { timeout = 10000 } = options || {};
    await this.page.waitForFunction(sel => document.querySelector(sel) !== null, selector, { timeout });
  }

  async fillAndVerifyInput(
    locator: Locator,
    value: string,
    options?: { timeout?: number; clearFirst?: boolean }
  ): Promise<void> {
    const { timeout = 10000, clearFirst = true } = options || {};

    await expect(locator).toBeVisible({ timeout });
    await expect(locator).toBeEnabled({ timeout: 5000 });

    if (clearFirst) {
      await locator.clear();
    }

    await locator.fill(value);
    await expect(locator).toHaveValue(value);
  }
}
