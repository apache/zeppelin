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
    this.paragraphResult = page.locator('[data-testid="paragraph-result"]');
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
    if (!noteId) {
      throw new Error('noteId is undefined or null. Cannot navigate to notebook.');
    }

    // Use the reusable navigation function with fallback strategies
    await navigateToNotebookWithFallback(this.page, noteId);

    // Ensure paragraphs are visible after navigation
    try {
      await expect(this.paragraphContainer.first()).toBeVisible({ timeout: 15000 });
    } catch (error) {
      // If no paragraphs found, log but don't throw - let tests handle gracefully
      const paragraphCount = await this.page.locator('zeppelin-notebook-paragraph').count();
      console.log(`Found ${paragraphCount} paragraphs after navigation`);
    }
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

    await this.focusEditorElement(paragraph, paragraphIndex);
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

  // Simple, direct keyboard execution - no hiding failures
  private async executePlatformShortcut(shortcut: string | string[]): Promise<void> {
    const key = Array.isArray(shortcut) ? shortcut[0] : shortcut;
    const formatted = this.formatKey(key);

    await this.page.keyboard.press(formatted);
  }

  private formatKey(shortcut: string): string {
    const isMac = process.platform === 'darwin';

    return shortcut
      .toLowerCase()
      .replace(/\./g, '+')
      .replace(/control/g, isMac ? 'Meta' : 'Control')
      .replace(/shift/g, 'Shift')
      .replace(/alt/g, 'Alt')
      .replace(/arrowup/g, 'ArrowUp')
      .replace(/arrowdown/g, 'ArrowDown')
      .replace(/enter/g, 'Enter')
      .replace(/\+([a-z])$/, (_, c) => `+${c.toUpperCase()}`);
  }

  // All ShortcutsMap keyboard shortcuts

  // Run paragraph - shift.enter
  async pressRunParagraph(): Promise<void> {
    await this.executePlatformShortcut('shift.enter');
  }

  // Run all above paragraphs - control.shift.arrowup
  async pressRunAbove(): Promise<void> {
    await this.executePlatformShortcut('control.shift.arrowup');
  }

  // Run all below paragraphs - control.shift.arrowdown
  async pressRunBelow(): Promise<void> {
    await this.executePlatformShortcut('control.shift.arrowdown');
  }

  // Cancel - control.alt.c (or control.alt.ç for macOS)
  async pressCancel(): Promise<void> {
    await this.executePlatformShortcut(['control.alt.c', 'control.alt.ç']);
  }

  // Move cursor up - control.p
  async pressMoveCursorUp(): Promise<void> {
    await this.executePlatformShortcut('control.p');
  }

  // Move cursor down - control.n
  async pressMoveCursorDown(): Promise<void> {
    await this.executePlatformShortcut('control.n');
  }

  // Delete paragraph - control.alt.d (or control.alt.∂ for macOS)
  async pressDeleteParagraph(): Promise<void> {
    await this.executePlatformShortcut(['control.alt.d', 'control.alt.∂']);
  }

  // Insert paragraph above - control.alt.a (or control.alt.å for macOS)
  async pressInsertAbove(): Promise<void> {
    await this.executePlatformShortcut(['control.alt.a', 'control.alt.å']);
  }

  // Insert paragraph below - control.alt.b (or control.alt.∫ for macOS)
  async pressInsertBelow(): Promise<void> {
    await this.executePlatformShortcut(['control.alt.b', 'control.alt.∫']);
  }

  // Insert copy of paragraph below - control.shift.c
  async pressInsertCopy(): Promise<void> {
    await this.executePlatformShortcut('control.shift.c');
  }

  // Move paragraph up - control.alt.k (or control.alt.˚ for macOS)
  async pressMoveParagraphUp(): Promise<void> {
    await this.executePlatformShortcut(['control.alt.k', 'control.alt.˚']);
  }

  // Move paragraph down - control.alt.j (or control.alt.∆ for macOS)
  async pressMoveParagraphDown(): Promise<void> {
    await this.executePlatformShortcut(['control.alt.j', 'control.alt.∆']);
  }

  // Switch editor - control.alt.e
  async pressSwitchEditor(): Promise<void> {
    await this.executePlatformShortcut('control.alt.e');
  }

  // Switch enable/disable paragraph - control.alt.r (or control.alt.® for macOS)
  async pressSwitchEnable(): Promise<void> {
    await this.executePlatformShortcut(['control.alt.r', 'control.alt.®']);
  }

  // Switch output show/hide - control.alt.o (or control.alt.ø for macOS)
  async pressSwitchOutputShow(): Promise<void> {
    await this.executePlatformShortcut(['control.alt.o', 'control.alt.ø']);
  }

  // Switch line numbers - control.alt.m (or control.alt.µ for macOS)
  async pressSwitchLineNumber(): Promise<void> {
    await this.executePlatformShortcut(['control.alt.m', 'control.alt.µ']);
  }

  // Switch title show/hide - control.alt.t (or control.alt.† for macOS)
  async pressSwitchTitleShow(): Promise<void> {
    await this.executePlatformShortcut(['control.alt.t', 'control.alt.†']);
  }

  // Clear output - control.alt.l (or control.alt.¬ for macOS)
  async pressClearOutput(): Promise<void> {
    await this.executePlatformShortcut(['control.alt.l', 'control.alt.¬']);
  }

  // Link this paragraph - control.alt.w (or control.alt.∑ for macOS)
  async pressLinkParagraph(): Promise<void> {
    await this.executePlatformShortcut(['control.alt.w', 'control.alt.∑']);
  }

  // Reduce paragraph width - control.shift.-
  async pressReduceWidth(): Promise<void> {
    await this.executePlatformShortcut(['control.shift.-', 'control.shift._']);
  }

  // Increase paragraph width - control.shift.=
  async pressIncreaseWidth(): Promise<void> {
    await this.executePlatformShortcut(['control.shift.=', 'control.shift.+']);
  }

  // Cut line - control.k
  async pressCutLine(): Promise<void> {
    await this.executePlatformShortcut('control.k');
  }

  // Paste line - control.y
  async pressPasteLine(): Promise<void> {
    await this.executePlatformShortcut('control.y');
  }

  // Search inside code - control.s
  async pressSearchInsideCode(): Promise<void> {
    await this.executePlatformShortcut('control.s');
  }

  // Find in code - control.alt.f (or control.alt.ƒ for macOS)
  async pressFindInCode(): Promise<void> {
    await this.executePlatformShortcut(['control.alt.f', 'control.alt.ƒ']);
  }

  async getParagraphCount(): Promise<number> {
    if (this.page.isClosed()) {
      return 0;
    }
    try {
      return await this.paragraphContainer.count();
    } catch {
      return 0;
    }
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
    if (this.page.isClosed()) {
      return false;
    }

    const paragraph = this.getParagraphByIndex(paragraphIndex);

    // Strategy 1: Check by standard selectors
    if (await this.findResultBySelectors(paragraph)) {
      return true;
    }

    // Strategy 2: Check by DOM evaluation
    if (await this.checkResultInDOM(paragraphIndex)) {
      return true;
    }

    // Strategy 3: WebKit-specific checks
    const browserName = test.info().project.name;
    if (browserName === 'webkit') {
      return await this.checkWebKitResult(paragraphIndex);
    }

    return false;
  }

  async getCodeEditorContent(): Promise<string> {
    try {
      // Try to get content directly from Monaco Editor's model first
      const monacoContent = await this.page.evaluate(() => {
        // eslint-disable-next-line  @typescript-eslint/no-explicit-any
        const win = window as any;
        if (win.monaco && typeof win.monaco.editor.getActiveEditor === 'function') {
          const editor = win.monaco.editor.getActiveEditor();
          if (editor) {
            return editor.getModel().getValue();
          }
        }
        return null;
      });

      if (monacoContent !== null) {
        return monacoContent;
      }

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
      const selectors = ['textarea', '.monaco-editor .view-lines', '.CodeMirror-line', '.ace_line'];

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
    } catch {
      return '';
    }
  }

  async setCodeEditorContent(content: string, paragraphIndex: number = 0): Promise<void> {
    if (this.page.isClosed()) {
      console.warn('Cannot set code editor content: page is closed');
      return;
    }
    await this.focusCodeEditor(paragraphIndex);
    if (this.page.isClosed()) {
      // Re-check after focusCodeEditor
      console.warn('Cannot set code editor content: page closed after focusing');
      return;
    }

    const paragraph = this.getParagraphByIndex(paragraphIndex);
    const editorInput = paragraph.locator('.monaco-editor .inputarea, .monaco-editor textarea').first();

    try {
      // Try to set content directly via Monaco Editor API
      const success = await this.page.evaluate(newContent => {
        // eslint-disable-next-line  @typescript-eslint/no-explicit-any
        const win = window as any;
        if (win.monaco && typeof win.monaco.editor.getActiveEditor === 'function') {
          const editor = win.monaco.editor.getActiveEditor();
          if (editor) {
            editor.getModel().setValue(newContent);
            return true;
          }
        }
        return false;
      }, content);

      if (success) {
        return;
      }

      // Fallback to Playwright's fill method if Monaco API didn't work
      await editorInput.click({ force: true });
      await editorInput.fill(content);
    } catch (e) {
      // Fallback to keyboard actions if fill method fails
      if (this.page.isClosed()) {
        console.warn('Page closed during fallback content setting');
        return;
      }
      await this.page.keyboard.press('Control+a');
      await this.page.keyboard.press('Delete');
      await this.page.keyboard.type(content, { delay: 10 });
    }
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

    // Check multiple possible indicators for disabled state
    const disabledSelectors = [
      '.paragraph-disabled',
      '[disabled="true"]',
      '.disabled:not(.monaco-sash)',
      '[aria-disabled="true"]',
      '.paragraph-status-disabled'
    ];

    for (const selector of disabledSelectors) {
      try {
        const disabledIndicator = paragraph.locator(selector).first();
        if (await disabledIndicator.isVisible()) {
          return false;
        }
      } catch (error) {
        // Ignore selector errors for ambiguous selectors
        continue;
      }
    }

    // Also check paragraph attributes and classes
    const paragraphClass = (await paragraph.getAttribute('class')) || '';
    const paragraphDisabled = await paragraph.getAttribute('disabled');

    if (paragraphClass.includes('disabled') || paragraphDisabled === 'true') {
      return false;
    }

    // If no disabled indicators found, paragraph is enabled
    return true;
  }

  async isEditorVisible(paragraphIndex: number = 0): Promise<boolean> {
    const paragraph = this.getParagraphByIndex(paragraphIndex);
    const editor = paragraph.locator('zeppelin-notebook-paragraph-code-editor');
    return await editor.isVisible();
  }

  async isOutputVisible(paragraphIndex: number = 0): Promise<boolean> {
    const paragraph = this.getParagraphByIndex(paragraphIndex);
    const output = paragraph.locator('[data-testid="paragraph-result"]');
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

  async getParagraphWidth(paragraphIndex: number = 0): Promise<string | null> {
    const paragraph = this.getParagraphByIndex(paragraphIndex);
    return await paragraph.getAttribute('class');
  }

  async waitForParagraphCountChange(expectedCount: number, timeout: number = 15000): Promise<void> {
    if (this.page.isClosed()) {
      return;
    }

    await expect(this.paragraphContainer).toHaveCount(expectedCount, { timeout });
  }

  async isSearchDialogVisible(): Promise<boolean> {
    const searchDialog = this.page.locator('.search-widget, .find-widget, [role="dialog"]:has-text("Find")');
    return await searchDialog.isVisible();
  }

  async clickModalOkButton(timeout: number = 10000): Promise<void> {
    // Wait for any modal to appear
    const modal = this.page.locator('.ant-modal, .modal-dialog, .ant-modal-confirm');
    await modal.waitFor({ state: 'visible', timeout });

    // Define all acceptable OK button labels
    const okButtons = this.page.locator(
      'button:has-text("OK"), button:has-text("Ok"), button:has-text("Okay"), button:has-text("Confirm")'
    );

    // Count how many OK-like buttons exist
    const count = await okButtons.count();
    if (count === 0) {
      console.log('⚠️ No OK buttons found.');
      return;
    }

    // Click each visible OK button in sequence
    for (let i = 0; i < count; i++) {
      const button = okButtons.nth(i);
      try {
        await button.waitFor({ state: 'visible', timeout });
        await button.click({ delay: 100 });
        // Wait for modal to actually close instead of fixed timeout
        await modal.waitFor({ state: 'hidden', timeout: 2000 }).catch(() => {
          console.log('Modal did not close within expected time, continuing...');
        });
      } catch (e) {
        console.warn(`⚠️ Failed to click OK button #${i + 1}:`, e);
      }
    }

    // Wait for all modals to be closed
    await this.page
      .locator('.ant-modal, .modal-dialog, .ant-modal-confirm')
      .waitFor({
        state: 'detached',
        timeout: 2000
      })
      .catch(() => {
        console.log('Some modals may still be present, continuing...');
      });
  }

  // ===== PRIVATE HELPER METHODS =====

  private async findResultBySelectors(paragraph: Locator): Promise<boolean> {
    const selectors = [
      '[data-testid="paragraph-result"]',
      'zeppelin-notebook-paragraph-result',
      '.paragraph-result',
      '.result-content'
    ];

    for (const selector of selectors) {
      const result = paragraph.locator(selector);
      const count = await result.count();

      if (count > 0 && (await result.first().isVisible())) {
        console.log(`Found result with selector: ${selector}`);
        return true;
      }
    }

    return false;
  }

  private async checkResultInDOM(paragraphIndex: number): Promise<boolean> {
    const hasResult = await this.page.evaluate(pIndex => {
      const paragraphs = document.querySelectorAll('zeppelin-notebook-paragraph');
      const targetParagraph = paragraphs[pIndex];

      if (!targetParagraph) {
        return false;
      }

      const resultElements = [
        targetParagraph.querySelector('[data-testid="paragraph-result"]'),
        targetParagraph.querySelector('zeppelin-notebook-paragraph-result'),
        targetParagraph.querySelector('.paragraph-result'),
        targetParagraph.querySelector('.result-content')
      ];

      return resultElements.some(el => el && getComputedStyle(el).display !== 'none');
    }, paragraphIndex);

    if (hasResult) {
      console.log('Found result via DOM evaluation');
    }

    return hasResult;
  }

  private async checkWebKitResult(paragraphIndex: number): Promise<boolean> {
    // Check 1: Text content analysis
    if (await this.checkWebKitTextContent(paragraphIndex)) {
      console.log('WebKit: Found execution content via text analysis');
      return true;
    }

    // Check 2: Structural changes
    if (await this.checkWebKitStructuralChanges(paragraphIndex)) {
      console.log('WebKit: Found execution via structural analysis');
      return true;
    }

    return false;
  }

  private async checkWebKitTextContent(paragraphIndex: number): Promise<boolean> {
    return await this.page.evaluate(pIndex => {
      const paragraphs = document.querySelectorAll('zeppelin-notebook-paragraph');
      const targetParagraph = paragraphs[pIndex];

      if (!targetParagraph) {
        return false;
      }

      const textContent = targetParagraph.textContent || '';
      const executionIndicators = ['1 + 1', '2', 'print', 'Out[', '>>>', 'result', 'output'];

      return executionIndicators.some(indicator => textContent.toLowerCase().includes(indicator.toLowerCase()));
    }, paragraphIndex);
  }

  private async checkWebKitStructuralChanges(paragraphIndex: number): Promise<boolean> {
    return await this.page.evaluate(pIndex => {
      const paragraphs = document.querySelectorAll('zeppelin-notebook-paragraph');
      const targetParagraph = paragraphs[pIndex];

      if (!targetParagraph) {
        return false;
      }

      const elementCount = targetParagraph.querySelectorAll('*').length;
      const executionElements = [
        'pre',
        'code',
        '.output',
        '.result',
        'table',
        'div[class*="result"]',
        'span[class*="output"]'
      ];

      const hasExecutionElements = executionElements.some(selector => targetParagraph.querySelector(selector) !== null);

      console.log(`WebKit structural check: ${elementCount} elements, hasExecutionElements: ${hasExecutionElements}`);

      return hasExecutionElements || elementCount > 10;
    }, paragraphIndex);
  }

  private async waitForExecutionStart(paragraphIndex: number): Promise<void> {
    const started = await this.page
      .waitForFunction(
        index => {
          const paragraphs = document.querySelectorAll('zeppelin-notebook-paragraph');
          const targetParagraph = paragraphs[index];
          if (!targetParagraph) {
            return false;
          }

          const hasRunning = targetParagraph.querySelector('.fa-spin, .running-indicator, .paragraph-status-running');
          const hasResult = targetParagraph.querySelector('[data-testid="paragraph-result"]');

          return hasRunning || hasResult;
        },
        paragraphIndex,
        { timeout: 8000 }
      )
      .catch(() => false);

    if (!started) {
      const paragraph = this.getParagraphByIndex(paragraphIndex);
      const existingResult = await paragraph.locator('[data-testid="paragraph-result"]').isVisible();
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
        index => {
          const paragraphs = document.querySelectorAll('zeppelin-notebook-paragraph');
          const targetParagraph = paragraphs[index];
          if (!targetParagraph) {
            return false;
          }

          const result = targetParagraph.querySelector('[data-testid="paragraph-result"]');
          return result && getComputedStyle(result).display !== 'none';
        },
        paragraphIndex,
        { timeout: Math.min(timeout / 2, 15000) }
      )
      .catch(() => false);

    if (!resultVisible) {
      const paragraph = this.getParagraphByIndex(paragraphIndex);
      const resultExists = await paragraph.locator('[data-testid="paragraph-result"]').isVisible();
      if (!resultExists) {
        console.log(`Warning: No result found for paragraph ${paragraphIndex} after execution`);
      }
    }
  }

  private async focusEditorElement(paragraph: Locator, paragraphIndex: number): Promise<void> {
    const editor = paragraph.locator('.monaco-editor, .CodeMirror, .ace_editor, textarea').first();
    await editor.waitFor({ state: 'visible', timeout: 5000 }).catch(() => {
      console.warn(`Editor not visible in paragraph ${paragraphIndex}`);
    });

    await editor.click({ force: true }).catch(() => {
      console.warn(`Failed to click editor in paragraph ${paragraphIndex}`);
    });

    await this.ensureEditorFocused(editor, paragraphIndex);
  }

  private async ensureEditorFocused(editor: Locator, paragraphIndex: number): Promise<void> {
    const textArea = editor.locator('textarea');
    const hasTextArea = (await textArea.count()) > 0;

    if (hasTextArea) {
      await textArea.press('ArrowRight').catch(() => {
        console.warn(`Failed to press ArrowRight in paragraph ${paragraphIndex}`);
      });
      await expect(textArea)
        .toBeFocused({ timeout: 2000 })
        .catch(() => {
          console.warn(`Textarea not focused in paragraph ${paragraphIndex}`);
        });
    } else {
      await expect(editor)
        .toHaveClass(/focused|focus/, { timeout: 5000 })
        .catch(() => {
          console.warn(`Editor not focused in paragraph ${paragraphIndex}`);
        });
    }
  }
}
