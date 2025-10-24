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
import { getBasicPageMetadata, waitForUrlNotContaining } from '../utils';
import { HomePage } from './home-page';

export class HomePageUtil {
  private homePage: HomePage;
  private page: Page;

  constructor(page: Page) {
    this.page = page;
    this.homePage = new HomePage(page);
  }

  async verifyAnonymousUserRedirectFromLogin(): Promise<{
    isLoginUrlMaintained: boolean;
    isHomeContentDisplayed: boolean;
    isAnonymousUser: boolean;
    currentPath: string;
  }> {
    await this.homePage.navigateToLogin();

    const currentPath = this.homePage.getCurrentPath();
    const isLoginUrlMaintained = currentPath.includes('#/login');
    const isHomeContentDisplayed = await this.homePage.isHomeContentDisplayed();
    const isAnonymousUser = await this.homePage.isAnonymousUser();

    return {
      isLoginUrlMaintained,
      isHomeContentDisplayed,
      isAnonymousUser,
      currentPath
    };
  }

  async verifyHomePageElements(): Promise<void> {
    await expect(this.homePage.welcomeHeading).toBeVisible();
    await expect(this.homePage.notebookSection).toBeVisible();
    await expect(this.homePage.helpSection).toBeVisible();
    await expect(this.homePage.communitySection).toBeVisible();
  }

  async verifyExternalLinks(): Promise<void> {
    await expect(this.homePage.externalLinks.documentation).toBeVisible();
    await expect(this.homePage.externalLinks.mailingList).toBeVisible();
    await expect(this.homePage.externalLinks.issuesTracking).toBeVisible();
    await expect(this.homePage.externalLinks.github).toBeVisible();
  }

  async testNavigationConsistency(): Promise<{
    pathBeforeClick: string;
    pathAfterClick: string;
    homeContentMaintained: boolean;
  }> {
    const pathBeforeClick = this.homePage.getCurrentPath();

    await this.homePage.clickZeppelinLogo();
    await this.homePage.waitForPageLoad();

    const pathAfterClick = this.homePage.getCurrentPath();
    const homeContentMaintained = await this.homePage.isHomeContentDisplayed();

    return {
      pathBeforeClick,
      pathAfterClick,
      homeContentMaintained
    };
  }

  async getPageMetadata(): Promise<{
    title: string;
    path: string;
    isAnonymous: boolean;
  }> {
    const basicMetadata = await getBasicPageMetadata(this.page);
    const isAnonymous = await this.homePage.isAnonymousUser();

    return {
      ...basicMetadata,
      isAnonymous
    };
  }

  async navigateToLoginAndWaitForRedirect(): Promise<void> {
    await this.page.goto('/#/login', { waitUntil: 'load' });
    await waitForUrlNotContaining(this.page, '#/login');
  }
}
