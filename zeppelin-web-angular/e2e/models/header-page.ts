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
import { BasePage } from './base-page';

export class HeaderPage extends BasePage {
  readonly header: Locator;
  readonly brandLogo: Locator;
  readonly brandLink: Locator;
  readonly notebookMenuItem: Locator;
  readonly notebookDropdownTrigger: Locator;
  readonly notebookDropdown: Locator;
  readonly jobMenuItem: Locator;
  readonly userDropdownTrigger: Locator;
  readonly userBadge: Locator;
  readonly userDropdown: Locator;
  readonly searchInput: Locator;
  readonly themeToggleButton: Locator;
  readonly connectionStatusBadge: Locator;

  readonly userMenuItems: {
    aboutZeppelin: Locator;
    interpreter: Locator;
    notebookRepos: Locator;
    credential: Locator;
    configuration: Locator;
    logout: Locator;
    switchToClassicUI: Locator;
  };

  constructor(page: Page) {
    super(page);
    this.header = page.locator('.header');
    this.brandLogo = page.locator('.header .brand .logo');
    this.brandLink = page.locator('.header .brand');
    this.notebookMenuItem = page.locator('[nz-menu-item]').filter({ hasText: 'Notebook' });
    this.notebookDropdownTrigger = page.locator('.node-list-trigger');
    this.notebookDropdown = page.locator('zeppelin-node-list');
    this.jobMenuItem = page.getByRole('link', { name: 'Job' });
    this.userDropdownTrigger = page.locator('.header .user .status');
    this.userBadge = page.locator('.header .user nz-badge');
    this.userDropdown = page.locator('ul[nz-menu]').filter({ has: page.getByText('About Zeppelin') });
    this.searchInput = page.locator('.header .search input[type="text"]');
    this.themeToggleButton = page.locator('zeppelin-theme-toggle button');
    this.connectionStatusBadge = page.locator('.header .user nz-badge');

    this.userMenuItems = {
      aboutZeppelin: page.getByText('About Zeppelin', { exact: true }),
      interpreter: page.getByRole('link', { name: 'Interpreter' }),
      notebookRepos: page.getByRole('link', { name: 'Notebook Repos' }),
      credential: page.getByRole('link', { name: 'Credential' }),
      configuration: page.getByRole('link', { name: 'Configuration' }),
      logout: page.getByText('Logout', { exact: true }),
      switchToClassicUI: page.getByRole('link', { name: 'Switch to Classic UI' })
    };
  }

  async clickBrandLogo(): Promise<void> {
    await this.brandLink.waitFor({ state: 'visible', timeout: 10000 });
    await this.brandLink.click();
  }

  async clickNotebookMenu(): Promise<void> {
    await this.notebookDropdownTrigger.waitFor({ state: 'visible', timeout: 10000 });
    await this.notebookDropdownTrigger.click();
  }

  async clickJobMenu(): Promise<void> {
    await this.jobMenuItem.waitFor({ state: 'visible', timeout: 10000 });
    await this.jobMenuItem.click();
  }

  async clickUserDropdown(): Promise<void> {
    await this.userDropdownTrigger.waitFor({ state: 'visible', timeout: 10000 });
    await this.userDropdownTrigger.click();
  }

  async clickAboutZeppelin(): Promise<void> {
    await this.userMenuItems.aboutZeppelin.click();
  }

  async clickInterpreter(): Promise<void> {
    await this.userMenuItems.interpreter.click();
  }

  async clickNotebookRepos(): Promise<void> {
    await this.userMenuItems.notebookRepos.click();
  }

  async clickCredential(): Promise<void> {
    await this.userMenuItems.credential.click();
  }

  async clickConfiguration(): Promise<void> {
    await this.userMenuItems.configuration.click();
  }

  async clickLogout(): Promise<void> {
    await this.userMenuItems.logout.click();
  }

  async clickSwitchToClassicUI(): Promise<void> {
    await this.userMenuItems.switchToClassicUI.click();
  }

  async isHeaderVisible(): Promise<boolean> {
    return this.header.isVisible();
  }

  async getUsernameText(): Promise<string> {
    return (await this.userBadge.textContent()) || '';
  }

  async getConnectionStatus(): Promise<string> {
    const status = await this.connectionStatusBadge.locator('.ant-badge-status-dot').getAttribute('class');
    if (status?.includes('success')) {
      return 'success';
    }
    if (status?.includes('error')) {
      return 'error';
    }
    return 'unknown';
  }

  async isNotebookDropdownVisible(): Promise<boolean> {
    return this.notebookDropdown.isVisible();
  }

  async isUserDropdownVisible(): Promise<boolean> {
    return this.userDropdown.isVisible();
  }

  async isLogoutMenuItemVisible(): Promise<boolean> {
    return this.userMenuItems.logout.isVisible();
  }

  async searchNote(query: string): Promise<void> {
    await this.searchInput.waitFor({ state: 'visible', timeout: 10000 });
    await this.searchInput.fill(query);
    await this.page.keyboard.press('Enter');
  }
}
