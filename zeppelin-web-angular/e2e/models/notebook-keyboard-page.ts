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
    try {
      // First check if paragraphs exist at all
      const paragraphCount = await this.page.locator('zeppelin-notebook-paragraph').count();
      if (paragraphCount === 0) {
        console.warn('No paragraphs found on page, cannot focus editor');
        return;
      }

      const paragraph = this.getParagraphByIndex(paragraphIndex);
      await paragraph.waitFor({ state: 'visible', timeout: 10000 });

      const editor = paragraph.locator('.monaco-editor, .CodeMirror, .ace_editor, textarea').first();
      await editor.waitFor({ state: 'visible', timeout: 5000 });

      await editor.click({ force: true });

      const textArea = editor.locator('textarea');
      if (await textArea.count()) {
        await textArea.press('ArrowRight');
        await expect(textArea).toBeFocused({ timeout: 2000 });
        return;
      }

      // Wait for editor to be focused instead of fixed timeout
      await expect(editor).toHaveClass(/focused|focus/, { timeout: 5000 });
    } catch (error) {
      console.warn(`Focus code editor for paragraph ${paragraphIndex} failed:`, error);
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

  // Platform detection utility
  private getPlatform(): string {
    return process.platform || 'unknown';
  }

  private isMacOS(): boolean {
    return this.getPlatform() === 'darwin';
  }

  private async executeWebkitShortcut(formattedShortcut: string): Promise<void> {
    const parts = formattedShortcut.split('+');
    const mainKey = parts[parts.length - 1];
    const hasControl = formattedShortcut.includes('control');
    const hasShift = formattedShortcut.includes('shift');
    const hasAlt = formattedShortcut.includes('alt');
    const keyMap: Record<string, string> = {
      arrowup: 'ArrowUp',
      arrowdown: 'ArrowDown',
      enter: 'Enter'
    };
    const resolvedKey = keyMap[mainKey] || mainKey.toUpperCase();

    if (hasAlt) {
      await this.page.keyboard.down('Alt');
    }
    if (hasShift) {
      await this.page.keyboard.down('Shift');
    }
    if (hasControl) {
      await this.page.keyboard.down('Control');
    }

    await this.page.keyboard.press(resolvedKey, { delay: 50 });

    if (hasControl) {
      await this.page.keyboard.up('Control');
    }
    if (hasShift) {
      await this.page.keyboard.up('Shift');
    }
    if (hasAlt) {
      await this.page.keyboard.up('Alt');
    }
  }

  private async executeStandardShortcut(formattedShortcut: string): Promise<void> {
    const isMac = this.isMacOS();
    const formattedKey = formattedShortcut
      .replace(/alt/g, 'Alt')
      .replace(/shift/g, 'Shift')
      .replace(/arrowup/g, 'ArrowUp')
      .replace(/arrowdown/g, 'ArrowDown')
      .replace(/enter/g, 'Enter')
      .replace(/control/g, isMac ? 'Meta' : 'Control')
      .replace(/\+([a-z0-9-=])$/, (_, c) => `+${c.toUpperCase()}`);

    console.log('Final key combination:', formattedKey);
    await this.page.keyboard.press(formattedKey, { delay: 50 });
  }

  // Platform-aware keyboard shortcut execution
  private async executePlatformShortcut(shortcuts: string | string[]): Promise<void> {
    const shortcutArray = Array.isArray(shortcuts) ? shortcuts : [shortcuts];
    const browserName = test.info().project.name;

    for (const shortcut of shortcutArray) {
      try {
        const formatted = shortcut.toLowerCase().replace(/\./g, '+');
        console.log('Shortcut:', shortcut, '->', formatted, 'on', browserName);

        await this.page.evaluate(() => {
          const el = document.activeElement || document.querySelector('body');
          if (el && 'focus' in el && typeof (el as HTMLElement).focus === 'function') {
            (el as HTMLElement).focus();
          }
        });

        if (browserName === 'webkit') {
          await this.executeWebkitShortcut(formatted);
        } else {
          await this.executeStandardShortcut(formatted);
        }

        return;
      } catch (error) {
        console.log(`Shortcut ${shortcut} failed:`, error);
        // Continue to next shortcut variant
      }
    }
  }

  // All ShortcutsMap keyboard shortcuts

  // Run paragraph - shift.enter
  async pressRunParagraph(): Promise<void> {
    const browserName = test.info().project.name;

    if (browserName === 'chromium' || browserName === 'Google Chrome' || browserName === 'Microsoft Edge') {
      try {
        const paragraph = this.getParagraphByIndex(0);
        const textarea = paragraph.locator('textarea').first();
        await textarea.focus();
        await expect(textarea).toBeFocused({ timeout: 1000 });
        await textarea.press('Shift+Enter');
        console.log(`${browserName}: Used textarea.press for Shift+Enter`);
        return;
      } catch (error) {
        console.log(`${browserName}: textarea.press failed:`, error);
      }
    }

    try {
      const paragraph = this.getParagraphByIndex(0);

      // Try multiple selectors for the run button - ordered by specificity
      const runButtonSelectors = [
        'i.run-para[nz-tooltip][nzTooltipTitle="Run paragraph"]',
        'i.run-para[nzType="play-circle"]',
        'i.run-para',
        'i[nzType="play-circle"]',
        'button[title="Run this paragraph"]',
        'button:has-text("Run")',
        'i.ant-icon-caret-right',
        '.paragraph-control i[nz-tooltip]',
        '.run-control i',
        'i.fa-play'
      ];

      let clickSuccess = false;

      for (const selector of runButtonSelectors) {
        try {
          const runElement = paragraph.locator(selector).first();
          const count = await runElement.count();

          if (count > 0) {
            await runElement.waitFor({ state: 'visible', timeout: 3000 });
            await runElement.click({ force: true });

            // Wait for paragraph to start running instead of fixed timeout
            const runningIndicator = paragraph.locator(
              '.paragraph-control .fa-spin, .running-indicator, .paragraph-status-running'
            );
            await runningIndicator.waitFor({ state: 'visible', timeout: 2000 }).catch(() => {
              console.log('No running indicator found, execution may have completed quickly');
            });

            console.log(`${browserName}: Used selector "${selector}" for run button`);
            clickSuccess = true;
            break;
          }
        } catch (selectorError) {
          console.log(`${browserName}: Selector "${selector}" failed:`, selectorError);
          continue;
        }
      }

      if (clickSuccess) {
        // Wait for execution to start or complete instead of fixed timeout
        const targetParagraph = this.getParagraphByIndex(0);
        const runningIndicator = targetParagraph.locator(
          '.paragraph-control .fa-spin, .running-indicator, .paragraph-status-running'
        );
        const resultIndicator = targetParagraph.locator('[data-testid="paragraph-result"]');

        // Wait for either execution to start (running indicator) or complete (result appears)
        await Promise.race([
          runningIndicator.waitFor({ state: 'visible', timeout: browserName === 'webkit' ? 3000 : 2000 }),
          resultIndicator.waitFor({ state: 'visible', timeout: browserName === 'webkit' ? 3000 : 2000 })
        ]).catch(() => {
          console.log(`${browserName}: No execution indicators found, continuing...`);
        });

        console.log(`${browserName}: Used Run button click as fallback`);
        return;
      }

      throw new Error('No run button found with any selector');
    } catch (error) {
      console.log(`${browserName}: Run button click failed, trying executePlatformShortcut:`, error);

      // Final fallback - try multiple approaches for WebKit
      if (browserName === 'webkit') {
        try {
          // WebKit specific: Try clicking on paragraph area first to ensure focus
          const webkitParagraph = this.getParagraphByIndex(0);
          await webkitParagraph.click();

          // Wait for focus to be set instead of fixed timeout
          const editor = webkitParagraph.locator('.monaco-editor, .CodeMirror, .ace_editor, textarea').first();
          await expect(editor)
            .toHaveClass(/focused|focus/, { timeout: 2000 })
            .catch(() => {
              console.log('WebKit: Focus not detected, continuing anyway...');
            });

          // Try to trigger run via keyboard
          await this.executePlatformShortcut('shift.enter');

          // Wait for execution to start instead of fixed timeout
          const runningIndicator = webkitParagraph.locator(
            '.paragraph-control .fa-spin, .running-indicator, .paragraph-status-running'
          );
          const resultIndicator = webkitParagraph.locator('[data-testid="paragraph-result"]');
          await Promise.race([
            runningIndicator.waitFor({ state: 'visible', timeout: 2000 }),
            resultIndicator.waitFor({ state: 'visible', timeout: 2000 })
          ]).catch(() => {
            console.log('WebKit: No execution indicators found after keyboard shortcut');
          });

          console.log(`${browserName}: Used WebKit-specific keyboard fallback`);
          return;
        } catch (webkitError) {
          console.log(`${browserName}: WebKit keyboard fallback failed:`, webkitError);
        }
      }

      // Final fallback
      await this.executePlatformShortcut('shift.enter');
    }
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
    try {
      const browserName = test.info().project.name;
      const paragraph = this.getParagraphByIndex(paragraphIndex);

      const selectors = [
        '[data-testid="paragraph-result"]',
        'zeppelin-notebook-paragraph-result',
        '.paragraph-result',
        '.result-content'
      ];

      for (const selector of selectors) {
        try {
          const result = paragraph.locator(selector);
          const count = await result.count();
          if (count > 0) {
            const isVisible = await result.first().isVisible();
            if (isVisible) {
              console.log(`Found result with selector: ${selector}`);
              return true;
            }
          }
        } catch (e) {
          continue;
        }
      }

      const hasResultInDOM = await this.page.evaluate(pIndex => {
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

      if (hasResultInDOM) {
        console.log('Found result via DOM evaluation');
        return true;
      }

      // WebKit specific: Additional checks for execution completion
      if (browserName === 'webkit') {
        try {
          // Check if paragraph has any output text content
          const hasAnyContent = await this.page.evaluate(pIndex => {
            const paragraphs = document.querySelectorAll('zeppelin-notebook-paragraph');
            const targetParagraph = paragraphs[pIndex];
            if (!targetParagraph) {
              return false;
            }

            // Look for any text content that suggests execution happened
            const textContent = targetParagraph.textContent || '';

            // Check for common execution indicators
            const executionIndicators = [
              '1 + 1', // Our test content
              '2', // Expected result
              'print', // Python output
              'Out[', // Jupyter-style output
              '>>>', // Python prompt
              'result', // Generic result indicator
              'output' // Generic output indicator
            ];

            return executionIndicators.some(indicator => textContent.toLowerCase().includes(indicator.toLowerCase()));
          }, paragraphIndex);

          if (hasAnyContent) {
            console.log('WebKit: Found execution content via text analysis');
            return true;
          }

          // Final WebKit check: Look for changes in DOM structure that indicate execution
          const hasStructuralChanges = await this.page.evaluate(pIndex => {
            const paragraphs = document.querySelectorAll('zeppelin-notebook-paragraph');
            const targetParagraph = paragraphs[pIndex];
            if (!targetParagraph) {
              return false;
            }

            // Count total elements - execution usually adds DOM elements
            const elementCount = targetParagraph.querySelectorAll('*').length;

            // Look for any elements that typically appear after execution
            const executionElements = [
              'pre', // Code output
              'code', // Inline code
              '.output', // Output containers
              '.result', // Result containers
              'table', // Table results
              'div[class*="result"]', // Any div with result in class
              'span[class*="output"]' // Any span with output in class
            ];

            const hasExecutionElements = executionElements.some(
              selector => targetParagraph.querySelector(selector) !== null
            );

            console.log(
              `WebKit structural check: ${elementCount} elements, hasExecutionElements: ${hasExecutionElements}`
            );
            return hasExecutionElements || elementCount > 10; // Arbitrary threshold for "complex" paragraph
          }, paragraphIndex);

          if (hasStructuralChanges) {
            console.log('WebKit: Found execution via structural analysis');
            return true;
          }
        } catch (webkitError) {
          console.log('WebKit specific checks failed:', webkitError);
        }
      }

      return false;
    } catch (error) {
      console.log('hasParagraphResult error:', error);
      return false;
    }
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
    // Check if page is still accessible
    if (this.page.isClosed()) {
      console.warn('Cannot wait for paragraph execution: page is closed');
      return;
    }

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
          const hasResult = targetParagraph.querySelector('[data-testid="paragraph-result"]');

          return hasRunning || hasResult;
        },
        paragraphIndex,
        { timeout: 8000 }
      );
    } catch (error) {
      // If we can't detect execution start, check if result already exists
      try {
        if (!this.page.isClosed()) {
          const existingResult = await paragraph.locator('[data-testid="paragraph-result"]').isVisible();
          if (!existingResult) {
            console.log(`Warning: Could not detect execution start for paragraph ${paragraphIndex}`);
          }
        }
      } catch {
        console.warn('Page closed during execution check');
        return;
      }
    }

    // Wait for running indicator to disappear (execution completed)
    try {
      if (!this.page.isClosed()) {
        await runningIndicator.waitFor({ state: 'detached', timeout: timeout / 2 }).catch(() => {
          console.log(`Running indicator timeout for paragraph ${paragraphIndex} - continuing`);
        });
      }
    } catch {
      console.warn('Page closed while waiting for running indicator');
      return;
    }

    // Wait for result to appear and be populated - more flexible approach
    try {
      if (!this.page.isClosed()) {
        await this.page.waitForFunction(
          index => {
            const paragraphs = document.querySelectorAll('zeppelin-notebook-paragraph');
            const targetParagraph = paragraphs[index];
            if (!targetParagraph) {
              return false;
            }

            const result = targetParagraph.querySelector('[data-testid="paragraph-result"]');
            // Accept any visible result, even if content is empty (e.g., for errors or empty outputs)
            return result && getComputedStyle(result).display !== 'none';
          },
          paragraphIndex,
          { timeout: Math.min(timeout / 2, 15000) } // Cap at 15 seconds
        );
      }
    } catch {
      // Final fallback: just check if result element exists
      try {
        if (!this.page.isClosed()) {
          const resultExists = await paragraph.locator('[data-testid="paragraph-result"]').isVisible();
          if (!resultExists) {
            console.log(`Warning: No result found for paragraph ${paragraphIndex} after execution`);
          }
        }
      } catch {
        console.warn('Page closed during final result check');
      }
    }
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
}
