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
import { getCurrentPath, waitForUrlNotContaining } from '../utils';
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
  readonly welcomeSection: Locator;
  readonly moreInfoGrid: Locator;
  readonly notebookColumn: Locator;
  readonly helpCommunityColumn: Locator;
  readonly welcomeDescription: Locator;
  readonly refreshNoteButton: Locator;
  readonly refreshIcon: Locator;
  readonly notebookList: Locator;
  readonly notebookHeading: Locator;
  readonly helpHeading: Locator;
  readonly communityHeading: Locator;
  readonly helpHeading: Locator;
  readonly communityHeading: Locator;
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
    this.welcomeSection = page.locator('.welcome');
    this.moreInfoGrid = page.locator('.more-info');
    this.notebookColumn = page.locator('[nz-col]').first();
    this.helpCommunityColumn = page.locator('[nz-col]').last();
    this.welcomeDescription = page.locator('.welcome').getByText('Zeppelin is web-based notebook');
    this.refreshNoteButton = page.locator('a.refresh-note');
    this.refreshIcon = page.locator('a.refresh-note i[nz-icon]');
    this.notebookList = page.locator('zeppelin-node-list');
    this.notebookHeading = this.notebookColumn.locator('h3');
    this.helpHeading = page.locator('h3').filter({ hasText: 'Help' });
    this.communityHeading = page.locator('h3').filter({ hasText: 'Community' });
    this.helpHeading = page.locator('h3').filter({ hasText: 'Help' });
    this.communityHeading = page.locator('h3').filter({ hasText: 'Community' });

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
    // Wait for potential redirect to complete by checking URL change
    await waitForUrlNotContaining(this.page, '#/login');
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

  getCurrentPath(): string {
    return getCurrentPath(this.page);
  }

  async getPageTitle(): Promise<string> {
    return this.page.title();
  }

  async getWelcomeHeadingText(): Promise<string> {
    const text = await this.welcomeHeading.textContent();
    return text || '';
  }

  async getWelcomeDescriptionText(): Promise<string> {
    const text = await this.welcomeDescription.textContent();
    return text || '';
  }

  async clickRefreshNotes(): Promise<void> {
    await this.refreshNoteButton.click();
  }

  async isNotebookListVisible(): Promise<boolean> {
    return this.notebookList.isVisible();
  }
}
