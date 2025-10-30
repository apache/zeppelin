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

import { test } from '@playwright/test';
import { HeaderPage } from '../../../models/header-page';
import { HeaderPageUtil } from '../../../models/header-page.util';
import { addPageAnnotationBeforeEach, PAGES, performLoginIfRequired, waitForZeppelinReady } from '../../../utils';

test.describe('Header Navigation', () => {
  let headerPage: HeaderPage;
  let headerUtil: HeaderPageUtil;

  addPageAnnotationBeforeEach(PAGES.SHARE.HEADER);

  test.beforeEach(async ({ page }) => {
    headerPage = new HeaderPage(page);
    headerUtil = new HeaderPageUtil(page, headerPage);

    await page.goto('/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
  });

  test('Given user is on any page, When viewing the header, Then all header elements should be visible', async () => {
    await headerUtil.verifyHeaderIsDisplayed();
  });

  test('Given user is on any page, When clicking the Zeppelin logo, Then user should navigate to home page', async () => {
    await headerUtil.verifyNavigationToHomePage();
  });

  test('Given user is on home page, When clicking the Job menu item, Then user should navigate to Job Manager page', async () => {
    await headerUtil.verifyNavigationToJobManager();
  });

  test('Given user is on home page, When clicking the Notebook dropdown, Then dropdown with node list should open', async () => {
    await headerUtil.verifyNotebookDropdownOpens();
  });

  test('Given user is on home page, When clicking the user dropdown, Then user menu should open', async () => {
    await headerUtil.verifyUserDropdownOpens();
  });

  test('Given user opens user dropdown, When all menu items are displayed, Then menu items should include settings and configuration options', async () => {
    const isAnonymous = (await headerPage.getUsernameText()).includes('anonymous');
    await headerUtil.verifyUserMenuItemsVisible(!isAnonymous);
  });

  test('Given user opens user dropdown, When clicking Interpreter menu item, Then user should navigate to Interpreter settings page', async () => {
    await headerUtil.navigateToInterpreterSettings();
  });

  test('Given user opens user dropdown, When clicking Notebook Repos menu item, Then user should navigate to Notebook Repos page', async () => {
    await headerUtil.navigateToNotebookRepos();
  });

  test('Given user opens user dropdown, When clicking Credential menu item, Then user should navigate to Credential page', async () => {
    await headerUtil.navigateToCredential();
  });

  test('Given user opens user dropdown, When clicking Configuration menu item, Then user should navigate to Configuration page', async () => {
    await headerUtil.navigateToConfiguration();
  });
});
