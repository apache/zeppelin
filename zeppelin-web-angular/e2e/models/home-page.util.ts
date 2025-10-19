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
  }> {
    return await getBasicPageMetadata(this.page);
  }

  async getHomePageMetadata(): Promise<{
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

  async verifyResponsiveGrid(): Promise<void> {
    await expect(this.homePage.moreInfoGrid).toBeVisible();
    await expect(this.homePage.notebookColumn).toBeVisible();
    await expect(this.homePage.helpCommunityColumn).toBeVisible();
  }

  async verifyWelcomeSection(): Promise<void> {
    await expect(this.homePage.welcomeSection).toBeVisible();
    await expect(this.homePage.welcomeHeading).toBeVisible();

    const headingText = await this.homePage.getWelcomeHeadingText();
    expect(headingText.trim()).toBe('Welcome to Zeppelin!');

    const welcomeText = await this.homePage.welcomeDescription.textContent();
    expect(welcomeText).toContain('web-based notebook');
    expect(welcomeText).toContain('interactive data analytics');
  }

  async verifyNotebookSection(): Promise<void> {
    await expect(this.homePage.notebookSection).toBeVisible();
    await expect(this.homePage.notebookHeading).toBeVisible();
    await expect(this.homePage.refreshNoteButton).toBeVisible();

    // Wait for notebook list to load with timeout
    await this.page.waitForSelector('zeppelin-node-list', { timeout: 10000 });
    await expect(this.homePage.notebookList).toBeVisible();

    // Additional wait for content to load
    await this.page.waitForTimeout(1000);
  }

  async verifyNotebookRefreshFunctionality(): Promise<void> {
    await this.homePage.clickRefreshNotes();

    // Wait for refresh operation to complete
    await this.page.waitForTimeout(2000);

    // Ensure the notebook list is still visible after refresh
    await expect(this.homePage.notebookList).toBeVisible();
    const isStillVisible = await this.homePage.isNotebookListVisible();
    expect(isStillVisible).toBe(true);
  }

  async verifyHelpSection(): Promise<void> {
    await expect(this.homePage.helpSection).toBeVisible();
    await expect(this.homePage.helpHeading).toBeVisible();
  }

  async verifyCommunitySection(): Promise<void> {
    await expect(this.homePage.communitySection).toBeVisible();
    await expect(this.homePage.communityHeading).toBeVisible();
  }

  async testExternalLinkTargets(): Promise<{
    documentationHref: string | null;
    mailingListHref: string | null;
    issuesTrackingHref: string | null;
    githubHref: string | null;
  }> {
    // Get the parent links that contain the text
    const docLink = this.page.locator('a').filter({ hasText: 'Zeppelin documentation' });
    const mailLink = this.page.locator('a').filter({ hasText: 'Mailing list' });
    const issuesLink = this.page.locator('a').filter({ hasText: 'Issues tracking' });
    const githubLink = this.page.locator('a').filter({ hasText: 'Github' });

    return {
      documentationHref: await docLink.getAttribute('href'),
      mailingListHref: await mailLink.getAttribute('href'),
      issuesTrackingHref: await issuesLink.getAttribute('href'),
      githubHref: await githubLink.getAttribute('href')
    };
  }
}
