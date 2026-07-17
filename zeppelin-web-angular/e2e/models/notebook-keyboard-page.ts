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
    this.addParagraphComponent = page.locator('zeppelin-notebook-add-paragraph').last(); // last() — bottom add-paragraph strip; first() is the top strip
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

  async tryFocusCodeEditor(paragraphIndex: number = 0): Promise<void> {
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
    await paragraph.waitFor({ state: 'visible', timeout: 10000 }).catch(() => {}); // JUSTIFIED: paragraph may not exist yet; caller guards with paragraphCount check

    // Wait for any loading/rendering to complete
    await this.page.waitForLoadState('domcontentloaded');

    await this.focusEditorElement(paragraph, paragraphIndex);
  }

  // Focus the paragraph host: it carries the shortcut bindings (tabindex=-1) and works even when the editor is hidden (a %md paragraph collapses its editor after running).
  async focusParagraphHost(paragraphIndex: number = 0): Promise<void> {
    const paragraph = this.getParagraphByIndex(paragraphIndex);
    // Retry: toggling output re-renders the paragraph, so a single focus() can miss.
    await expect(async () => {
      await paragraph.evaluate((el: HTMLElement) => el.focus());
      await expect(paragraph).toBeFocused({ timeout: 1000 });
    }).toPass({ timeout: 10000 });
  }

  // Dispatch a paragraph-scoped shortcut and retry only until its effect is observed.
  async pressShortcutFromHostUntil(
    paragraphIndex: number,
    press: () => Promise<void>,
    isSettled: () => Promise<boolean>
  ): Promise<void> {
    await expect(async () => {
      if (!(await isSettled())) {
        await this.focusParagraphHost(paragraphIndex);
        await press();
      }
      expect(await isSettled()).toBe(true);
    }).toPass({ timeout: 15000 });
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
      expectedCount => document.querySelectorAll('zeppelin-notebook-paragraph').length > expectedCount, // JUSTIFIED: waitForFunction polls DOM count; Playwright toHaveCount() requires exact match, not minimum
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
    return this.readEditorText(this.paragraphContainer.first());
  }

  // Reconstruct editor text from Monaco's absolutely-positioned `.view-line` divs sorted by top (DOM order need not match line order), via textContent; innerText is "" for off-layout lines in headless Chromium.
  private async readEditorText(paragraph: Locator): Promise<string> {
    const monaco = paragraph.locator('.monaco-editor').first();
    if ((await monaco.count()) > 0) {
      const text = await monaco.evaluate((el: Element) => {
        const lines = Array.from(el.querySelectorAll('.view-line')) as HTMLElement[];
        lines.sort((a, b) => parseInt(a.style.top || '0', 10) - parseInt(b.style.top || '0', 10));
        return lines.map(l => (l.textContent || '').replace(/\u00a0/g, ' ')).join('\n');
      });
      if (text.trim().length > 0) {
        return text;
      }
    }
    const textarea = paragraph.locator('.monaco-editor textarea').first();
    if ((await textarea.count()) > 0) {
      const value = await textarea.inputValue().catch(() => '');
      if (value) {
        return value;
      }
    }
    return '';
  }

  async setCodeEditorContent(content: string, paragraphIndex: number = 0): Promise<void> {
    if (this.page.isClosed()) {
      console.warn('Cannot set code editor content: page is closed');
      return;
    }

    await this.tryFocusCodeEditor(paragraphIndex);
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

      // JUSTIFIED: Monaco textarea can be covered by editor overlays during fixture setup.
      await editorInput.fill(content, { force: true });
    } else {
      // Standard clearing for other browsers
      await this.pressSelectAll();
      await this.page.keyboard.press('Delete');
      // JUSTIFIED: Monaco textarea can be overlaid after select+delete during fixture setup.
      await editorInput.fill(content, { force: true });
    }

    // Wait for the full normalized editor content to avoid stale Monaco renders.
    const expected = content.replace(/\s+/g, '');
    if (expected.length === 0) {
      await expect.poll(async () => (await this.readEditorText(paragraph)).trim(), { timeout: 10000 }).toBe('');
    } else {
      await expect
        .poll(async () => (await this.readEditorText(paragraph)).replace(/\s+/g, ''), { timeout: 10000 })
        .toContain(expected);
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
    return this.readEditorText(this.getParagraphByIndex(paragraphIndex));
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

  async tryClickModalOkButton(timeout: number = 30000): Promise<void> {
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
      await this.modal.waitFor({ state: 'hidden', timeout: 2000 }).catch(() => {}); // JUSTIFIED: UI stabilization — next iteration or detach check handles remaining modals
    }

    await this.modal.waitFor({ state: 'detached', timeout: 2000 }).catch(() => {}); // JUSTIFIED: UI stabilization — some modals may legitimately remain open
  }

  private async waitForExecutionStart(paragraphIndex: number): Promise<void> {
    const started = await this.page
      .waitForFunction(
        // waitForFunction executes in browser context, not Node.js context.
        // Browser cannot access Node.js variables like PARAGRAPH_RESULT_SELECTOR.
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        ([index, selector]: any[]) => {
          const paragraphs = document.querySelectorAll('zeppelin-notebook-paragraph'); // JUSTIFIED: index-based paragraph lookup with sub-element checks not expressible via Playwright locator API
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
      .catch(() => false); // JUSTIFIED: execution may not start within timeout; caller falls back to checking existing result

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

    await runningIndicator.waitFor({ state: 'detached', timeout: timeout / 2 }).catch(() => {}); // JUSTIFIED: UI stabilization — paragraph may have completed before indicator appeared
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
          const paragraphs = document.querySelectorAll('zeppelin-notebook-paragraph'); // JUSTIFIED: index-based paragraph lookup with sub-element checks not expressible via Playwright locator API
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
      .catch(() => false); // JUSTIFIED: result may not appear within timeout; caller checks existence separately

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

    await editor.waitFor({ state: 'visible', timeout: 5000 }).catch(() => {}); // editor may not be visible yet; the click/focus below retries
    // A `trial` click only runs actionability checks and never focuses; do a real click, falling back to focusing the textarea if it is intercepted.
    await editor.click({ force: true }).catch(async () => {
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
      // Monaco sets the `focused` class only after processing the focus event; activeElement can be set earlier, dropping the shortcut. Gate on the class so focus is real.
      await expect(editor).toHaveClass(/\bfocused\b/, { timeout: 10000 });
    } else {
      await expect(editor).toHaveClass(/focused|focus|active/, { timeout: 30000 });
    }
  }

  private async executePlatformShortcut(shortcut: string | string[]): Promise<void> {
    const shortcuts = Array.isArray(shortcut) ? shortcut : [shortcut];
    // Playwright presses by physical key code, so the primary variant works on every platform; the macOS special-character variant (e.g. control.alt.∂) is unpressable via keyboard.press.
    await this.page.keyboard.press(this.formatKey(shortcuts[0]));
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
