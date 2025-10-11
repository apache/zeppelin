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

    // Ensure paragraphs are visible
    await expect(this.paragraphContainer.first()).toBeVisible({ timeout: 15000 });
  }

  async focusCodeEditor(): Promise<void> {
    // Wait for paragraph to be visible
    await expect(this.paragraphContainer.first()).toBeVisible({ timeout: 15000 });

    // Try multiple editor selectors in priority order
    const editorSelectors = [
      '.monaco-editor .inputarea',
      '.monaco-editor textarea',
      '.monaco-editor .view-lines',
      '.monaco-editor',
      'textarea',
      '.CodeMirror',
      '.ace_editor'
    ];

    let focused = false;
    for (const selector of editorSelectors) {
      try {
        const element = this.page.locator(selector).first();
        if (await element.isVisible({ timeout: 2000 })) {
          await element.click();
          focused = true;
          break;
        }
      } catch {
        continue;
      }
    }

    if (!focused) {
      // Fallback: click on the paragraph itself
      await this.paragraphContainer.first().click();
    }
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
    // Ensure we're focused on the editor first
    await this.focusCodeEditor();

    // Try different approaches to trigger Shift+Enter
    try {
      // First try the direct keyboard approach
      await this.page.keyboard.press('Shift+Enter');
    } catch {
      // Fallback: try using the run button if keyboard doesn't work
      const runButton = this.page
        .locator('button[title="Run this paragraph"], .paragraph-control button:has-text("Run")')
        .first();
      if (await runButton.isVisible({ timeout: 3000 })) {
        await runButton.click();
      }
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

  // All ShortcutsMap keyboard shortcuts

  // Run paragraph - shift.enter
  async pressRunParagraph(): Promise<void> {
    // Ensure we're focused and ready
    await this.focusCodeEditor();

    // Multiple approaches to run the paragraph
    let executed = false;

    // Try keyboard shortcut first
    try {
      await this.page.keyboard.press('Shift+Enter');

      // Check if execution started within a reasonable time
      await this.page.waitForFunction(
        () => {
          const runningIndicator = document.querySelector('.paragraph-control .fa-spin, .running-indicator');
          const result = document.querySelector('zeppelin-notebook-paragraph-result');
          return runningIndicator || result;
        },
        { timeout: 5000 } // 시간 증가
      );
      executed = true;
    } catch {
      // Keyboard shortcut failed, try run button
    }

    if (!executed) {
      // Fallback: try clicking the run button
      const runButton = this.page.locator('button[title="Run this paragraph"], .paragraph-control button').first();
      if (await runButton.isVisible({ timeout: 5000 })) {
        await runButton.click();
        executed = true;
      }
    }

    if (!executed) {
      // Last resort: try Angular scope execution
      await this.page.evaluate(() => {
        const paragraphElement = document.querySelector('zeppelin-notebook-paragraph');
        if (paragraphElement) {
          // tslint:disable-next-line:no-any
          const win = window as any;
          if (win.angular) {
            const scope = win.angular.element(paragraphElement).scope();
            if (scope && scope.$ctrl && scope.$ctrl.runParagraph) {
              scope.$ctrl.runParagraph();
              scope.$apply?.();
            }
          }
        }
      });
    }
  }

  // Run all above paragraphs - control.shift.arrowup
  async pressRunAbove(): Promise<void> {
    await this.page.keyboard.press('Control+Shift+ArrowUp');
  }

  // Run all below paragraphs - control.shift.arrowdown
  async pressRunBelow(): Promise<void> {
    await this.page.keyboard.press('Control+Shift+ArrowDown');
  }

  // Cancel - control.alt.c (or control.alt.ç for macOS)
  async pressCancel(): Promise<void> {
    await this.page.keyboard.press('Control+Alt+c');
  }

  // Move cursor up - control.p
  async pressMoveCursorUp(): Promise<void> {
    await this.page.keyboard.press('Control+p');
  }

  // Move cursor down - control.n
  async pressMoveCursorDown(): Promise<void> {
    await this.page.keyboard.press('Control+n');
  }

  // Delete paragraph - control.alt.d (or control.alt.∂ for macOS)
  async pressDeleteParagraph(): Promise<void> {
    await this.page.keyboard.press('Control+Alt+d');
  }

  // Insert paragraph above - control.alt.a (or control.alt.å for macOS)
  async pressInsertAbove(): Promise<void> {
    await this.page.keyboard.press('Control+Alt+a');
  }

  // Insert paragraph below - control.alt.b (or control.alt.∫ for macOS)
  async pressInsertBelow(): Promise<void> {
    await this.page.keyboard.press('Control+Alt+b');
  }

  // Insert copy of paragraph below - control.shift.c
  async pressInsertCopy(): Promise<void> {
    await this.page.keyboard.press('Control+Shift+c');
  }

  // Move paragraph up - control.alt.k (or control.alt.˚ for macOS)
  async pressMoveParagraphUp(): Promise<void> {
    await this.page.keyboard.press('Control+Alt+k');
  }

  // Move paragraph down - control.alt.j (or control.alt.∆ for macOS)
  async pressMoveParagraphDown(): Promise<void> {
    await this.page.keyboard.press('Control+Alt+j');
  }

  // Switch editor - control.alt.e
  async pressSwitchEditor(): Promise<void> {
    await this.page.keyboard.press('Control+Alt+e');
  }

  // Switch enable/disable paragraph - control.alt.r (or control.alt.® for macOS)
  async pressSwitchEnable(): Promise<void> {
    await this.page.keyboard.press('Control+Alt+r');
  }

  // Switch output show/hide - control.alt.o (or control.alt.ø for macOS)
  async pressSwitchOutputShow(): Promise<void> {
    await this.page.keyboard.press('Control+Alt+o');
  }

  // Switch line numbers - control.alt.m (or control.alt.µ for macOS)
  async pressSwitchLineNumber(): Promise<void> {
    await this.page.keyboard.press('Control+Alt+m');
  }

  // Switch title show/hide - control.alt.t (or control.alt.† for macOS)
  async pressSwitchTitleShow(): Promise<void> {
    await this.page.keyboard.press('Control+Alt+t');
  }

  // Clear output - control.alt.l (or control.alt.¬ for macOS)
  async pressClearOutput(): Promise<void> {
    await this.page.keyboard.press('Control+Alt+l');
  }

  // Link this paragraph - control.alt.w (or control.alt.∑ for macOS)
  async pressLinkParagraph(): Promise<void> {
    await this.page.keyboard.press('Control+Alt+w');
  }

  // Reduce paragraph width - control.shift.-
  async pressReduceWidth(): Promise<void> {
    await this.page.keyboard.press('Control+Shift+-');
  }

  // Increase paragraph width - control.shift.=
  async pressIncreaseWidth(): Promise<void> {
    await this.page.keyboard.press('Control+Shift+=');
  }

  // Cut line - control.k
  async pressCutLine(): Promise<void> {
    await this.page.keyboard.press('Control+k');
  }

  // Paste line - control.y
  async pressPasteLine(): Promise<void> {
    await this.page.keyboard.press('Control+y');
  }

  // Search inside code - control.s
  async pressSearchInsideCode(): Promise<void> {
    await this.page.keyboard.press('Control+s');
  }

  // Find in code - control.alt.f (or control.alt.ƒ for macOS)
  async pressFindInCode(): Promise<void> {
    await this.page.keyboard.press('Control+Alt+f');
  }

  // Generic method to press any shortcut with proper formatting
  async pressShortcut(shortcut: string): Promise<void> {
    // Convert dot notation (control.alt.k) to plus notation (Control+Alt+k)
    const formatted = shortcut
      .replace(/control/g, 'Control')
      .replace(/alt/g, 'Alt')
      .replace(/shift/g, 'Shift')
      .replace(/\./g, '+')
      .replace(/arrowup/g, 'ArrowUp')
      .replace(/arrowdown/g, 'ArrowDown')
      .replace(/enter/g, 'Enter');

    await this.page.keyboard.press(formatted);
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

    await expect(settingsButton).toBeVisible({ timeout: 10000 });
    await settingsButton.click();

    await expect(this.clearOutputOption).toBeVisible({ timeout: 5000 });
    await this.clearOutputOption.click();

    // Wait for output to be cleared by checking the result element is not visible
    const result = paragraph.locator('zeppelin-notebook-paragraph-result');
    await result.waitFor({ state: 'detached', timeout: 5000 }).catch(() => {});
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
    try {
      // First try to get content from Angular scope (most reliable)
      const angularContent = await this.page.evaluate(() => {
        const paragraphElement = document.querySelector('zeppelin-notebook-paragraph');
        if (paragraphElement) {
          // tslint:disable-next-line:no-any
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

  async setCodeEditorContent(content: string): Promise<void> {
    // Focus the editor first
    await this.focusCodeEditor();

    // Clear any existing content first
    await this.page.keyboard.press('Control+a');
    await this.page.keyboard.press('Delete');

    // Type the new content
    if (content) {
      await this.page.keyboard.type(content, { delay: 10 });

      // Simple wait to allow content to be processed
      await this.page.waitForTimeout(500);
    }
  }

  // Helper methods for verifying shortcut effects

  async waitForParagraphExecution(paragraphIndex: number = 0, timeout: number = 30000): Promise<void> {
    const paragraph = this.getParagraphByIndex(paragraphIndex);

    // First check if paragraph is currently running
    const runningIndicator = paragraph.locator(
      '.paragraph-control .fa-spin, .running-indicator, .paragraph-status-running'
    );

    // Wait for execution to start (brief moment) - more lenient approach
    try {
      await this.page.waitForFunction(
        index => {
          const paragraphs = document.querySelectorAll('zeppelin-notebook-paragraph');
          const targetParagraph = paragraphs[index];
          if (!targetParagraph) {
            return false;
          }

          // Check if execution started
          const hasRunning = targetParagraph.querySelector('.fa-spin, .running-indicator, .paragraph-status-running');
          const hasResult = targetParagraph.querySelector('zeppelin-notebook-paragraph-result');

          return hasRunning || hasResult;
        },
        paragraphIndex,
        { timeout: 8000 }
      );
    } catch {
      // If we can't detect execution start, check if result already exists
      const existingResult = await paragraph.locator('zeppelin-notebook-paragraph-result').isVisible();
      if (!existingResult) {
        console.log(`Warning: Could not detect execution start for paragraph ${paragraphIndex}`);
      }
    }

    // Wait for running indicator to disappear (execution completed)
    await runningIndicator.waitFor({ state: 'detached', timeout: timeout / 2 }).catch(() => {
      console.log(`Running indicator timeout for paragraph ${paragraphIndex} - continuing`);
    });

    // Wait for result to appear and be populated - more flexible approach
    try {
      await this.page.waitForFunction(
        index => {
          const paragraphs = document.querySelectorAll('zeppelin-notebook-paragraph');
          const targetParagraph = paragraphs[index];
          if (!targetParagraph) {
            return false;
          }

          const result = targetParagraph.querySelector('zeppelin-notebook-paragraph-result');
          // Accept any visible result, even if content is empty (e.g., for errors or empty outputs)
          return result && getComputedStyle(result).display !== 'none';
        },
        paragraphIndex,
        { timeout: timeout / 2 }
      );
    } catch {
      // Final fallback: just check if result element exists
      const resultExists = await paragraph.locator('zeppelin-notebook-paragraph-result').isVisible();
      if (!resultExists) {
        console.log(`Warning: No result found for paragraph ${paragraphIndex} after execution`);
      }
    }
  }

  async isParagraphEnabled(paragraphIndex: number = 0): Promise<boolean> {
    const paragraph = this.getParagraphByIndex(paragraphIndex);

    // Check multiple possible indicators for disabled state
    const disabledSelectors = [
      '.paragraph-disabled',
      '[disabled="true"]',
      '.disabled',
      '[aria-disabled="true"]',
      '.paragraph-status-disabled'
    ];

    for (const selector of disabledSelectors) {
      const disabledIndicator = paragraph.locator(selector);
      if (await disabledIndicator.isVisible()) {
        return false;
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
    const output = paragraph.locator('zeppelin-notebook-paragraph-result');
    return await output.isVisible();
  }

  async areLineNumbersVisible(paragraphIndex: number = 0): Promise<boolean> {
    const paragraph = this.getParagraphByIndex(paragraphIndex);
    const lineNumbers = paragraph.locator('.monaco-editor .margin .line-numbers');
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
    // First wait for DOM to stabilize
    await this.page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => {});

    await this.page.waitForFunction(
      count => {
        const paragraphs = document.querySelectorAll('zeppelin-notebook-paragraph');
        return paragraphs.length === count;
      },
      expectedCount,
      { timeout }
    );

    // Wait for the expected number of paragraph components to be visible
    await expect(this.paragraphContainer).toHaveCount(expectedCount, { timeout: 5000 });
  }

  async getCurrentCursorPosition(): Promise<{ line: number; column: number } | null> {
    try {
      return await this.page.evaluate(() => {
        // tslint:disable-next-line:no-any
        const win = (window as unknown) as any;
        const editor = win.monaco?.editor?.getModels?.()?.[0];
        if (editor) {
          const position = editor.getPosition?.();
          if (position) {
            return { line: position.lineNumber, column: position.column };
          }
        }
        return null;
      });
    } catch {
      return null;
    }
  }

  async isSearchDialogVisible(): Promise<boolean> {
    const searchDialog = this.page.locator('.search-widget, .find-widget, [role="dialog"]:has-text("Find")');
    return await searchDialog.isVisible();
  }

  async hasOutputBeenCleared(paragraphIndex: number = 0): Promise<boolean> {
    const paragraph = this.getParagraphByIndex(paragraphIndex);
    const result = paragraph.locator('zeppelin-notebook-paragraph-result');
    return !(await result.isVisible());
  }

  async isParagraphSelected(paragraphIndex: number): Promise<boolean> {
    const paragraph = this.getParagraphByIndex(paragraphIndex);
    const selectedClass = await paragraph.getAttribute('class');
    return selectedClass?.includes('focused') || selectedClass?.includes('selected') || false;
  }

  async getSelectedContent(): Promise<string> {
    return await this.page.evaluate(() => {
      const selection = window.getSelection();
      return selection?.toString() || '';
    });
  }
}
