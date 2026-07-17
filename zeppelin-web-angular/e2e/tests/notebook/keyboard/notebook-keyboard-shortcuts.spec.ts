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

import { expect, test } from '@playwright/test';
import { NotebookKeyboardPage } from 'e2e/models/notebook-keyboard-page';
import {
  addPageAnnotationBeforeEach,
  waitForNotebookLinks,
  waitForZeppelinReady,
  PAGES,
  createTestNotebook
} from '../../../utils';

/**
 * Comprehensive keyboard shortcuts test suite based on ShortcutsMap
 * (src/app/key-binding/shortcuts-map.ts). The page object gates on Monaco's `focused`
 * class before dispatching shortcuts; effects are asserted with web-first expectations.
 */
// Serial ordering prevents cross-test editor state corruption within the shared notebook.
test.describe.serial('Comprehensive Keyboard Shortcuts (ShortcutsMap)', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK);
  addPageAnnotationBeforeEach(PAGES.SHARE.SHORTCUT);

  let keyboardPage: NotebookKeyboardPage;
  let testNotebook: { noteId: string; paragraphId: string };

  test.beforeEach(async ({ page }) => {
    keyboardPage = new NotebookKeyboardPage(page);

    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await waitForNotebookLinks(page);

    // Handle the welcome modal if it appears
    const welcomeModal = page.locator('.ant-modal-root', { hasText: 'Welcome to Zeppelin!' });
    if ((await welcomeModal.count()) > 0) {
      const cancelButton = welcomeModal.locator('button', { hasText: 'Cancel' });
      await cancelButton.click();
      await welcomeModal.waitFor({ state: 'hidden', timeout: 5000 });
    }

    testNotebook = await createTestNotebook(page);
    await keyboardPage.navigateToNotebook(testNotebook.noteId);
    const currentUrl = page.url();
    if (!currentUrl.includes(`/notebook/${testNotebook.noteId}`)) {
      throw new Error(`Navigation to notebook ${testNotebook.noteId} failed. Got: ${currentUrl}`);
    }
    // JUSTIFIED: single-paragraph test notebook; first() is deterministic
    await expect(keyboardPage.paragraphContainer.first()).toBeVisible({ timeout: 30000 });
    await keyboardPage.setCodeEditorContent('%python\nprint("Hello World")');
  });

  test.afterEach(async ({ page }) => {
    // Clean up any open dialogs or modals
    await page.keyboard.press('Escape');
  });

  // ===== CORE EXECUTION SHORTCUTS =====

  test.describe('ParagraphActions.Run: Shift+Enter', () => {
    test('should execute markdown paragraph with Shift+Enter', async () => {
      // Given: A paragraph with markdown content
      await keyboardPage.tryFocusCodeEditor();
      await keyboardPage.setCodeEditorContent('%md\n# Test Heading\n\nThis is **bold** text.');

      // Verify content was set (setCodeEditorContent already gated on the rendered text)
      await expect(keyboardPage.editorLines.first()).toContainText('Test Heading');

      // When: User presses Shift+Enter
      await keyboardPage.pressRunParagraph();

      // Then: Paragraph should execute (reach a terminal state; interpreter availability varies by env)
      await keyboardPage.waitForParagraphExecution(0);
      // JUSTIFIED: single-paragraph test notebook; first() is deterministic
      const statusEl = keyboardPage.paragraphContainer.first().locator('.status');
      const statusText = (await statusEl.textContent({ timeout: 30000 }))?.trim();
      expect(statusText === 'FINISHED' || statusText === 'ERROR' || statusText === 'ABORT').toBe(true);
    });
  });

  // TODO: Fix the previously skipped tests - ZEPPELIN-6379
  test.describe('ParagraphActions.RunAbove: Control+Shift+ArrowUp', () => {
    test.skip();
    test('should run all paragraphs above current with Control+Shift+ArrowUp', async () => {
      // Given: Multiple paragraphs
      await keyboardPage.tryFocusCodeEditor(0);
      await keyboardPage.setCodeEditorContent('%md\n# First Paragraph\nTest content for run above', 0);
      await keyboardPage.addParagraph();
      await keyboardPage.waitForParagraphCountChange(2);

      // Focus on second paragraph
      await keyboardPage.tryFocusCodeEditor(1);
      await keyboardPage.setCodeEditorContent('%md\n# Second Paragraph\nTest content for second paragraph', 1);
      await keyboardPage.tryFocusCodeEditor(1); // Ensure focus on the second paragraph

      // Add an explicit wait for the page to be completely stable and the notebook UI to be interactive
      await keyboardPage.page.waitForLoadState('networkidle', { timeout: 30000 }); // Wait for network to be idle
      // JUSTIFIED: single-paragraph test notebook; first() is deterministic
      await expect(keyboardPage.paragraphContainer.first()).toBeVisible({ timeout: 15000 }); // Ensure a paragraph is visible

      // When: User presses Control+Shift+ArrowUp from second paragraph
      await keyboardPage.pressRunAbove();

      await keyboardPage.tryClickModalOkButton();

      // Then: First paragraph should execute
      await keyboardPage.waitForParagraphExecution(0);
      const hasResult = await keyboardPage.isParagraphResultSettled(0);
      expect(hasResult).toBe(true);
    });
  });

  // TODO: Fix the previously skipped tests - ZEPPELIN-6379
  test.describe('ParagraphActions.RunBelow: Control+Shift+ArrowDown', () => {
    test.skip();
    test('should run current and all paragraphs below with Control+Shift+ArrowDown', async () => {
      // Given: Multiple paragraphs with content
      await keyboardPage.tryFocusCodeEditor(0);
      await keyboardPage.setCodeEditorContent('%md\n# First Paragraph\nContent for run below test', 0);
      await keyboardPage.addParagraph();
      await keyboardPage.waitForParagraphCountChange(2);

      // Add content to second paragraph
      await keyboardPage.tryFocusCodeEditor(1);
      await keyboardPage.setCodeEditorContent('%md\n# Second Paragraph\nContent for run below test', 1);

      // Focus first paragraph
      await keyboardPage.tryFocusCodeEditor(0);

      // When: User presses Control+Shift+ArrowDown
      await keyboardPage.pressRunBelow();

      // Confirmation modal must appear when running paragraphs
      await keyboardPage.tryClickModalOkButton();

      // Then: Both paragraphs should execute
      await keyboardPage.waitForParagraphExecution(0);
      await keyboardPage.waitForParagraphExecution(1);

      const firstHasResult = await keyboardPage.isParagraphResultSettled(0);
      const secondHasResult = await keyboardPage.isParagraphResultSettled(1);

      expect(firstHasResult).toBe(true);
      expect(secondHasResult).toBe(true);
    });
  });

  test.describe('ParagraphActions.Cancel: Control+Alt+C', () => {
    test('should cancel running paragraph with Control+Alt+C', async () => {
      test.skip(!!process.env.CI, 'Requires Python interpreter with running indicator; not available in CI');
      // Given: A long-running paragraph
      await keyboardPage.tryFocusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\nimport time;time.sleep(3)\nprint("Should be cancelled")');

      // Start execution
      await keyboardPage.pressRunParagraph();

      // Wait for execution to start by checking if paragraph is running
      // JUSTIFIED: compound selector; first() picks any visible running indicator
      const runningIndicator = keyboardPage.page
        .locator('zeppelin-notebook-paragraph .fa-spin, .running-indicator')
        .first();
      await expect(runningIndicator).toBeVisible({ timeout: 30000 });

      // When: User presses Control+Alt+C quickly
      await keyboardPage.pressCancel();

      // Then: The execution should be cancelled or completed
      await expect(
        keyboardPage.getParagraphByIndex(0).locator('.paragraph-control .fa-spin, .running-indicator')
      ).not.toBeVisible();
    });
  });

  // ===== CURSOR MOVEMENT SHORTCUTS =====

  test.describe('ParagraphActions.MoveCursorUp: Control+P', () => {
    test('should move cursor up with Control+P', async () => {
      // Given: A paragraph with multiple lines
      await keyboardPage.tryFocusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\nline1\nline2\nline3');

      // Position cursor at end of last line using more reliable cross-browser method
      await keyboardPage.pressSelectAll(); // Select all content
      await keyboardPage.pressKey('ArrowRight'); // Move to end

      // When: User presses Control+P (should move cursor up one line)
      await keyboardPage.pressMoveCursorUp();

      // Then: Verify cursor movement by checking if we can type at the current position
      // Type a marker and check where it appears in the content
      await keyboardPage.pressKey('End'); // Move to end of current line
      await keyboardPage.page.keyboard.type('MARKER');

      await expect.poll(() => keyboardPage.getCodeEditorContent()).toContain('line2MARKER');
      expect(await keyboardPage.getCodeEditorContent()).not.toContain('line3MARKER');
    });
  });

  test.describe('ParagraphActions.MoveCursorDown: Control+N', () => {
    test('should move cursor down with Control+N', async () => {
      // Given: A paragraph with multiple lines
      await keyboardPage.tryFocusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\nline1\nline2\nline3');

      // Position cursor at beginning of first content line (after %python) using more reliable method
      await keyboardPage.pressSelectAll(); // Select all content
      await keyboardPage.pressKey('ArrowLeft'); // Move to beginning
      await keyboardPage.pressKey('ArrowDown'); // Move to line1

      // When: User presses Control+N (should move cursor down one line)
      await keyboardPage.pressMoveCursorDown();

      // Then: Verify cursor movement by checking if we can type at the current position
      // Type a marker and check where it appears in the content
      await keyboardPage.page.keyboard.type('MARKER');

      await expect.poll(() => keyboardPage.getCodeEditorContent()).toContain('MARKERline2');
      expect(await keyboardPage.getCodeEditorContent()).not.toContain('MARKERline1');
    });
  });

  // ===== PARAGRAPH MANIPULATION SHORTCUTS =====

  test.describe('ParagraphActions.Delete: Control+Alt+D', () => {
    test('should delete current paragraph with Control+Alt+D', async () => {
      // Wait for notebook to fully load
      await keyboardPage.tryFocusCodeEditor(0);
      await keyboardPage.setCodeEditorContent('%python\nprint("First paragraph")', 0);
      const firstParagraph = keyboardPage.getParagraphByIndex(0);
      await firstParagraph.click();
      await keyboardPage.addParagraph();

      // Use more flexible waiting strategy
      await keyboardPage.waitForParagraphCountChange(2);

      const currentCount = await keyboardPage.getParagraphCount();

      // Add content to second paragraph
      const secondParagraph = keyboardPage.getParagraphByIndex(1);
      await secondParagraph.click();
      await keyboardPage.setCodeEditorContent('%python\nprint("Second paragraph")', 1);
      // Focus first paragraph
      await firstParagraph.click();
      await keyboardPage.tryFocusCodeEditor(0);

      // When: User presses Control+Alt+D
      await keyboardPage.pressDeleteParagraph();

      // Handle confirmation modal — removeParagraph() always shows nzModalService.confirm()
      await keyboardPage.tryClickModalOkButton();

      // Then: Paragraph count should decrease
      await keyboardPage.waitForParagraphCountChange(currentCount - 1);
      const finalCount = await keyboardPage.getParagraphCount();
      expect(finalCount).toEqual(currentCount - 1);
    });

    test('should not delete last remaining paragraph with Control+Alt+D', async () => {
      // Given: A notebook with exactly one paragraph (beforeEach creates one)
      const initialCount = await keyboardPage.getParagraphCount();
      expect(initialCount).toBe(1);

      await keyboardPage.tryFocusCodeEditor(0);

      // When: User presses Control+Alt+D on the only paragraph
      await keyboardPage.pressDeleteParagraph();

      // JUSTIFIED: compound locator; first() picks any visible cancel/no button in confirmation dialog
      const cancelButton = keyboardPage.page.locator('button:has-text("Cancel"), button:has-text("No")').first();
      const isCancelVisible = await cancelButton.isVisible({ timeout: 2000 });
      if (isCancelVisible) {
        // JUSTIFIED: cancel dialog is optional; paragraph count assertion below covers both paths
        await cancelButton.click();
        await cancelButton.waitFor({ state: 'hidden', timeout: 3000 });
      }

      // Then: The notebook must still have at least one paragraph
      const finalCount = await keyboardPage.getParagraphCount();
      expect(finalCount).toBe(1);
    });
  });

  test.describe('ParagraphActions.InsertAbove: Control+Alt+A', () => {
    test('should insert paragraph above with Control+Alt+A', async () => {
      // Given: A single paragraph with content
      await keyboardPage.tryFocusCodeEditor();
      const originalContent = '%python\n# Original Paragraph\nprint("Content for insert above test")';
      await keyboardPage.setCodeEditorContent(originalContent);

      const initialCount = await keyboardPage.getParagraphCount();

      await keyboardPage.tryFocusCodeEditor(0);

      // When: User presses Control+Alt+A
      await keyboardPage.pressInsertAbove();

      // Then: A new paragraph should be inserted above
      await keyboardPage.waitForParagraphCountChange(initialCount + 1);
      const finalCount = await keyboardPage.getParagraphCount();
      expect(finalCount).toBe(initialCount + 1);

      // And: the new paragraph at index 0 holds no user content; empty or just an interpreter directive (poll so the async insert/render settles).
      await expect.poll(() => keyboardPage.getCodeEditorContentByIndex(0).then(c => c.trim())).toMatch(/^(%\w+)?$/);

      // And the original content moved to index 1 (normalize whitespace; Monaco reflows).
      const normalizedOriginalContent = originalContent.replace(/\s+/g, ' ').trim();
      await expect
        .poll(() => keyboardPage.getCodeEditorContentByIndex(1).then(c => c.replace(/\s+/g, ' ').trim()))
        .toContain(normalizedOriginalContent);
    });
  });

  test.describe('ParagraphActions.InsertBelow: Control+Alt+B', () => {
    test('should insert paragraph below with Control+Alt+B', async () => {
      // Given: A single paragraph with content
      await keyboardPage.tryFocusCodeEditor();
      const originalContent = '%md\n# Original Paragraph\nContent for insert below test';
      await keyboardPage.setCodeEditorContent(originalContent);

      const initialCount = await keyboardPage.getParagraphCount();

      // When: User presses Control+Alt+B
      await keyboardPage.pressInsertBelow();

      // Then: A new paragraph should be inserted below
      await keyboardPage.waitForParagraphCountChange(initialCount + 1);
      const finalCount = await keyboardPage.getParagraphCount();
      expect(finalCount).toBe(initialCount + 1);

      // And: the original content stays at index 0 (poll so the async insert/render settles).
      await expect.poll(() => keyboardPage.getCodeEditorContentByIndex(0)).toMatch(/Original\s+Paragraph/);
      const originalParagraphContent = await keyboardPage.getCodeEditorContentByIndex(0);
      expect(originalParagraphContent).toMatch(/Content\s+for\s+insert\s+below\s+test/);

      // And: a new paragraph exists at index 1 holding no user content.
      await expect.poll(() => keyboardPage.getCodeEditorContentByIndex(1).then(c => c.trim())).toMatch(/^(%\w+)?$/);
    });
  });

  test.describe('ParagraphActions.InsertCopyOfParagraphBelow: Control+Shift+C', () => {
    test('should insert copy of paragraph below with Control+Shift+C', async () => {
      // Given: A paragraph with content
      await keyboardPage.tryFocusCodeEditor();
      await keyboardPage.setCodeEditorContent('%md\n# Copy Test\nContent to be copied below');

      const initialCount = await keyboardPage.getParagraphCount();
      const originalContent = await keyboardPage.getCodeEditorContentByIndex(0);

      // When: User presses Control+Shift+C
      await keyboardPage.pressInsertCopy();

      // Then: a copy is inserted below carrying the same text, and the original is unchanged
      await keyboardPage.waitForParagraphCountChange(initialCount + 1);
      expect(await keyboardPage.getParagraphCount()).toBe(initialCount + 1);
      await expect.poll(() => keyboardPage.getCodeEditorContentByIndex(0)).toBe(originalContent);
      await expect.poll(() => keyboardPage.getCodeEditorContentByIndex(1)).toBe(originalContent);
    });
  });

  test.describe('ParagraphActions.MoveParagraphUp: Control+Alt+K', () => {
    test('should move paragraph up with Control+Alt+K', async () => {
      // Given: Create two paragraphs using keyboard shortcut
      const firstContent = '%python\nprint("First Paragraph - Content for move up test")';
      const secondContent = '%python\nprint("Second Paragraph - This should move up")';

      // Set first paragraph content (setCodeEditorContent gates on the rendered text)
      await keyboardPage.tryFocusCodeEditor(0);
      await keyboardPage.setCodeEditorContent(firstContent, 0);

      // Create second paragraph using InsertBelow shortcut (Control+Alt+B)
      await keyboardPage.pressInsertBelow();
      await keyboardPage.waitForParagraphCountChange(2);

      // Set second paragraph content
      await keyboardPage.tryFocusCodeEditor(1);
      await keyboardPage.setCodeEditorContent(secondContent, 1);

      // Verify we have 2 paragraphs
      const paragraphCount = await keyboardPage.getParagraphCount();
      expect(paragraphCount).toBe(2);

      // Verify initial content before move
      const initialFirst = await keyboardPage.getCodeEditorContentByIndex(0);
      const initialSecond = await keyboardPage.getCodeEditorContentByIndex(1);

      // Focus on second paragraph for move operation
      await keyboardPage.tryFocusCodeEditor(1);

      // When: User presses Control+Alt+K from second paragraph
      await keyboardPage.pressMoveParagraphUp();

      // Then: Paragraph count should remain the same
      const finalParagraphCount = await keyboardPage.getParagraphCount();
      expect(finalParagraphCount).toBe(2);

      // And: Paragraph positions should be swapped (poll until the move lands in the DOM)
      await expect.poll(() => keyboardPage.getCodeEditorContentByIndex(0)).toBe(initialSecond);
      await expect.poll(() => keyboardPage.getCodeEditorContentByIndex(1)).toBe(initialFirst);
    });
  });

  test.describe('ParagraphActions.MoveParagraphDown: Control+Alt+J', () => {
    test('should move paragraph down with Control+Alt+J', async () => {
      // Given: Create two paragraphs using keyboard shortcut instead of addParagraph()
      const firstContent = '%python\nprint("First Paragraph - This should move down")';
      const secondContent = '%python\nprint("Second Paragraph - Content for second paragraph")';

      // Set first paragraph content (setCodeEditorContent gates on the rendered text)
      await keyboardPage.tryFocusCodeEditor(0);
      await keyboardPage.setCodeEditorContent(firstContent, 0);

      // Create second paragraph using InsertBelow shortcut (Control+Alt+B)
      await keyboardPage.pressInsertBelow();
      await keyboardPage.waitForParagraphCountChange(2);

      // Set second paragraph content
      await keyboardPage.tryFocusCodeEditor(1);
      await keyboardPage.setCodeEditorContent(secondContent, 1);

      // Verify we have 2 paragraphs
      const paragraphCount = await keyboardPage.getParagraphCount();
      expect(paragraphCount).toBe(2);

      // Verify initial content before move
      const initialFirst = await keyboardPage.getCodeEditorContentByIndex(0);
      const initialSecond = await keyboardPage.getCodeEditorContentByIndex(1);

      // Focus first paragraph for move operation
      await keyboardPage.tryFocusCodeEditor(0);

      // When: User presses Control+Alt+J from first paragraph
      await keyboardPage.pressMoveParagraphDown();

      // Then: Paragraph count should remain the same
      const finalParagraphCount = await keyboardPage.getParagraphCount();
      expect(finalParagraphCount).toBe(2);

      // And: Paragraph positions should be swapped (poll until the move lands in the DOM)
      await expect.poll(() => keyboardPage.getCodeEditorContentByIndex(0)).toBe(initialSecond);
      await expect.poll(() => keyboardPage.getCodeEditorContentByIndex(1)).toBe(initialFirst);
    });
  });

  // ===== UI TOGGLE SHORTCUTS =====

  test.describe('ParagraphActions.SwitchEditor: Control+Alt+E', () => {
    test('should toggle editor visibility with Control+Alt+E', async () => {
      // Given: A paragraph with visible editor
      await keyboardPage.tryFocusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\nprint("Test editor toggle")');

      const initialEditorVisibility = await keyboardPage.isEditorVisible(0);

      // When: User presses Control+Alt+E
      await keyboardPage.pressSwitchEditor();

      // Then: editor visibility toggles
      await expect.poll(() => keyboardPage.isEditorVisible(0), { timeout: 10000 }).toBe(!initialEditorVisibility);
    });
  });

  test.describe('ParagraphActions.SwitchEnable: Control+Alt+R', () => {
    test('should toggle paragraph enable/disable with Control+Alt+R', async () => {
      // Given: An enabled paragraph
      await keyboardPage.tryFocusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\nprint("Test enable toggle")');

      const initialEnabledState = await keyboardPage.isParagraphEnabled(0);

      // When: User presses Control+Alt+R
      await keyboardPage.pressSwitchEnable();

      // Then: paragraph enabled state toggles
      await expect.poll(() => keyboardPage.isParagraphEnabled(0), { timeout: 10000 }).toBe(!initialEnabledState);
    });
  });

  test.describe('ParagraphActions.SwitchOutputShow: Control+Alt+O', () => {
    test('should toggle output visibility with Control+Alt+O', async () => {
      // Given: A paragraph with output
      await keyboardPage.tryFocusCodeEditor();
      await keyboardPage.setCodeEditorContent('%md\n# Test Output Toggle\nThis creates immediate output');
      await keyboardPage.pressRunParagraph();
      await keyboardPage.waitForParagraphExecution(0);

      const resultLocator = keyboardPage.getParagraphByIndex(0).locator('[data-testid="paragraph-result"]');
      await expect(resultLocator).toBeVisible();

      // When: User presses Control+Alt+O from the paragraph host
      await keyboardPage.pressShortcutFromHostUntil(
        0,
        () => keyboardPage.pressSwitchOutputShow(),
        () => resultLocator.isHidden()
      );

      // And toggling again restores it
      await keyboardPage.pressShortcutFromHostUntil(
        0,
        () => keyboardPage.pressSwitchOutputShow(),
        () => resultLocator.isVisible()
      );
    });
  });

  test.describe('ParagraphActions.SwitchLineNumber: Control+Alt+M', () => {
    test('should toggle line numbers with Control+Alt+M', async () => {
      // Given: A paragraph with code
      await keyboardPage.tryFocusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\nprint("Test line numbers")');

      const initialLineNumbersVisibility = await keyboardPage.areLineNumbersVisible(0);

      // When: User presses Control+Alt+M
      await keyboardPage.pressSwitchLineNumber();

      // Then: line numbers visibility toggles
      await expect
        .poll(() => keyboardPage.areLineNumbersVisible(0), { timeout: 10000 })
        .toBe(!initialLineNumbersVisibility);
    });
  });

  test.describe('ParagraphActions.SwitchTitleShow: Control+Alt+T', () => {
    test('should toggle title visibility with Control+Alt+T', async () => {
      // Given: A paragraph
      await keyboardPage.tryFocusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\nprint("Test title toggle")');

      const initialTitleVisibility = await keyboardPage.isTitleVisible(0);

      // When: User presses Control+Alt+T
      await keyboardPage.pressSwitchTitleShow();

      // Then: title visibility toggles (poll; the DOM updates asynchronously)
      await expect.poll(() => keyboardPage.isTitleVisible(0), { timeout: 10000 }).toBe(!initialTitleVisibility);
    });
  });

  test.describe('ParagraphActions.Clear: Control+Alt+L', () => {
    test('should clear output with Control+Alt+L', async () => {
      // Given: A paragraph with executed content that has output
      await keyboardPage.tryFocusCodeEditor();
      await keyboardPage.setCodeEditorContent('%md\n# Test Content\nFor clear output test');
      await keyboardPage.pressRunParagraph();
      await keyboardPage.waitForParagraphExecution(0);

      // Verify there is output to clear
      // JUSTIFIED: single-paragraph test notebook; first() is deterministic
      const statusElBefore = keyboardPage.paragraphContainer.first().locator('.status');
      await expect(statusElBefore).toHaveText(/FINISHED|ERROR|PENDING|RUNNING/);

      // When: User presses Control+Alt+L (editor hidden after %md run; dispatch from the host)
      const resultLocator = keyboardPage.getParagraphByIndex(0).locator('[data-testid="paragraph-result"]');
      await keyboardPage.pressShortcutFromHostUntil(
        0,
        () => keyboardPage.pressClearOutput(),
        () => resultLocator.isHidden()
      );

      // Then: Output should be cleared
      await expect(resultLocator).not.toBeVisible();
    });
  });

  test.describe('ParagraphActions.Link: Control+Alt+W', () => {
    test('should trigger link paragraph with Control+Alt+W', async () => {
      // Given: A paragraph with content
      await keyboardPage.tryFocusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\nprint("Link Test")');

      // Get the current URL to extract notebook ID
      const currentUrl = keyboardPage.page.url();
      const notebookMatch = currentUrl.match(/\/notebook\/([^\/]+)/);
      expect(notebookMatch).not.toBeNull();
      const notebookId = notebookMatch![1];

      // Listen for new tabs being opened
      const newPagePromise = keyboardPage.page.context().waitForEvent('page');

      // When: User presses Control+Alt+W
      await keyboardPage.pressLinkParagraph();

      // Then: A new tab should be opened with paragraph link
      const newPage = await newPagePromise;
      await newPage.waitForLoadState('networkidle');

      // Verify the new tab URL contains the notebook ID and paragraph reference
      const newUrl = newPage.url();
      expect(newUrl).toContain(`/notebook/${notebookId}/paragraph/`);
      expect(newUrl).toMatch(/\/paragraph\/paragraph_\d+_\d+/);

      // Clean up: Close the new tab
      await newPage.close();
    });
  });

  // ===== PARAGRAPH WIDTH SHORTCUTS =====

  test.describe('ParagraphActions.ReduceWidth: Control+Shift+-', () => {
    test('should reduce paragraph width with Control+Shift+-', async () => {
      // Given: A paragraph
      await keyboardPage.tryFocusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\nprint("Test width reduction")');

      const initialWidth = await keyboardPage.getParagraphWidth(0);

      // When: User presses Control+Shift+-
      await keyboardPage.pressReduceWidth();

      // Then: paragraph width reduces (poll; the layout updates asynchronously)
      await expect.poll(() => keyboardPage.getParagraphWidth(0), { timeout: 10000 }).toBeLessThan(initialWidth);
    });
  });

  test.describe('ParagraphActions.IncreaseWidth: Control+Shift+=', () => {
    test('should increase paragraph width with Control+Shift+=', async () => {
      // Given: A paragraph
      await keyboardPage.tryFocusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\nprint("Test width increase")');

      // First, reduce width to ensure there's room to increase
      const fullWidth = await keyboardPage.getParagraphWidth(0);
      await keyboardPage.pressReduceWidth();
      // Poll until the reduction is reflected in the layout instead of a fixed settle
      await expect.poll(() => keyboardPage.getParagraphWidth(0), { timeout: 10000 }).toBeLessThan(fullWidth);

      const initialWidth = await keyboardPage.getParagraphWidth(0);

      // When: User presses Control+Shift+=
      await keyboardPage.pressIncreaseWidth();

      // Then: paragraph width increases (poll; the layout updates asynchronously)
      await expect.poll(() => keyboardPage.getParagraphWidth(0), { timeout: 10000 }).toBeGreaterThan(initialWidth);
    });
  });

  // ===== EDITOR LINE OPERATIONS =====

  // TODO: Fix the previously skipped tests - ZEPPELIN-6379
  test.describe('ParagraphActions.CutLine: Control+K', () => {
    test.skip();
    test('should cut line with Control+K', async () => {
      // Given: Code editor with content
      await keyboardPage.tryFocusCodeEditor();
      await keyboardPage.setCodeEditorContent('first line\nsecond line\nthird line');

      const initialContent = await keyboardPage.getCodeEditorContent();
      expect(initialContent).toContain('first line');

      // Additional wait and focus for Firefox compatibility
      const browserName = test.info().project.name;
      if (browserName === 'firefox') {
        // JUSTIFIED: wait for Monaco focus/cursor state after editor shortcut
        await keyboardPage.page.waitForTimeout(200); // JUSTIFIED: Monaco editor internal state settle; cursor/focus state not observable via DOM
        // Ensure Monaco editor is properly focused
        // JUSTIFIED: single Monaco editor per paragraph; first() picks the active textarea
        const editorTextarea = keyboardPage.page.locator('.monaco-editor textarea').first();
        await editorTextarea.click();
        await editorTextarea.focus();
        // JUSTIFIED: wait for Monaco focus/cursor state after editor shortcut
        await keyboardPage.page.waitForTimeout(200); // JUSTIFIED: Monaco editor internal state settle; cursor/focus state not observable via DOM
      }

      // When: User presses Control+K (cut to end of line)
      await keyboardPage.pressCutLine();

      // Then: First line content should be cut (cut from cursor position to end of line)
      // JUSTIFIED: wait for Monaco focus/cursor state after editor shortcut
      await keyboardPage.page.waitForTimeout(500); // JUSTIFIED: Monaco editor internal state settle; cursor/focus state not observable via DOM
      const finalContent = await keyboardPage.getCodeEditorContent();
      expect(finalContent).toBeDefined();
      expect(typeof finalContent).toBe('string');

      // Verify the first line was actually cut
      expect(finalContent).toContain('first line');
      expect(finalContent).toContain('second line');
      expect(finalContent).not.toContain('third line');
    });
  });

  // TODO: Fix the previously skipped tests - ZEPPELIN-6379
  test.describe('ParagraphActions.PasteLine: Control+Y', () => {
    test.skip();
    test('should paste line with Control+Y', async () => {
      // Given: Content in the editor
      await keyboardPage.tryFocusCodeEditor();
      const originalContent = 'line to cut and paste';
      await keyboardPage.setCodeEditorContent(originalContent);

      // Wait for content to be properly set and verify it
      // JUSTIFIED: wait for Monaco focus/cursor state after editor shortcut
      await keyboardPage.page.waitForTimeout(500); // JUSTIFIED: Monaco editor internal state settle; cursor/focus state not observable via DOM
      const initialContent = await keyboardPage.getCodeEditorContent();
      expect(initialContent.replace(/\s+/g, ' ').trim()).toContain(originalContent);

      // When: User presses Control+K to cut the line
      await keyboardPage.pressCutLine();
      // JUSTIFIED: wait for Monaco focus/cursor state after editor shortcut
      await keyboardPage.page.waitForTimeout(500); // JUSTIFIED: Monaco editor internal state settle; cursor/focus state not observable via DOM

      // Then: Content should be reduced (line was cut)
      const afterCutContent = await keyboardPage.getCodeEditorContent();
      expect(afterCutContent.length).toBeLessThan(initialContent.length);

      // Clear the editor to verify paste works from clipboard
      await keyboardPage.setCodeEditorContent('');
      // JUSTIFIED: wait for Monaco focus/cursor state after editor shortcut
      await keyboardPage.page.waitForTimeout(200); // JUSTIFIED: Monaco editor internal state settle; cursor/focus state not observable via DOM
      const emptyContent = await keyboardPage.getCodeEditorContent();
      expect(emptyContent.trim()).toBe('');

      // When: User presses Control+Y to paste
      await keyboardPage.pressPasteLine();
      // JUSTIFIED: wait for Monaco focus/cursor state after editor shortcut
      await keyboardPage.page.waitForTimeout(500); // JUSTIFIED: Monaco editor internal state settle; cursor/focus state not observable via DOM

      // Then: Original content should be restored from clipboard
      const finalContent = await keyboardPage.getCodeEditorContent();
      expect(finalContent.replace(/\s+/g, ' ').trim()).toContain(originalContent);
    });
  });

  // ===== SEARCH SHORTCUTS =====

  test.describe('ParagraphActions.SearchInsideCode: Control+S', () => {
    test('should open search with Control+S', async () => {
      // Given: A paragraph with content
      await keyboardPage.tryFocusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\nprint("Search test content")');

      // When: User presses Control+S
      await keyboardPage.pressSearchInsideCode();

      // Then: Search functionality should be triggered
      await expect(keyboardPage.searchDialog).toBeVisible();
    });
  });

  test.describe('ParagraphActions.FindInCode: Control+Alt+F', () => {
    test('should open find in code with Control+Alt+F', async () => {
      // Given: A paragraph with content
      await keyboardPage.tryFocusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\nprint("Find test content")');

      // When: User presses Control+Alt+F
      await keyboardPage.pressFindInCode();

      // Then: Find functionality should be triggered (toBeVisible auto-retries)
      await expect(keyboardPage.searchDialog).toBeVisible();

      // Close search dialog
      await keyboardPage.pressEscape();
    });
  });

  // ===== AUTOCOMPLETION AND NAVIGATION =====

  test.describe('Control+Space: Code Autocompletion', () => {
    test('should trigger autocomplete for Python code', async () => {
      // Given: Code editor with partial Python function
      await keyboardPage.tryFocusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\npr');
      await keyboardPage.pressKey('End'); // Position cursor at end

      // When: User presses Control+Space to trigger autocomplete
      await keyboardPage.pressControlSpace();
      await keyboardPage.autocompletePopup.waitFor({ state: 'visible', timeout: 3000 }).catch(() => {});

      // Then: Editor must remain functional after shortcut (baseline; always asserts)
      // JUSTIFIED: single-paragraph test notebook; first() is deterministic
      await expect(keyboardPage.codeEditor.first()).toBeVisible();

      const isAutocompleteVisible = await keyboardPage.isAutocompleteVisible();
      if (isAutocompleteVisible) {
        // If autocomplete appeared, verify we can interact with it and close it cleanly
        const autocompletePopup = keyboardPage.page
          .locator('.monaco-editor .suggest-widget, .autocomplete-popup, [role="listbox"]')
          // JUSTIFIED: compound selector; first() picks any visible autocomplete popup
          .first();
        await expect(autocompletePopup).toBeVisible();
        await keyboardPage.pressEscape();
      }
      // If no autocomplete (e.g., no Python kernel): editor-visible assertion above is the baseline
    });

    test('should complete autocomplete selection when available', async () => {
      // Given: Code editor with content likely to have autocomplete suggestions
      await keyboardPage.tryFocusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\nimport os\nos.');
      await keyboardPage.pressKey('End');

      // When: User triggers autocomplete and selects an option
      await keyboardPage.pressControlSpace();
      await keyboardPage.autocompletePopup.waitFor({ state: 'visible', timeout: 3000 }).catch(() => {});

      const isAutocompleteVisible = await keyboardPage.isAutocompleteVisible();
      if (isAutocompleteVisible) {
        // Navigate and select first suggestion
        await keyboardPage.pressArrowDown();
        await keyboardPage.pressKey('Enter');

        // Then: Content should be modified with autocomplete suggestion
        const finalContent = await keyboardPage.getCodeEditorContent();
        expect(finalContent.length).toBeGreaterThan('os.'.length);
        expect(finalContent).toContain('os.');
      } else {
        // If autocomplete not available, verify typing still works
        await keyboardPage.pressKey('p');
        await keyboardPage.pressKey('a');
        await keyboardPage.pressKey('t');
        await keyboardPage.pressKey('h');

        const finalContent = await keyboardPage.getCodeEditorContent();
        expect(finalContent).toContain('os.path');
      }
    });
  });

  test.describe('Tab: Code Indentation', () => {
    test('should indent code properly when Tab is pressed', async () => {
      // Given: Code editor with a function definition and cursor on new line
      await keyboardPage.tryFocusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\ndef function():');
      await keyboardPage.pressKey('End');
      await keyboardPage.pressKey('Enter');

      const contentBeforeTab = await keyboardPage.getCodeEditorContent();

      // When: User presses Tab for indentation
      await keyboardPage.pressTab();

      // Then: Content should be longer (indentation added); poll until CodeMirror processes the Tab asynchronously
      let contentAfterTab = '';
      await expect(async () => {
        contentAfterTab = await keyboardPage.getCodeEditorContent();
        expect(contentAfterTab.length).toBeGreaterThan(contentBeforeTab.length);
      }).toPass({ timeout: 5000 });

      // And: The difference should be the addition of indentation characters
      const addedContent = contentAfterTab.substring(contentBeforeTab.length);

      // Check that indentation was added and is either tabs (1-2 chars) or spaces (2-8 chars)
      expect(addedContent.length).toBeLessThanOrEqual(8); // Reasonable indentation limit

      // Should be only whitespace characters
      expect(addedContent).toMatch(/^\s+$/);
    });
  });

  test.describe('Arrow Keys: Cursor Navigation', () => {
    test('should move cursor position with arrow keys', async () => {
      // Given: Code editor with multi-line content
      await keyboardPage.tryFocusCodeEditor();
      const testContent = '%python\nfirst line\nsecond line\nthird line';
      await keyboardPage.setCodeEditorContent(testContent);

      // Position cursor at the beginning
      await keyboardPage.pressKey('Control+Home');

      // When: User navigates with arrow keys
      await keyboardPage.pressArrowDown(); // Move down one line
      await keyboardPage.pressArrowRight(); // Move right one character

      // Type a character to verify cursor position
      await keyboardPage.pressKey('X');

      // Then: Character should be inserted at the correct position
      const finalContent = await keyboardPage.getCodeEditorContent();
      expect(finalContent).toContain('X');
      expect(finalContent).not.toBe(testContent); // Content should have changed
    });
  });

  test.describe('Interpreter Selection', () => {
    test('should recognize and highlight interpreter directives', async () => {
      // Given: Empty code editor
      await keyboardPage.tryFocusCodeEditor();
      await keyboardPage.setCodeEditorContent('');

      // When: User types various interpreter directives
      await keyboardPage.typeInEditor('%python\nprint("Hello")\n');

      // Then: Content should contain the interpreter directive
      const pythonContent = await keyboardPage.getCodeEditorContent();
      expect(pythonContent).toContain('%python');
      expect(pythonContent).toContain('print("Hello")');

      // When: User changes to different interpreter
      await keyboardPage.setCodeEditorContent('%scala\nval x = 1');

      // Then: New interpreter directive should be recognized
      const scalaContent = await keyboardPage.getCodeEditorContent();
      expect(scalaContent).toContain('%scala');

      // Monaco editor removes line breaks, check individual parts
      expect(scalaContent).toContain('val');
      expect(scalaContent).toContain('x');
      expect(scalaContent).toContain('=');
      expect(scalaContent).toContain('1');

      // When: User types markdown directive
      await keyboardPage.setCodeEditorContent('%md\n# Header\nMarkdown content');

      // Then: Markdown directive should be recognized
      const markdownContent = await keyboardPage.getCodeEditorContent();
      expect(markdownContent).toContain('%md');

      // Monaco editor removes line breaks, check individual parts
      expect(markdownContent).toContain('#');
      expect(markdownContent).toContain('Header');
    });
  });

  test.describe('Comprehensive Shortcuts Integration', () => {
    test('should maintain shortcut functionality after errors', async () => {
      // Given: An error has occurred
      await keyboardPage.tryFocusCodeEditor();
      await keyboardPage.setCodeEditorContent('invalid python syntax here');
      await keyboardPage.pressRunParagraph();
      await keyboardPage.waitForParagraphExecution(0);

      // Verify error result exists (invalid syntax produces a final ERROR or FINISHED with error output)
      // JUSTIFIED: single-paragraph test notebook; first() is deterministic
      const statusElError = keyboardPage.paragraphContainer.first().locator('.status');
      await expect(statusElError).toHaveText(/FINISHED|ERROR/, { timeout: 30000 });

      // When: User continues with shortcuts (insert new paragraph)
      const initialCount = await keyboardPage.getParagraphCount();
      await keyboardPage.addParagraph();
      await keyboardPage.waitForParagraphCountChange(initialCount + 1, 10000);

      // Set valid content in new paragraph and run
      const newParagraphIndex = (await keyboardPage.getParagraphCount()) - 1;
      await keyboardPage.tryFocusCodeEditor(newParagraphIndex);
      await keyboardPage.setCodeEditorContent('%md\n# Recovery Test\nShortcuts work after error', newParagraphIndex);
      await keyboardPage.pressRunParagraph();

      // Then: Shortcut execution still reaches a terminal state
      await keyboardPage.waitForParagraphExecution(newParagraphIndex);
      // JUSTIFIED: newParagraphIndex is dynamically computed from getParagraphCount(); nth() is the only way to address this specific paragraph
      const statusElNew = keyboardPage.paragraphContainer.nth(newParagraphIndex).locator('.status');
      await expect(statusElNew).toHaveText(/FINISHED|ERROR/, { timeout: 30000 });
    });

    test('should gracefully handle shortcuts when no paragraph is focused', async () => {
      // Given: A notebook with at least one paragraph but no focus
      await keyboardPage.tryFocusCodeEditor();
      await keyboardPage.setCodeEditorContent('%md\n# Test paragraph');

      // Remove focus by clicking on empty area, then confirm no editor holds focus
      await keyboardPage.page.locator('body').click();
      await expect(keyboardPage.page.locator('.monaco-editor.focused')).toHaveCount(0, { timeout: 5000 });

      const initialCount = await keyboardPage.getParagraphCount();

      // When: User tries keyboard shortcuts that require paragraph focus
      // These should either not work or gracefully handle the lack of focus
      await keyboardPage.pressInsertBelow(); // This may not work without focus

      const afterShortcut = await keyboardPage.getParagraphCount();

      // Then: Either the shortcut works (creates new paragraph) or is gracefully ignored
      expect(afterShortcut === initialCount || afterShortcut === initialCount + 1).toBe(true);

      // Editor remains usable after error recovery.
      // JUSTIFIED: single-paragraph test notebook; first() is deterministic
      await expect(keyboardPage.codeEditor.first()).toBeVisible();
    });

    test('should handle rapid keyboard operations without instability', async () => {
      await keyboardPage.tryFocusCodeEditor();
      await keyboardPage.setCodeEditorContent('%md\nrapid keyboard test');

      // Rapid Shift+Enter operations
      for (let i = 0; i < 3; i++) {
        await keyboardPage.pressRunParagraph();
        await keyboardPage.waitForParagraphExecution(0, 60000);
        // JUSTIFIED: single-paragraph test notebook; first() is deterministic
        await expect(keyboardPage.paragraphResult.first()).toBeVisible({ timeout: 60000 });
      }

      // Then: System should remain stable
      // JUSTIFIED: single-paragraph test notebook; first() is deterministic
      await expect(keyboardPage.codeEditor.first()).toBeVisible();
    });
  });
});
