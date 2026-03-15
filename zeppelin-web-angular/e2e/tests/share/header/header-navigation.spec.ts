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

import { test, expect } from '@playwright/test';
import { HeaderPage } from '../../../models/header-page';
import { NodeListPage } from '../../../models/node-list-page';
import { addPageAnnotationBeforeEach, PAGES, performLoginIfRequired, waitForZeppelinReady } from '../../../utils';

test.describe('Header Navigation', () => {
  let headerPage: HeaderPage;

  addPageAnnotationBeforeEach(PAGES.SHARE.HEADER);

  test.beforeEach(async ({ page }) => {
    headerPage = new HeaderPage(page);

    await page.goto('/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
  });

  test('Given user is on any page, When viewing the header, Then all header elements should be visible', async () => {
    await expect(headerPage.header).toBeVisible();
    await expect(headerPage.brandLogo).toBeVisible();
    await expect(headerPage.notebookMenuItem).toBeVisible();
    await expect(headerPage.jobMenuItem).toBeVisible();
    await expect(headerPage.userDropdownTrigger).toBeVisible();
    await expect(headerPage.searchInput).toBeVisible();
    await expect(headerPage.themeToggleButton).toBeVisible();
  });

  test('Given user is on any page, When clicking the Zeppelin logo, Then user should navigate to home page', async ({
    page
  }) => {
    await headerPage.clickBrandLogo();
    await page.waitForURL(/\/(#\/)?$/);
    expect(page.url()).toMatch(/\/(#\/)?$/);
  });

  test('Given user is on home page, When clicking the Job menu item, Then user should navigate to Job Manager page', async ({
    page
  }) => {
    await headerPage.clickJobMenu();
    await page.waitForURL(/jobmanager/);
    expect(page.url()).toContain('jobmanager');
  });

  test('Given user is on home page, When clicking the Notebook dropdown, Then dropdown with node list should open', async ({
    page
  }) => {
    await headerPage.clickNotebookMenu();
    await expect(headerPage.notebookDropdown).toBeVisible();

    const nodeList = new NodeListPage(page);
    await expect(nodeList.createNewNoteButton).toBeVisible();
  });

  test('Given user is on home page, When clicking the user dropdown, Then user menu should open', async () => {
    await headerPage.clickUserDropdown();
    await expect(headerPage.userMenuItems.aboutZeppelin).toBeVisible();
  });

  test('Given user opens user dropdown, When all menu items are displayed, Then menu items should include settings and configuration options', async () => {
    const isAnonymous = (await headerPage.getUsernameText()).includes('anonymous');
    await headerPage.clickUserDropdown();
    await expect(headerPage.userMenuItems.aboutZeppelin).toBeVisible();
    await expect(headerPage.userMenuItems.interpreter).toBeVisible();
    await expect(headerPage.userMenuItems.notebookRepos).toBeVisible();
    await expect(headerPage.userMenuItems.credential).toBeVisible();
    await expect(headerPage.userMenuItems.configuration).toBeVisible();
    await expect(headerPage.userMenuItems.switchToClassicUI).toBeVisible();
    if (!isAnonymous) {
      expect(await headerPage.getUsernameText()).not.toBe('anonymous');
      await expect(headerPage.userMenuItems.logout).toBeVisible();
    }
  });

  test('Given user opens user dropdown, When clicking Interpreter menu item, Then user should navigate to Interpreter settings page', async ({
    page
  }) => {
    await headerPage.clickUserDropdown();
    await headerPage.clickInterpreter();
    await page.waitForURL(/interpreter/);
    expect(page.url()).toContain('interpreter');
  });

  test('Given user opens user dropdown, When clicking Notebook Repos menu item, Then user should navigate to Notebook Repos page', async ({
    page
  }) => {
    await headerPage.clickUserDropdown();
    await headerPage.clickNotebookRepos();
    await page.waitForURL(/notebook-repos/);
    expect(page.url()).toContain('notebook-repos');
  });

  test('Given user opens user dropdown, When clicking Credential menu item, Then user should navigate to Credential page', async ({
    page
  }) => {
    await headerPage.clickUserDropdown();
    await headerPage.clickCredential();
    await page.waitForURL(/credential/);
    expect(page.url()).toContain('credential');
  });

  test('Given user opens user dropdown, When clicking Configuration menu item, Then user should navigate to Configuration page', async ({
    page
  }) => {
    await headerPage.clickUserDropdown();
    await headerPage.clickConfiguration();
    await page.waitForURL(/configuration/);
    expect(page.url()).toContain('configuration');
  });
});
