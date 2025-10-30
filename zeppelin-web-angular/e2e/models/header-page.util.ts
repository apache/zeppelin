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

import { expect, Page } from '@playwright/test';
import { HeaderPage } from './header-page';
import { AboutZeppelinModal } from './about-zeppelin-modal';
import { NodeListPage } from './node-list-page';

export class HeaderPageUtil {
  constructor(
    private readonly page: Page,
    private readonly headerPage: HeaderPage
  ) {}

  async verifyHeaderIsDisplayed(): Promise<void> {
    await expect(this.headerPage.header).toBeVisible();
    await expect(this.headerPage.brandLogo).toBeVisible();
    await expect(this.headerPage.notebookMenuItem).toBeVisible();
    await expect(this.headerPage.jobMenuItem).toBeVisible();
    await expect(this.headerPage.userDropdownTrigger).toBeVisible();
    await expect(this.headerPage.searchInput).toBeVisible();
    await expect(this.headerPage.themeToggleButton).toBeVisible();
  }

  async verifyNavigationToHomePage(): Promise<void> {
    await this.headerPage.clickBrandLogo();
    await this.page.waitForURL(/\/(#\/)?$/);
    const url = this.page.url();
    expect(url).toMatch(/\/(#\/)?$/);
  }

  async verifyNavigationToJobManager(): Promise<void> {
    await this.headerPage.clickJobMenu();
    await this.page.waitForURL(/jobmanager/);
    expect(this.page.url()).toContain('jobmanager');
  }

  async verifyUserDropdownOpens(): Promise<void> {
    await this.headerPage.clickUserDropdown();
    await expect(this.headerPage.userMenuItems.aboutZeppelin).toBeVisible();
  }

  async verifyNotebookDropdownOpens(): Promise<void> {
    await this.headerPage.clickNotebookMenu();
    // Target the header dropdown version specifically using ng-reflect-header-mode attribute
    const dropdownNodeList = this.page.locator('zeppelin-node-list[ng-reflect-header-mode="true"]');
    await expect(dropdownNodeList).toBeVisible();
  }

  async verifyAboutZeppelinModalOpens(): Promise<void> {
    await this.headerPage.clickUserDropdown();
    await this.headerPage.clickAboutZeppelin();

    const aboutModal = new AboutZeppelinModal(this.page);
    await expect(aboutModal.modal).toBeVisible();
  }

  async verifySearchNavigation(query: string): Promise<void> {
    await this.headerPage.searchNote(query);
    await this.page.waitForURL(/search/);
    expect(this.page.url()).toContain('search');
    expect(this.page.url()).toContain(query);
  }

  async verifyConnectionStatus(): Promise<void> {
    const status = await this.headerPage.getConnectionStatus();
    expect(['success', 'error']).toContain(status);
  }

  async verifyUserMenuItemsVisible(isLoggedIn: boolean): Promise<void> {
    await this.headerPage.clickUserDropdown();
    await expect(this.headerPage.userMenuItems.aboutZeppelin).toBeVisible();
    await expect(this.headerPage.userMenuItems.interpreter).toBeVisible();
    await expect(this.headerPage.userMenuItems.notebookRepos).toBeVisible();
    await expect(this.headerPage.userMenuItems.credential).toBeVisible();
    await expect(this.headerPage.userMenuItems.configuration).toBeVisible();
    await expect(this.headerPage.userMenuItems.switchToClassicUI).toBeVisible();

    if (isLoggedIn) {
      const username = await this.headerPage.getUsernameText();
      expect(username).not.toBe('anonymous');
      await expect(this.headerPage.userMenuItems.logout).toBeVisible();
    }
  }

  async openNotebookDropdownAndVerifyNodeList(): Promise<void> {
    await this.headerPage.clickNotebookMenu();
    await expect(this.headerPage.notebookDropdown).toBeVisible();

    const nodeList = new NodeListPage(this.page);
    await expect(nodeList.createNewNoteButton).toBeVisible();
    await expect(nodeList.importNoteButton).toBeVisible();
    await expect(nodeList.filterInput).toBeVisible();
    await expect(nodeList.treeView).toBeVisible();
  }

  async navigateToInterpreterSettings(): Promise<void> {
    await this.headerPage.clickUserDropdown();
    await this.headerPage.clickInterpreter();
    await this.page.waitForURL(/interpreter/);
    expect(this.page.url()).toContain('interpreter');
  }

  async navigateToNotebookRepos(): Promise<void> {
    await this.headerPage.clickUserDropdown();
    await this.headerPage.clickNotebookRepos();
    await this.page.waitForURL(/notebook-repos/);
    expect(this.page.url()).toContain('notebook-repos');
  }

  async navigateToCredential(): Promise<void> {
    await this.headerPage.clickUserDropdown();
    await this.headerPage.clickCredential();
    await this.page.waitForURL(/credential/);
    expect(this.page.url()).toContain('credential');
  }

  async navigateToConfiguration(): Promise<void> {
    await this.headerPage.clickUserDropdown();
    await this.headerPage.clickConfiguration();
    await this.page.waitForURL(/configuration/);
    expect(this.page.url()).toContain('configuration');
  }
}
