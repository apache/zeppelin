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
import { BasePage } from './base-page';

export class DarkModePage extends BasePage {
  readonly themeToggleButton: Locator;
  readonly rootElement: Locator;

  constructor(page: Page) {
    super(page);
    this.themeToggleButton = page.locator('zeppelin-theme-toggle button');
    this.rootElement = page.locator('html');
  }

  async toggleTheme() {
    await this.themeToggleButton.click({ timeout: 15000 });
  }

  async assertDarkTheme() {
    await expect(this.rootElement).toHaveClass(/dark/, { timeout: 10000 });
    await expect(this.rootElement).toHaveAttribute('data-theme', 'dark');
    await expect(this.themeToggleButton).toHaveText('dark_mode');
  }

  async assertLightTheme() {
    await expect(this.rootElement).toHaveClass(/light/, { timeout: 10000 });
    await expect(this.rootElement).toHaveAttribute('data-theme', 'light');
    await expect(this.themeToggleButton).toHaveText('light_mode');
  }

  async assertSystemTheme() {
    await expect(this.themeToggleButton).toHaveText('smart_toy', { timeout: 60000 });
  }

  async setThemeInLocalStorage(theme: 'light' | 'dark' | 'system') {
    await this.page.evaluate(themeValue => {
      if (typeof window !== 'undefined' && window.localStorage) {
        window.localStorage.setItem('zeppelin-theme', themeValue);
      }
    }, theme);
  }

  async clearLocalStorage() {
    await this.page.evaluate(() => {
      if (typeof window !== 'undefined' && window.localStorage) {
        window.localStorage.clear();
      }
    });
  }
}
