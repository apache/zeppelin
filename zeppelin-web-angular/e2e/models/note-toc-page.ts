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

import { Locator, Page } from '@playwright/test';
import { NotebookKeyboardPage } from './notebook-keyboard-page';

export class NoteTocPage extends NotebookKeyboardPage {
  readonly tocToggleButton: Locator;
  readonly tocPanel: Locator;
  readonly tocTitle: Locator;
  readonly tocCloseButton: Locator;
  readonly tocListArea: Locator;
  readonly tocEmptyMessage: Locator;
  readonly tocItems: Locator;
  readonly codeEditor: Locator;
  readonly runButton: Locator;
  readonly addParagraphButton: Locator;

  constructor(page: Page) {
    super(page);
    this.tocToggleButton = page.locator('.sidebar-button').first();
    this.tocPanel = page.locator('zeppelin-note-toc').first();
    this.tocTitle = page.getByText('Table of Contents');
    this.tocCloseButton = page
      .locator('button')
      .filter({ hasText: /close|×/ })
      .or(page.locator('[class*="close"]'))
      .first();
    this.tocListArea = page.locator('[class*="toc"]').first();
    this.tocEmptyMessage = page.getByText('Headings in the output show up here');
    this.tocItems = page.locator('[class*="toc"] li, [class*="heading"]');
    this.codeEditor = page.locator('textarea, [contenteditable], .monaco-editor textarea').first();
    this.runButton = page
      .locator('button')
      .filter({ hasText: /run|실행|▶/ })
      .or(page.locator('[title*="run"], [aria-label*="run"]'))
      .first();
    this.addParagraphButton = page.locator('.add-paragraph-button').or(page.locator('button[title="Add Paragraph"]'));
  }

  async navigate(noteId: string): Promise<void> {
    await this.page.goto(`/#/notebook/${noteId}`);
    await this.waitForPageLoad();
  }

  async clickTocToggle(): Promise<void> {
    await this.tocToggleButton.click();
  }

  async clickTocClose(): Promise<void> {
    try {
      await this.tocCloseButton.click({ timeout: 5000 });
    } catch {
      // Fallback: try to click the TOC toggle again to close
      await this.tocToggleButton.click();
    }
  }

  async clickTocItem(index: number): Promise<void> {
    await this.tocItems.nth(index).click();
  }

  async isTocPanelVisible(): Promise<boolean> {
    try {
      return await this.tocPanel.isVisible({ timeout: 2000 });
    } catch {
      // Fallback to check if any TOC-related element is visible
      const fallbackToc = this.page.locator('[class*="toc"], zeppelin-note-toc');
      return await fallbackToc.first().isVisible({ timeout: 1000 });
    }
  }

  async getTocItemCount(): Promise<number> {
    return this.tocItems.count();
  }

  async getTocItemText(index: number): Promise<string> {
    return (await this.tocItems.nth(index).textContent()) || '';
  }

  async typeCodeInEditor(code: string): Promise<void> {
    await this.codeEditor.fill(code);
  }

  async runParagraph(): Promise<void> {
    await this.codeEditor.focus();
    await this.pressRunParagraph();
  }

  async addNewParagraph(): Promise<void> {
    // Use keyboard shortcut to add new paragraph below (Ctrl+Alt+B)
    await this.pressInsertBelow();
    // Wait for the second editor to appear
    await this.page
      .getByRole('textbox', { name: /Editor content/i })
      .nth(1)
      .waitFor();
  }

  async typeCodeInSecondEditor(code: string): Promise<void> {
    const secondEditor = this.page.getByRole('textbox', { name: /Editor content/i }).nth(1);
    await secondEditor.fill(code);
  }

  async runSecondParagraph(): Promise<void> {
    const secondEditor = this.page.getByRole('textbox', { name: /Editor content/i }).nth(1);
    await secondEditor.focus();
    await this.pressRunParagraph();
  }
}
