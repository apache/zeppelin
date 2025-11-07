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
import { NotebookPage } from './notebook-page';

export class NotebookPageUtil extends BasePage {
  private notebookPage: NotebookPage;

  constructor(page: Page) {
    super(page);
    this.notebookPage = new NotebookPage(page);
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

  async verifyExtensionAreaIfVisible(): Promise<void> {
    await this.notebookPage.isExtensionAreaVisible();
    await expect(this.notebookPage.extensionArea).toBeVisible();
  }

  async verifyNoteFormBlockIfVisible(): Promise<void> {
    await this.notebookPage.isNoteFormBlockVisible();
    await expect(this.notebookPage.noteFormBlock).toBeVisible();
  }

  // ===== LAYOUT VERIFICATION METHODS =====

  async verifyGridLayoutForParagraphs(): Promise<void> {
    await expect(this.notebookPage.paragraphInner).toBeVisible();

    const paragraphInner = this.notebookPage.paragraphInner;
    const hasRowClass = await paragraphInner.getAttribute('class');
    expect(hasRowClass).toContain('paragraph-inner');

    await expect(paragraphInner).toHaveAttribute('nz-row');
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
}
