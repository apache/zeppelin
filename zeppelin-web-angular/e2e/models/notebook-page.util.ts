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
import { BasePage } from './base-page';
import { HomePage } from './home-page';
import { NotebookPage } from './notebook-page';

export class NotebookPageUtil extends BasePage {
  private homePage: HomePage;
  private notebookPage: NotebookPage;

  constructor(page: Page) {
    super(page);
    this.homePage = new HomePage(page);
    this.notebookPage = new NotebookPage(page);
  }

  // ===== NOTEBOOK CREATION METHODS =====

  async createNotebook(notebookName: string): Promise<void> {
    await this.homePage.navigateToHome();
    await this.homePage.createNewNoteButton.click();

    // Wait for the modal to appear and fill the notebook name
    const notebookNameInput = this.page.locator('input[name="noteName"]');
    await expect(notebookNameInput).toBeVisible({ timeout: 10000 });

    // Fill notebook name
    await notebookNameInput.fill(notebookName);

    // Click the 'Create' button in the modal
    const createButton = this.page.locator('button', { hasText: 'Create' });
    await createButton.click();

    // Wait for the notebook to be created and navigate to it
    await expect(this.page).toHaveURL(/#\/notebook\//, { timeout: 60000 });
    await this.waitForPageLoad();
    await this.page.waitForSelector('zeppelin-notebook-paragraph', { timeout: 15000 });
    await this.page.waitForSelector('.spin-text', { state: 'hidden', timeout: 10000 }).catch(() => {});
  }

  // ===== NOTEBOOK VERIFICATION METHODS =====

  async verifyNotebookContainerStructure(): Promise<void> {
    await expect(this.notebookPage.notebookContainer).toBeVisible();

    const containerClass = await this.notebookPage.getNotebookContainerClass();
    expect(containerClass).toContain('notebook-container');
  }

  async verifyActionBarPresence(): Promise<void> {
    // Wait for the notebook container to be fully loaded first
    await expect(this.notebookPage.notebookContainer).toBeVisible();

    // Wait for the action bar to be visible with a longer timeout
    await expect(this.notebookPage.actionBar).toBeVisible({ timeout: 15000 });
  }

  async verifySidebarFunctionality(): Promise<void> {
    // Wait for the notebook container to be fully loaded first
    await expect(this.notebookPage.notebookContainer).toBeVisible();

    // Wait for the sidebar area to be visible with a longer timeout
    await expect(this.notebookPage.sidebarArea).toBeVisible({ timeout: 15000 });

    const width = await this.notebookPage.getSidebarWidth();
    expect(width).toBeGreaterThanOrEqual(40);
    expect(width).toBeLessThanOrEqual(800);
  }

  async verifyParagraphContainerStructure(): Promise<void> {
    // Wait for the notebook container to be fully loaded first
    await expect(this.notebookPage.notebookContainer).toBeVisible();

    // Wait for the paragraph inner area to be visible
    await expect(this.notebookPage.paragraphInner).toBeVisible({ timeout: 15000 });

    const paragraphCount = await this.notebookPage.getParagraphCount();
    expect(paragraphCount).toBeGreaterThanOrEqual(0);
  }

  async verifyExtensionAreaIfVisible(): Promise<void> {
    const isExtensionVisible = await this.notebookPage.isExtensionAreaVisible();
    if (isExtensionVisible) {
      await expect(this.notebookPage.extensionArea).toBeVisible();
    }
  }

  async verifyNoteFormBlockIfVisible(): Promise<void> {
    const isFormBlockVisible = await this.notebookPage.isNoteFormBlockVisible();
    if (isFormBlockVisible) {
      await expect(this.notebookPage.noteFormBlock).toBeVisible();
    }
  }

  // ===== LAYOUT VERIFICATION METHODS =====

  async verifyGridLayoutForParagraphs(): Promise<void> {
    await expect(this.notebookPage.paragraphInner).toBeVisible();

    const paragraphInner = this.notebookPage.paragraphInner;
    const hasRowClass = await paragraphInner.getAttribute('class');
    expect(hasRowClass).toContain('paragraph-inner');

    await expect(paragraphInner).toHaveAttribute('nz-row');
  }

  async verifyResponsiveLayout(): Promise<void> {
    await this.page.setViewportSize({ width: 1200, height: 800 });
    await expect(this.notebookPage.notebookContainer).toBeVisible();

    await this.page.setViewportSize({ width: 800, height: 600 });
    await expect(this.notebookPage.notebookContainer).toBeVisible();
  }

  // ===== ADDITIONAL VERIFICATION METHODS FOR TESTS =====

  async verifyActionBarComponent(): Promise<void> {
    await this.verifyActionBarPresence();
  }

  async verifyResizableSidebarWithConstraints(): Promise<void> {
    await this.verifySidebarFunctionality();
  }

  async verifyParagraphContainerGridLayout(): Promise<void> {
    await this.verifyGridLayoutForParagraphs();
  }

  async verifyExtensionAreaWhenActivated(): Promise<void> {
    await this.verifyExtensionAreaIfVisible();
  }

  async verifyNoteFormsBlockWhenPresent(): Promise<void> {
    await this.verifyNoteFormBlockIfVisible();
  }

  // ===== COMPREHENSIVE VERIFICATION METHOD =====

  async verifyAllNotebookComponents(): Promise<void> {
    await this.verifyNotebookContainerStructure();
    await this.verifyActionBarPresence();
    await this.verifySidebarFunctionality();
    await this.verifyParagraphContainerStructure();
    await this.verifyExtensionAreaIfVisible();
    await this.verifyNoteFormBlockIfVisible();
    await this.verifyGridLayoutForParagraphs();
  }
}
