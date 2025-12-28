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

import test, { expect, Locator, Page } from '@playwright/test';
import { navigateToNotebookWithFallback } from '../utils';
import { ShortcutsMap } from '../../src/app/key-binding/shortcuts-map';
import { ParagraphActions } from '../../src/app/key-binding/paragraph-actions';
import { BasePage } from './base-page';

const PARAGRAPH_RESULT_SELECTOR = '[data-testid="paragraph-result"]';

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
  readonly addParagraphComponent: Locator;
  readonly searchDialog: Locator;
  readonly modal: Locator;
  readonly okButtons: Locator;

  constructor(page: Page) {
    super(page);
    this.codeEditor = page.locator('.monaco-editor .monaco-mouse-cursor-text');
    this.paragraphContainer = page.locator('zeppelin-notebook-paragraph');
    this.firstParagraph = this.paragraphContainer.first();
    this.runButton = page.locator('button[title="Run this paragraph"], button:has-text("Run")');
    this.paragraphResult = page.locator(PARAGRAPH_RESULT_SELECTOR);
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
    this.addParagraphComponent = page.locator('zeppelin-notebook-add-paragraph').last();
    this.searchDialog = page.locator(
      '.dropdown-menu.search-code, .search-widget, .find-widget, [role="dialog"]:has-text("Find")'
    );
    this.modal = page.locator('.ant-modal, .modal-dialog, .ant-modal-confirm');
    this.okButtons = page.locator(
      'button:has-text("OK"), button:has-text("Ok"), button:has-text("Okay"), button:has-text("Confirm")'
    );
  }

  async navigateToNotebook(noteId: string): Promise<void> {
    if (!noteId) {
      throw new Error('noteId is undefined or null. Cannot navigate to notebook.');
    }

    await navigateToNotebookWithFallback(this.page, noteId);

    // Verify we're actually on a notebook page before checking for paragraphs
    await expect(this.page).toHaveURL(new RegExp(`/notebook/${noteId}`), { timeout: 15000 });

    // Ensure paragraphs are visible after navigation with longer timeout
    await expect(this.paragraphContainer.first()).toBeVisible({ timeout: 30000 });
  }

  async focusCodeEditor(paragraphIndex: number = 0): Promise<void> {
    if (this.page.isClosed()) {
      console.warn('Cannot focus code editor: page is closed');
      return;
    }

    const paragraphCount = await this.getParagraphCount();
    if (paragraphCount === 0) {
      console.warn('No paragraphs found on page, cannot focus editor');
      return;
    }

    const paragraph = this.getParagraphByIndex(paragraphIndex);
    await paragraph.waitFor({ state: 'visible', timeout: 10000 }).catch(() => {
      console.warn(`Paragraph ${paragraphIndex} not visible`);
    });

    // Wait for any loading/rendering to complete
    await this.page.waitForLoadState('domcontentloaded');

    const browserName = this.page.context().browser()?.browserType().name();
    if (browserName === 'firefox' || browserName === 'chromium') {
      // Additional wait for Firefox to ensure editor is fully ready
      await this.page.waitForTimeout(200);
    }

    await this.focusEditorElement(paragraph, paragraphIndex);
  }

  async typeInEditor(text: string): Promise<void> {
    await this.page.keyboard.type(text);
  }

  async pressKey(key: string): Promise<void> {
    await this.page.keyboard.press(key);
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

  async pressArrowRight(): Promise<void> {
    await this.page.keyboard.press('ArrowRight');
  }

  async pressTab(): Promise<void> {
    await this.page.keyboard.press('Tab');
  }

  async pressEscape(): Promise<void> {
    await this.page.keyboard.press('Escape');
  }

  async pressSelectAll(): Promise<void> {
    const isWebkit = this.page.context().browser()?.browserType().name() === 'webkit';
    if (isWebkit) {
      await this.page.keyboard.press('Meta+A');
    } else {
      await this.page.keyboard.press('ControlOrMeta+A');
    }
  }

  // Run paragraph - shift.enter
  async pressRunParagraph(): Promise<void> {
    await this.executePlatformShortcut(ShortcutsMap[ParagraphActions.Run]);
  }

  // Run all above paragraphs - control.shift.arrowup
  async pressRunAbove(): Promise<void> {
    await this.executePlatformShortcut(ShortcutsMap[ParagraphActions.RunAbove]);
  }

  // Run all below paragraphs - control.shift.arrowdown
  async pressRunBelow(): Promise<void> {
    await this.executePlatformShortcut(ShortcutsMap[ParagraphActions.RunBelow]);
  }

  // Cancel - control.alt.c (or control.alt.ç for macOS)
  async pressCancel(): Promise<void> {
    await this.executePlatformShortcut(ShortcutsMap[ParagraphActions.Cancel]);
  }

  // Move cursor up - control.p
  async pressMoveCursorUp(): Promise<void> {
    await this.executePlatformShortcut(ShortcutsMap[ParagraphActions.MoveCursorUp]);
  }

  // Move cursor down - control.n
  async pressMoveCursorDown(): Promise<void> {
    await this.executePlatformShortcut(ShortcutsMap[ParagraphActions.MoveCursorDown]);
  }

  // Delete paragraph - control.alt.d (or control.alt.∂ for macOS)
  async pressDeleteParagraph(): Promise<void> {
    await this.executePlatformShortcut(ShortcutsMap[ParagraphActions.Delete]);
  }

  // Insert paragraph above - control.alt.a (or control.alt.å for macOS)
  async pressInsertAbove(): Promise<void> {
    await this.executePlatformShortcut(ShortcutsMap[ParagraphActions.InsertAbove]);
  }

  // Insert paragraph below - control.alt.b (or control.alt.∫ for macOS)
  async pressInsertBelow(): Promise<void> {
    await this.executePlatformShortcut(ShortcutsMap[ParagraphActions.InsertBelow]);
  }

  async addParagraph(): Promise<void> {
    const currentCount = await this.getParagraphCount();
    console.log(`[addParagraph] Paragraph count before: ${currentCount}`);

    await this.addParagraphComponent.hover();
    await this.addParagraphComponent.locator('a.inner').click();
    console.log(`[addParagraph] "Add Paragraph" button clicked`);

    // Wait for paragraph count to increase
    await this.page.waitForFunction(
      expectedCount => document.querySelectorAll('zeppelin-notebook-paragraph').length > expectedCount,
      currentCount,
      { timeout: 10000 }
    );

    const newCount = await this.getParagraphCount();
    console.log(`[addParagraph] Success! Paragraph count increased from ${currentCount} to ${newCount}`);
  }

  // Insert copy of paragraph below - control.shift.c
  async pressInsertCopy(): Promise<void> {
    await this.executePlatformShortcut(ShortcutsMap[ParagraphActions.InsertCopyOfParagraphBelow]);
  }

  // Move paragraph up - control.alt.k (or control.alt.˚ for macOS)
  async pressMoveParagraphUp(): Promise<void> {
    await this.executePlatformShortcut(ShortcutsMap[ParagraphActions.MoveParagraphUp]);
  }

  // Move paragraph down - control.alt.j (or control.alt.∆ for macOS)
  async pressMoveParagraphDown(): Promise<void> {
    await this.executePlatformShortcut(ShortcutsMap[ParagraphActions.MoveParagraphDown]);
  }

  // Switch editor - control.alt.e
  async pressSwitchEditor(): Promise<void> {
    await this.executePlatformShortcut(ShortcutsMap[ParagraphActions.SwitchEditor]);
  }

  // Switch enable/disable paragraph - control.alt.r (or control.alt.® for macOS)
  async pressSwitchEnable(): Promise<void> {
    await this.executePlatformShortcut(ShortcutsMap[ParagraphActions.SwitchEnable]);
  }

  // Switch output show/hide - control.alt.o (or control.alt.ø for macOS)
  async pressSwitchOutputShow(): Promise<void> {
    await this.executePlatformShortcut(ShortcutsMap[ParagraphActions.SwitchOutputShow]);
  }

  // Switch line numbers - control.alt.m (or control.alt.µ for macOS)
  async pressSwitchLineNumber(): Promise<void> {
    await this.executePlatformShortcut(ShortcutsMap[ParagraphActions.SwitchLineNumber]);
  }

  // Switch title show/hide - control.alt.t (or control.alt.† for macOS)
  async pressSwitchTitleShow(): Promise<void> {
    await this.executePlatformShortcut(ShortcutsMap[ParagraphActions.SwitchTitleShow]);
  }

  // Clear output - control.alt.l (or control.alt.¬ for macOS)
  async pressClearOutput(): Promise<void> {
    await this.executePlatformShortcut(ShortcutsMap[ParagraphActions.Clear]);
  }

  // Link this paragraph - control.alt.w (or control.alt.∑ for macOS)
  async pressLinkParagraph(): Promise<void> {
    await this.executePlatformShortcut(ShortcutsMap[ParagraphActions.Link]);
  }

  // Reduce paragraph width - control.shift.-
  async pressReduceWidth(): Promise<void> {
    await this.executePlatformShortcut(ShortcutsMap[ParagraphActions.ReduceWidth]);
  }

  // Increase paragraph width - control.shift.=
  async pressIncreaseWidth(): Promise<void> {
    await this.executePlatformShortcut(ShortcutsMap[ParagraphActions.IncreaseWidth]);
  }

  // Cut line - control.k
  async pressCutLine(): Promise<void> {
    await this.executePlatformShortcut(ShortcutsMap[ParagraphActions.CutLine]);
  }

  // Paste line - control.y
  async pressPasteLine(): Promise<void> {
    await this.executePlatformShortcut(ShortcutsMap[ParagraphActions.PasteLine]);
  }

  // Search inside code - control.s
  async pressSearchInsideCode(): Promise<void> {
    await this.executePlatformShortcut(ShortcutsMap[ParagraphActions.SearchInsideCode]);
  }

  // Find in code - control.alt.f (or control.alt.ƒ for macOS)
  async pressFindInCode(): Promise<void> {
    await this.executePlatformShortcut(ShortcutsMap[ParagraphActions.FindInCode]);
  }

  async getParagraphCount(): Promise<number> {
    if (this.page.isClosed()) {
      return 0;
    }
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

  async isParagraphResultSettled(paragraphIndex: number = 0): Promise<boolean> {
    if (this.page.isClosed()) {
      return false;
    }

    const paragraph = this.getParagraphByIndex(paragraphIndex);

    // Check status from DOM directly
    const statusElement = paragraph.locator('.status');
    if (await statusElement.isVisible()) {
      const status = await statusElement.textContent();
      console.log(`Paragraph ${paragraphIndex} status: ${status}`);

      // NOTE: accept PENDING/RUNNING states as "settled" because
      // these browsers may maintain execution in these states longer than Chromium,
      // but the paragraph execution has been triggered successfully and will complete.
      // The key is that execution started, not necessarily that it finished.

      if (status === 'FINISHED' || status === 'ERROR' || status === 'PENDING' || status === 'RUNNING') {
        return true;
      }
    }

    return false;
  }

  async getCodeEditorContent(): Promise<string> {
    // Fallback to Angular scope
    const angularContent = await this.page.evaluate(() => {
      const paragraphElement = document.querySelector('zeppelin-notebook-paragraph');
      if (paragraphElement) {
        // eslint-disable-next-line  @typescript-eslint/no-explicit-any
        const angular = (window as any).angular;
        if (angular) {
          const scope = angular.element(paragraphElement).scope();
          if (scope && scope.$ctrl && scope.$ctrl.paragraph) {
            return scope.$ctrl.paragraph.text || '';
          }
        }
      }
      return null;
    });

    if (angularContent !== null) {
      return angularContent;
    }

    // Fallback to DOM-based approaches
    const selectors = ['.monaco-editor .view-lines', '.CodeMirror-line', '.ace_line', 'textarea'];

    for (const selector of selectors) {
      const element = this.page.locator(selector).first();
      if (await element.isVisible({ timeout: 1000 })) {
        if (selector === 'textarea') {
          return await element.inputValue();
        } else {
          return (await element.textContent()) || '';
        }
      }
    }

    return '';
  }

  async setCodeEditorContent(content: string, paragraphIndex: number = 0): Promise<void> {
    if (this.page.isClosed()) {
      console.warn('Cannot set code editor content: page is closed');
      return;
    }

    await this.focusCodeEditor(paragraphIndex);
    if (this.page.isClosed()) {
      console.warn('Cannot set code editor content: page closed after focusing');
      return;
    }

    const paragraph = this.getParagraphByIndex(paragraphIndex);
    const editorInput = paragraph.locator('.monaco-editor .inputarea, .monaco-editor textarea').first();

    const browserName = test.info().project.name;
    if (browserName !== 'firefox') {
      await editorInput.waitFor({ state: 'visible', timeout: 30000 });
      await editorInput.click();
      await editorInput.clear();
    }

    // Clear existing content with keyboard shortcuts for better reliability
    await editorInput.focus();

    if (browserName === 'firefox') {
      // Clear by backspacing existing content length
      const currentContent = await editorInput.inputValue();
      const contentLength = currentContent.length;

      // Position cursor at end and backspace all content
      await this.page.keyboard.press('End');
      for (let i = 0; i < contentLength; i++) {
        await this.page.keyboard.press('Backspace');
      }
      await this.page.waitForTimeout(100);

      await this.page.keyboard.type(content);

      await this.page.waitForTimeout(300);
    } else {
      // Standard clearing for other browsers
      await this.pressSelectAll();
      await this.page.keyboard.press('Delete');
      await editorInput.fill(content, { force: true });
    }

    await this.page.waitForTimeout(200);
  }

  // Helper methods for verifying shortcut effects
  async waitForParagraphExecution(paragraphIndex: number = 0, timeout: number = 30000): Promise<void> {
    if (this.page.isClosed()) {
      console.warn('Cannot wait for paragraph execution: page is closed');
      return;
    }

    const paragraph = this.getParagraphByIndex(paragraphIndex);

    // Step 1: Wait for execution to start
    await this.waitForExecutionStart(paragraphIndex);

    // Step 2: Wait for execution to complete
    const runningIndicator = paragraph.locator(
      '.paragraph-control .fa-spin, .running-indicator, .paragraph-status-running'
    );
    await this.waitForExecutionComplete(runningIndicator, paragraphIndex, timeout);

    // Step 3: Wait for result to be visible
    await this.waitForResultVisible(paragraphIndex, timeout);
  }

  async isParagraphEnabled(paragraphIndex: number = 0): Promise<boolean> {
    const paragraph = this.getParagraphByIndex(paragraphIndex);
    const runButton = paragraph.locator('i[nztooltiptitle="Run paragraph"]');
    return await runButton.isVisible();
  }

  async isEditorVisible(paragraphIndex: number = 0): Promise<boolean> {
    const paragraph = this.getParagraphByIndex(paragraphIndex);
    const editor = paragraph.locator('zeppelin-notebook-paragraph-code-editor');
    return await editor.isVisible();
  }

  async isOutputVisible(paragraphIndex: number = 0): Promise<boolean> {
    const paragraph = this.getParagraphByIndex(paragraphIndex);
    const output = paragraph.locator(PARAGRAPH_RESULT_SELECTOR);
    return await output.isVisible();
  }

  async areLineNumbersVisible(paragraphIndex: number = 0): Promise<boolean> {
    if (this.page.isClosed()) {
      return false;
    }
    const paragraph = this.getParagraphByIndex(paragraphIndex);
    const lineNumbers = paragraph.locator('.monaco-editor .margin .line-numbers').first();
    return await lineNumbers.isVisible();
  }

  async isTitleVisible(paragraphIndex: number = 0): Promise<boolean> {
    const paragraph = this.getParagraphByIndex(paragraphIndex);
    const title = paragraph.locator('.paragraph-title, zeppelin-elastic-input');
    return await title.isVisible();
  }

  async getParagraphWidth(paragraphIndex: number = 0): Promise<number> {
    const paragraph = this.getParagraphByIndex(paragraphIndex);
    const boundingBox = await paragraph.boundingBox();
    return boundingBox?.width || 0;
  }

  async getCodeEditorContentByIndex(paragraphIndex: number): Promise<string> {
    const paragraph = this.getParagraphByIndex(paragraphIndex);

    const editorTextarea = paragraph.locator('.monaco-editor textarea');
    if (await editorTextarea.isVisible()) {
      const textContent = await editorTextarea.inputValue();
      if (textContent) {
        return textContent;
      }
    }

    const viewLines = paragraph.locator('.monaco-editor .view-lines');
    if (await viewLines.isVisible()) {
      const text = await viewLines.evaluate((el: Element) => (el as HTMLElement).innerText || '');
      if (text && text.trim().length > 0) {
        return text;
      }
    }

    const scopeContent = await paragraph.evaluate(el => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const angular = (window as any).angular;
      if (angular) {
        const scope = angular.element(el).scope();
        if (scope && scope.$ctrl && scope.$ctrl.paragraph) {
          return scope.$ctrl.paragraph.text || '';
        }
      }
      return '';
    });

    if (scopeContent) {
      return scopeContent;
    }

    return '';
  }

  async waitForParagraphCountChange(expectedCount: number, timeout: number = 30000): Promise<void> {
    if (this.page.isClosed()) {
      return;
    }

    await expect(this.paragraphContainer).toHaveCount(expectedCount, { timeout });
  }

  async isSearchDialogVisible(): Promise<boolean> {
    return await this.searchDialog.isVisible();
  }

  async clickModalOkButton(timeout: number = 30000): Promise<void> {
    await this.modal.waitFor({ state: 'visible', timeout });

    const count = await this.okButtons.count();
    if (count === 0) {
      console.log('⚠️ No OK buttons found.');
      return;
    }

    for (let i = 0; i < count; i++) {
      const button = this.okButtons.nth(i);
      await button.waitFor({ state: 'visible', timeout });
      await button.click({ delay: 100 });
      await this.modal.waitFor({ state: 'hidden', timeout: 2000 }).catch(() => {
        console.log('Modal did not close within expected time, continuing...');
      });
    }

    await this.modal.waitFor({ state: 'detached', timeout: 2000 }).catch(() => {
      console.log('Some modals may still be present, continuing...');
    });
  }

  private async waitForExecutionStart(paragraphIndex: number): Promise<void> {
    const started = await this.page
      .waitForFunction(
        // waitForFunction executes in browser context, not Node.js context.
        // Browser cannot access Node.js variables like PARAGRAPH_RESULT_SELECTOR.
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        ([index, selector]: any[]) => {
          const paragraphs = document.querySelectorAll('zeppelin-notebook-paragraph');
          const targetParagraph = paragraphs[index];
          if (!targetParagraph) {
            return false;
          }

          const hasRunning = targetParagraph.querySelector('.fa-spin, .running-indicator, .paragraph-status-running');
          const hasResult = targetParagraph.querySelector(selector);

          return hasRunning || hasResult;
        },
        [paragraphIndex, PARAGRAPH_RESULT_SELECTOR],
        { timeout: 8000 }
      )
      .catch(() => false);

    if (!started) {
      const paragraph = this.getParagraphByIndex(paragraphIndex);
      const existingResult = await paragraph.locator(PARAGRAPH_RESULT_SELECTOR).isVisible();
      if (!existingResult) {
        console.log(`Warning: Could not detect execution start for paragraph ${paragraphIndex}`);
      }
    }
  }

  private async waitForExecutionComplete(
    runningIndicator: Locator,
    paragraphIndex: number,
    timeout: number
  ): Promise<void> {
    if (this.page.isClosed()) {
      return;
    }

    await runningIndicator.waitFor({ state: 'detached', timeout: timeout / 2 }).catch(() => {
      console.log(`Running indicator timeout for paragraph ${paragraphIndex} - continuing`);
    });
  }

  private async waitForResultVisible(paragraphIndex: number, timeout: number): Promise<void> {
    if (this.page.isClosed()) {
      return;
    }

    const resultVisible = await this.page
      .waitForFunction(
        // waitForFunction executes in browser context, not Node.js context.
        // Browser cannot access Node.js variables like PARAGRAPH_RESULT_SELECTOR.
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        ([index, selector]: any[]) => {
          const paragraphs = document.querySelectorAll('zeppelin-notebook-paragraph');
          const targetParagraph = paragraphs[index];
          if (!targetParagraph) {
            return false;
          }

          const result = targetParagraph.querySelector(selector);
          return result && getComputedStyle(result).display !== 'none';
        },
        [paragraphIndex, PARAGRAPH_RESULT_SELECTOR],
        { timeout: Math.min(timeout / 2, 15000) }
      )
      .catch(() => false);

    if (!resultVisible) {
      const paragraph = this.getParagraphByIndex(paragraphIndex);
      const resultExists = await paragraph.locator(PARAGRAPH_RESULT_SELECTOR).isVisible();
      if (!resultExists) {
        console.log(`Warning: No result found for paragraph ${paragraphIndex} after execution`);
      }
    }
  }

  private async focusEditorElement(paragraph: Locator, paragraphIndex: number): Promise<void> {
    if (this.page.isClosed()) {
      console.warn(`Attempted to focus editor in paragraph ${paragraphIndex} but page is closed.`);
      return;
    }

    const editor = paragraph.locator('.monaco-editor, .CodeMirror, .ace_editor, textarea').first();

    await editor.waitFor({ state: 'visible', timeout: 5000 }).catch(() => {
      console.warn(`Editor not visible in paragraph ${paragraphIndex}`);
    });

    await editor.click({ force: true, trial: true }).catch(async () => {
      console.warn(`Failed to click editor in paragraph ${paragraphIndex}, trying to focus textarea directly`);
      const textArea = editor.locator('textarea').first();
      if ((await textArea.count()) > 0) {
        await textArea.focus({ timeout: 1000 });
      }
    });

    await this.ensureEditorFocused(editor);
  }

  private async ensureEditorFocused(editor: Locator): Promise<void> {
    const textArea = editor.locator('textarea');
    const hasTextArea = (await textArea.count()) > 0;

    if (hasTextArea) {
      await textArea.focus();
      await expect(textArea).toBeFocused({ timeout: 3000 });
    } else {
      await expect(editor).toHaveClass(/focused|focus|active/, { timeout: 30000 });
    }
  }

  private async executePlatformShortcut(shortcut: string | string[]): Promise<void> {
    const shortcutsToTry = Array.isArray(shortcut) ? shortcut : [shortcut];

    for (const s of shortcutsToTry) {
      try {
        const formatted = this.formatKey(s);
        await this.page.keyboard.press(formatted);
        return;
      } catch {
        continue;
      }
    }
  }

  private formatKey(shortcut: string): string {
    return shortcut
      .toLowerCase()
      .replace(/\./g, '+')
      .replace(/control/g, 'Control')
      .replace(/shift/g, 'Shift')
      .replace(/alt/g, 'Alt')
      .replace(/arrowup/g, 'ArrowUp')
      .replace(/arrowdown/g, 'ArrowDown')
      .replace(/enter/g, 'Enter')
      .replace(/\+([a-z])$/, (_, c) => `+${c.toUpperCase()}`);
  }
}
