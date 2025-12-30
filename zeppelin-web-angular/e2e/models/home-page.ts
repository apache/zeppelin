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

export class HomePage extends BasePage {
  readonly notebookSection: Locator;
  readonly helpSection: Locator;
  readonly communitySection: Locator;
  readonly zeppelinLogo: Locator;
  readonly anonymousUserIndicator: Locator;
  readonly welcomeSection: Locator;
  readonly moreInfoGrid: Locator;
  readonly notebookColumn: Locator;
  readonly helpCommunityColumn: Locator;
  readonly welcomeDescription: Locator;
  readonly refreshNoteButton: Locator;
  readonly notebookHeading: Locator;
  readonly helpHeading: Locator;
  readonly communityHeading: Locator;
  readonly createNoteModal: Locator;
  readonly createNoteButton: Locator;
  readonly notebookNameInput: Locator;
  readonly externalLinks: {
    documentation: Locator;
    mailingList: Locator;
    issuesTracking: Locator;
    github: Locator;
  };
  readonly nodeList: {
    createNewNoteLink: Locator;
    importNoteLink: Locator;
    filterInput: Locator;
    tree: Locator;
    noteActions: {
      renameNote: Locator;
      clearOutput: Locator;
      moveToTrash: Locator;
    };
  };

  constructor(page: Page) {
    super(page);
    this.notebookSection = page.locator('text=Notebook').first();
    this.helpSection = page.locator('text=Help').first();
    this.communitySection = page.locator('text=Community').first();
    this.zeppelinLogo = page.locator('text=Zeppelin').first();
    this.anonymousUserIndicator = page.locator('text=anonymous');
    this.welcomeSection = page.locator('.welcome');
    this.moreInfoGrid = page.locator('.more-info');
    this.notebookColumn = page.locator('[nz-col]').first();
    this.helpCommunityColumn = page.locator('[nz-col]').last();
    this.welcomeDescription = page.locator('.welcome').getByText('Zeppelin is web-based notebook');
    this.refreshNoteButton = page.locator('a.refresh-note');
    this.notebookHeading = this.notebookColumn.locator('h3');
    this.helpHeading = page.locator('h3').filter({ hasText: 'Help' });
    this.communityHeading = page.locator('h3').filter({ hasText: 'Community' });
    this.createNoteModal = page.locator('div.ant-modal-content');
    this.createNoteButton = this.createNoteModal.locator('button', { hasText: 'Create' });
    this.notebookNameInput = this.createNoteModal.locator('input[name="noteName"]');

    this.externalLinks = {
      documentation: page.locator('a[href*="zeppelin.apache.org/docs"]'),
      mailingList: page.locator('a[href*="community.html"]'),
      issuesTracking: page.locator('a[href*="issues.apache.org"]'),
      github: page.locator('a[href*="github.com/apache/zeppelin"]')
    };

    this.nodeList = {
      createNewNoteLink: page.locator('zeppelin-node-list a').filter({ hasText: 'Create new Note' }),
      importNoteLink: page.locator('zeppelin-node-list a').filter({ hasText: 'Import Note' }),
      filterInput: page.locator('zeppelin-node-list input[placeholder*="Filter"]'),
      tree: page.locator('zeppelin-node-list nz-tree'),
      noteActions: {
        renameNote: page.locator('.file .operation a[nztooltiptitle*="Rename note"]'),
        clearOutput: page.locator('.file .operation a[nztooltiptitle*="Clear output"]'),
        moveToTrash: page.locator('.file .operation a[nztooltiptitle*="Move note to Trash"]')
      }
    };
  }

  async navigateToLogin(): Promise<void> {
    await this.navigateToRoute('/login');
    // Wait for potential redirect to complete by checking URL change
    await this.waitForUrlNotContaining('#/login');
  }

  async isHomeContentDisplayed(): Promise<boolean> {
    return this.welcomeTitle.isVisible();
  }

  async isAnonymousUser(): Promise<boolean> {
    return this.anonymousUserIndicator.isVisible();
  }

  async clickZeppelinLogo(): Promise<void> {
    await this.zeppelinLogo.click({ timeout: 15000 });
  }

  async getWelcomeHeadingText(): Promise<string> {
    const text = await this.welcomeTitle.textContent();
    return text || '';
  }

  async getWelcomeDescriptionText(): Promise<string> {
    const text = await this.welcomeDescription.textContent();
    return text || '';
  }

  async clickRefreshNotes(): Promise<void> {
    await this.refreshNoteButton.click({ timeout: 15000 });
  }

  async isNotebookListVisible(): Promise<boolean> {
    return this.zeppelinNodeList.isVisible();
  }

  async clickCreateNewNote(): Promise<void> {
    await this.nodeList.createNewNoteLink.click({ timeout: 15000 });
    await this.createNoteModal.waitFor({ state: 'visible' });
  }

  async createNote(notebookName: string): Promise<void> {
    await this.clickCreateNewNote();

    // Wait for the modal form to be fully rendered with proper labels
    await this.page.waitForSelector('nz-form-label', { timeout: 10000 });

    await this.waitForFormLabels(['Note Name', 'Clone Note']);

    // Fill and verify the notebook name input
    await this.fillAndVerifyInput(this.notebookNameInput, notebookName);

    // Click the 'Create' button in the modal
    await expect(this.createNoteButton).toBeEnabled({ timeout: 5000 });
    await this.createNoteButton.click({ timeout: 15000 });
    await this.waitForPageLoad();
  }

  async clickImportNote(): Promise<void> {
    await this.nodeList.importNoteLink.click({ timeout: 15000 });
  }

  async filterNotes(searchTerm: string): Promise<void> {
    await this.nodeList.filterInput.fill(searchTerm, { timeout: 15000 });
  }

  async waitForRefreshToComplete(): Promise<void> {
    await this.waitForElementAttribute('a.refresh-note i[nz-icon]', 'nzSpin', false);
  }

  async getDocumentationLinkHref(): Promise<string | null> {
    return this.externalLinks.documentation.getAttribute('href');
  }
}
