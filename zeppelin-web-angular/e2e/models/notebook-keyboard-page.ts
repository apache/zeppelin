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

export class NotebookKeyboardPage extends BasePage {
  readonly codeEditor: Locator;
  readonly paragraphContainer: Locator;
  readonly firstParagraph: Locator;
  readonly runButton: Locator;
  readonly paragraphResult: Locator;
  readonly newParagraphButton: Locator;
  readonly interpreterSelector: Locator;
  readonly interpreterDropdown: Locator;
  readonly autocompletePopup: Locator;
  readonly autocompleteItems: Locator;
  readonly paragraphTitle: Locator;
  readonly editorLines: Locator;
  readonly cursorLine: Locator;
  readonly settingsButton: Locator;
  readonly clearOutputOption: Locator;
  readonly deleteButton: Locator;

  constructor(page: Page) {
    super(page);
    this.codeEditor = page.locator('.monaco-editor .monaco-mouse-cursor-text');
    this.paragraphContainer = page.locator('zeppelin-notebook-paragraph');
    this.firstParagraph = this.paragraphContainer.first();
    this.runButton = page.locator('button[title="Run this paragraph"], button:has-text("Run")');
    this.paragraphResult = page.locator('zeppelin-notebook-paragraph-result');
    this.newParagraphButton = page.locator('button:has-text("Add Paragraph"), .new-paragraph-button');
    this.interpreterSelector = page.locator('.interpreter-selector');
    this.interpreterDropdown = page.locator('nz-select[ng-reflect-nz-placeholder="Interpreter"]');
    this.autocompletePopup = page.locator('.monaco-editor .suggest-widget');
    this.autocompleteItems = page.locator('.monaco-editor .suggest-widget .monaco-list-row');
    this.paragraphTitle = page.locator('.paragraph-title');
    this.editorLines = page.locator('.monaco-editor .view-lines');
    this.cursorLine = page.locator('.monaco-editor .current-line');
    this.settingsButton = page.locator('a[nz-dropdown]');
    this.clearOutputOption = page.locator('li.list-item:has-text("Clear output")');
    this.deleteButton = page.locator('button:has-text("Delete"), .delete-paragraph-button');
  }

  async navigateToNotebook(noteId: string): Promise<void> {
    await this.page.goto(`/#/notebook/${noteId}`);
    await this.waitForPageLoad();
  }

  async focusCodeEditor(): Promise<void> {
    // Use the code editor component locator directly
    const codeEditorComponent = this.page.locator('zeppelin-notebook-paragraph-code-editor').first();
    await expect(codeEditorComponent).toBeVisible({ timeout: 10000 });

    // Click on the editor area to focus
    const editorTextArea = codeEditorComponent.locator('.monaco-editor').first();
    await editorTextArea.click();
  }

  async typeInEditor(text: string): Promise<void> {
    await this.page.keyboard.type(text);
  }

  async pressKey(key: string, modifiers?: string[]): Promise<void> {
    if (modifiers && modifiers.length > 0) {
      await this.page.keyboard.press(`${modifiers.join('+')}+${key}`);
    } else {
      await this.page.keyboard.press(key);
    }
  }

  async pressShiftEnter(): Promise<void> {
    await this.page.keyboard.press('Shift+Enter');
  }

  async pressControlEnter(): Promise<void> {
    await this.page.keyboard.press('Control+Enter');
  }

  async pressControlSpace(): Promise<void> {
    await this.page.keyboard.press('Control+Space');
  }

  async pressArrowDown(): Promise<void> {
    await this.page.keyboard.press('ArrowDown');
  }

  async pressArrowUp(): Promise<void> {
    await this.page.keyboard.press('ArrowUp');
  }

  async pressTab(): Promise<void> {
    await this.page.keyboard.press('Tab');
  }

  async pressEscape(): Promise<void> {
    await this.page.keyboard.press('Escape');
  }

  async getParagraphCount(): Promise<number> {
    return await this.paragraphContainer.count();
  }

  getParagraphByIndex(index: number): Locator {
    return this.paragraphContainer.nth(index);
  }

  async isAutocompleteVisible(): Promise<boolean> {
    return await this.autocompletePopup.isVisible();
  }

  async getAutocompleteItemCount(): Promise<number> {
    if (await this.isAutocompleteVisible()) {
      return await this.autocompleteItems.count();
    }
    return 0;
  }

  async isParagraphRunning(paragraphIndex: number = 0): Promise<boolean> {
    const paragraph = this.getParagraphByIndex(paragraphIndex);
    const runningIndicator = paragraph.locator('.paragraph-control .fa-spin, .running-indicator');
    return await runningIndicator.isVisible();
  }

  async hasParagraphResult(paragraphIndex: number = 0): Promise<boolean> {
    const paragraph = this.getParagraphByIndex(paragraphIndex);
    const result = paragraph.locator('zeppelin-notebook-paragraph-result');
    return await result.isVisible();
  }

  async clearParagraphOutput(paragraphIndex: number = 0): Promise<void> {
    const paragraph = this.getParagraphByIndex(paragraphIndex);
    const settingsButton = paragraph.locator('a[nz-dropdown]');
    await settingsButton.click();
    await this.clearOutputOption.click();
  }

  async getCurrentParagraphIndex(): Promise<number> {
    const activeParagraph = this.page.locator(
      'zeppelin-notebook-paragraph.paragraph-selected, zeppelin-notebook-paragraph.focus'
    );
    if ((await activeParagraph.count()) > 0) {
      const allParagraphs = await this.paragraphContainer.all();
      for (let i = 0; i < allParagraphs.length; i++) {
        if (await allParagraphs[i].locator('.paragraph-selected, .focus').isVisible()) {
          return i;
        }
      }
    }
    return -1;
  }

  async getCodeEditorContent(): Promise<string> {
    // Get content using input value or text content
    const codeEditorComponent = this.page.locator('zeppelin-notebook-paragraph-code-editor').first();
    const textArea = codeEditorComponent.locator('textarea, .monaco-editor .view-lines');

    try {
      // Try to get value from textarea if it exists
      const textAreaElement = codeEditorComponent.locator('textarea');
      if ((await textAreaElement.count()) > 0) {
        return await textAreaElement.inputValue();
      }

      // Fallback to text content
      return (await textArea.textContent()) || '';
    } catch {
      return '';
    }
  }

  async setCodeEditorContent(content: string): Promise<void> {
    // Focus the editor first
    await this.focusCodeEditor();

    // Select all existing content and replace
    await this.page.keyboard.press('Control+a');

    // Type the new content
    if (content) {
      await this.page.keyboard.type(content);
    } else {
      await this.page.keyboard.press('Delete');
    }
  }
}
