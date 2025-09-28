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

import { Page, expect } from '@playwright/test';
import { HomePage } from './home-page';

export class HomePageUtil {
  private homePage: HomePage;

  constructor(page: Page) {
    this.homePage = new HomePage(page);
  }

  async verifyAnonymousUserRedirectFromLogin(): Promise<{
    isLoginUrlMaintained: boolean;
    isHomeContentDisplayed: boolean;
    isAnonymousUser: boolean;
    currentUrl: string;
  }> {
    await this.homePage.navigateToLogin();
    
    const currentUrl = await this.homePage.getCurrentURL();
    const isLoginUrlMaintained = currentUrl.includes('#/login');
    const isHomeContentDisplayed = await this.homePage.isHomeContentDisplayed();
    const isAnonymousUser = await this.homePage.isAnonymousUser();

    return {
      isLoginUrlMaintained,
      isHomeContentDisplayed,
      isAnonymousUser,
      currentUrl
    };
  }

  async verifyHomePageIntegrity(): Promise<void> {
    await this.verifyHomePageElements();
    await this.verifyNotebookFunctionalities();
    await this.verifyTutorialNotebooks();
    await this.verifyExternalLinks();
  }

  async verifyHomePageElements(): Promise<void> {
    await expect(this.homePage.welcomeHeading).toBeVisible();
    await expect(this.homePage.notebookSection).toBeVisible();
    await expect(this.homePage.helpSection).toBeVisible();
    await expect(this.homePage.communitySection).toBeVisible();
  }

  async verifyNotebookFunctionalities(): Promise<void> {
    await expect(this.homePage.createNewNoteButton).toBeVisible();
    await expect(this.homePage.importNoteButton).toBeVisible();
    
    const filterInputCount = await this.homePage.filterInput.count();
    if (filterInputCount > 0) {
      await expect(this.homePage.filterInput).toBeVisible();
    }
  }

  async verifyTutorialNotebooks(): Promise<void> {
    await expect(this.homePage.tutorialNotebooks.flinkTutorial).toBeVisible();
    await expect(this.homePage.tutorialNotebooks.pythonTutorial).toBeVisible();
    await expect(this.homePage.tutorialNotebooks.sparkTutorial).toBeVisible();
    await expect(this.homePage.tutorialNotebooks.rTutorial).toBeVisible();
    await expect(this.homePage.tutorialNotebooks.miscellaneousTutorial).toBeVisible();
  }

  async verifyExternalLinks(): Promise<void> {
    const docCount = await this.homePage.externalLinks.documentation.count();
    const mailCount = await this.homePage.externalLinks.mailingList.count();
    const issuesCount = await this.homePage.externalLinks.issuesTracking.count();
    const githubCount = await this.homePage.externalLinks.github.count();
    
    if (docCount > 0) await expect(this.homePage.externalLinks.documentation).toBeVisible();
    if (mailCount > 0) await expect(this.homePage.externalLinks.mailingList).toBeVisible();
    if (issuesCount > 0) await expect(this.homePage.externalLinks.issuesTracking).toBeVisible();
    if (githubCount > 0) await expect(this.homePage.externalLinks.github).toBeVisible();
  }

  async testNavigationConsistency(): Promise<{
    urlBeforeClick: string;
    urlAfterClick: string;
    homeContentMaintained: boolean;
  }> {
    const urlBeforeClick = await this.homePage.getCurrentURL();
    
    await this.homePage.clickZeppelinLogo();
    await this.homePage.waitForPageLoad();
    
    const urlAfterClick = await this.homePage.getCurrentURL();
    const homeContentMaintained = await this.homePage.isHomeContentDisplayed();

    return {
      urlBeforeClick,
      urlAfterClick,
      homeContentMaintained
    };
  }

  async getPageMetadata(): Promise<{
    title: string;
    url: string;
    isAnonymous: boolean;
  }> {
    return {
      title: await this.homePage.getPageTitle(),
      url: await this.homePage.getCurrentURL(),
      isAnonymous: await this.homePage.isAnonymousUser()
    };
  }
}