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

import { Locator, Page, expect } from '@playwright/test';
import { BasePage } from './base-page';

export class HomePage extends BasePage {
  readonly welcomeHeading: Locator;
  readonly notebookSection: Locator;
  readonly helpSection: Locator;
  readonly communitySection: Locator;
  readonly createNewNoteButton: Locator;
  readonly importNoteButton: Locator;
  readonly searchInput: Locator;
  readonly filterInput: Locator;
  readonly zeppelinLogo: Locator;
  readonly anonymousUserIndicator: Locator;
  readonly tutorialNotebooks: {
    flinkTutorial: Locator;
    pythonTutorial: Locator;
    sparkTutorial: Locator;
    rTutorial: Locator;
    miscellaneousTutorial: Locator;
  };
  readonly externalLinks: {
    documentation: Locator;
    mailingList: Locator;
    issuesTracking: Locator;
    github: Locator;
  };

  constructor(page: Page) {
    super(page);
    this.welcomeHeading = page.locator('h1', { hasText: 'Welcome to Zeppelin!' });
    this.notebookSection = page.locator('text=Notebook').first();
    this.helpSection = page.locator('text=Help').first();
    this.communitySection = page.locator('text=Community').first();
    this.createNewNoteButton = page.locator('text=Create new Note');
    this.importNoteButton = page.locator('text=Import Note');
    this.searchInput = page.locator('textbox', { hasText: 'Search' });
    this.filterInput = page.locator('input[placeholder*="Filter"]');
    this.zeppelinLogo = page.locator('text=Zeppelin').first();
    this.anonymousUserIndicator = page.locator('text=anonymous');

    this.tutorialNotebooks = {
      flinkTutorial: page.locator('text=Flink Tutorial'),
      pythonTutorial: page.locator('text=Python Tutorial'),
      sparkTutorial: page.locator('text=Spark Tutorial'),
      rTutorial: page.locator('text=R Tutorial'),
      miscellaneousTutorial: page.locator('text=Miscellaneous Tutorial')
    };

    this.externalLinks = {
      documentation: page.locator('a[href*="zeppelin.apache.org/docs"]'),
      mailingList: page.locator('a[href*="community.html"]'),
      issuesTracking: page.locator('a[href*="issues.apache.org"]'),
      github: page.locator('a[href*="github.com/apache/zeppelin"]')
    };
  }

  async navigateToHome(): Promise<void> {
    await this.page.goto('/', { waitUntil: 'load' });
    await this.waitForPageLoad();
  }

  async navigateToLogin(): Promise<void> {
    await this.page.goto('/#/login', { waitUntil: 'load' });
    await this.waitForPageLoad();
  }

  async isHomeContentDisplayed(): Promise<boolean> {
    try {
      await expect(this.welcomeHeading).toBeVisible();
      return true;
    } catch {
      return false;
    }
  }

  async isAnonymousUser(): Promise<boolean> {
    try {
      await expect(this.anonymousUserIndicator).toBeVisible();
      return true;
    } catch {
      return false;
    }
  }


  async clickZeppelinLogo(): Promise<void> {
    await this.zeppelinLogo.click();
  }

  async getCurrentURL(): Promise<string> {
    return this.page.url();
  }

  async getPageTitle(): Promise<string> {
    return this.page.title();
  }
}