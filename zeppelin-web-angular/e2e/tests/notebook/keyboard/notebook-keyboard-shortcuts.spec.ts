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
import { NotebookKeyboardPageUtil } from 'e2e/models/notebook-keyboard-page.util';
import {
  addPageAnnotationBeforeEach,
  performLoginIfRequired,
  waitForNotebookLinks,
  waitForZeppelinReady,
  PAGES,
  createTestNotebook
} from '../../../utils';

/**
 * Comprehensive keyboard shortcuts test suite based on ShortcutsMap
 * Tests all keyboard shortcuts defined in src/app/key-binding/shortcuts-map.ts
 */
test.describe.serial('Comprehensive Keyboard Shortcuts (ShortcutsMap)', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK);
  addPageAnnotationBeforeEach(PAGES.SHARE.SHORTCUT);

  let keyboardPage: NotebookKeyboardPage;
  let testUtil: NotebookKeyboardPageUtil;
  let testNotebook: { noteId: string; paragraphId: string };

  test.beforeEach(async ({ page }) => {
    keyboardPage = new NotebookKeyboardPage(page);
    testUtil = new NotebookKeyboardPageUtil(page);

    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
    await waitForNotebookLinks(page);

    // Handle the welcome modal if it appears
    const welcomeModal = page.locator('.ant-modal-root', { hasText: 'Welcome to Zeppelin!' });
    if ((await welcomeModal.count()) > 0) {
      const cancelButton = welcomeModal.locator('button', { hasText: 'Cancel' });
      await cancelButton.click();
      await welcomeModal.waitFor({ state: 'hidden', timeout: 5000 });
    }

    // Simple notebook creation without excessive waiting
    testNotebook = await createTestNotebook(page);
    await testUtil.prepareNotebookForKeyboardTesting(testNotebook.noteId);
  });

  test.afterEach(async ({ page }) => {
    // Clean up any open dialogs or modals
    await page.keyboard.press('Escape');
  });

  // ===== CORE EXECUTION SHORTCUTS =====

  test.describe('ParagraphActions.Run: Shift+Enter', () => {
    test('should execute markdown paragraph with Shift+Enter', async () => {
      // Given: A paragraph with markdown content
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%md\n# Test Heading\n\nThis is **bold** text.');

      // Verify content was set
      const content = await keyboardPage.getCodeEditorContent();
      expect(content.replace(/\s+/g, '')).toContain('#TestHeading');

      // When: User presses Shift+Enter
      await keyboardPage.pressRunParagraph();

      // Then: Paragraph should execute and show result
      await keyboardPage.waitForParagraphExecution(0);
      const hasResult = await keyboardPage.isParagraphResultSettled(0);
      expect(hasResult).toBe(true);
    });
  });

  // TODO: Fix the previously skipped tests - ZEPPELIN-6379
  test.describe('ParagraphActions.RunAbove: Control+Shift+ArrowUp', () => {
    test.skip();
    test('should run all paragraphs above current with Control+Shift+ArrowUp', async () => {
      // Given: Multiple paragraphs
      await keyboardPage.focusCodeEditor(0);
      await keyboardPage.setCodeEditorContent('%md\n# First Paragraph\nTest content for run above', 0);
      await keyboardPage.addParagraph();
      await keyboardPage.waitForParagraphCountChange(2);

      // Focus on second paragraph
      await keyboardPage.focusCodeEditor(1);
      await keyboardPage.setCodeEditorContent('%md\n# Second Paragraph\nTest content for second paragraph', 1);
      await keyboardPage.focusCodeEditor(1); // Ensure focus on the second paragraph

      // Add an explicit wait for the page to be completely stable and the notebook UI to be interactive
      await keyboardPage.page.waitForLoadState('networkidle', { timeout: 30000 }); // Wait for network to be idle
      await expect(keyboardPage.paragraphContainer.first()).toBeVisible({ timeout: 15000 }); // Ensure a paragraph is visible

      // When: User presses Control+Shift+ArrowUp from second paragraph
      await keyboardPage.pressRunAbove();

      await keyboardPage.clickModalOkButton();

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
      await keyboardPage.focusCodeEditor(0);
      await keyboardPage.setCodeEditorContent('%md\n# First Paragraph\nContent for run below test', 0);
      await keyboardPage.addParagraph();
      await keyboardPage.waitForParagraphCountChange(2);

      // Add content to second paragraph
      await keyboardPage.focusCodeEditor(1);
      await keyboardPage.setCodeEditorContent('%md\n# Second Paragraph\nContent for run below test', 1);

      // Focus first paragraph
      await keyboardPage.focusCodeEditor(0);

      // When: User presses Control+Shift+ArrowDown
      await keyboardPage.pressRunBelow();

      // Confirmation modal must appear when running paragraphs
      await keyboardPage.clickModalOkButton();

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
      // Given: A long-running paragraph
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\nimport time;time.sleep(3)\nprint("Should be cancelled")');

      // Start execution
      await keyboardPage.pressRunParagraph();

      // Wait for execution to start by checking if paragraph is running
      await keyboardPage.page.waitForTimeout(1000);

      // When: User presses Control+Alt+C quickly
      await keyboardPage.pressCancel();

      // Then: The execution should be cancelled or completed
      const isParagraphRunning = await keyboardPage.isParagraphRunning(0);
      expect(isParagraphRunning).toBe(false);
    });
  });

  // ===== CURSOR MOVEMENT SHORTCUTS =====

  test.describe('ParagraphActions.MoveCursorUp: Control+P', () => {
    test('should move cursor up with Control+P', async () => {
      // Given: A paragraph with multiple lines
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\nline1\nline2\nline3');

      // Position cursor at end of last line using more reliable cross-browser method
      await keyboardPage.pressSelectAll(); // Select all content
      await keyboardPage.pressKey('ArrowRight'); // Move to end
      await keyboardPage.page.waitForTimeout(500); // Wait for cursor to position

      // When: User presses Control+P (should move cursor up one line)
      await keyboardPage.pressMoveCursorUp();
      await keyboardPage.page.waitForTimeout(500); // Wait for cursor movement

      // Then: Verify cursor movement by checking if we can type at the current position
      // Type a marker and check where it appears in the content
      await keyboardPage.pressKey('End'); // Move to end of current line
      await keyboardPage.page.keyboard.type('MARKER');

      const content = await keyboardPage.getCodeEditorContent();
      // If cursor moved up correctly, marker should be on line2
      expect(content).toContain('line2MARKER');
      expect(content).not.toContain('line3MARKER');
    });
  });

  test.describe('ParagraphActions.MoveCursorDown: Control+N', () => {
    test('should move cursor down with Control+N', async () => {
      // Given: A paragraph with multiple lines
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\nline1\nline2\nline3');

      // Position cursor at beginning of first content line (after %python) using more reliable method
      await keyboardPage.pressSelectAll(); // Select all content
      await keyboardPage.pressKey('ArrowLeft'); // Move to beginning
      await keyboardPage.pressKey('ArrowDown'); // Move to line1
      await keyboardPage.page.waitForTimeout(500); // Wait for cursor to position

      // When: User presses Control+N (should move cursor down one line)
      await keyboardPage.pressMoveCursorDown();
      await keyboardPage.page.waitForTimeout(500); // Wait for cursor movement

      // Then: Verify cursor movement by checking if we can type at the current position
      // Type a marker and check where it appears in the content
      await keyboardPage.page.keyboard.type('MARKER');

      const content = await keyboardPage.getCodeEditorContent();
      // If cursor moved down correctly, marker should be on line2
      expect(content).toContain('MARKERline2');
      expect(content).not.toContain('MARKERline1');
    });
  });

  // ===== PARAGRAPH MANIPULATION SHORTCUTS =====

  test.describe('ParagraphActions.Delete: Control+Alt+D', () => {
    test('should delete current paragraph with Control+Alt+D', async () => {
      // Wait for notebook to fully load
      await keyboardPage.focusCodeEditor(0);
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
      await keyboardPage.focusCodeEditor(0);
      await keyboardPage.page.waitForTimeout(1000); // Wait for focus

      // When: User presses Control+Alt+D
      await keyboardPage.pressDeleteParagraph();

      // Handle confirmation modal if it appears
      const confirmButton = keyboardPage.page
        .locator(
          'button:has-text("OK"), button:has-text("Yes"), button:has-text("Delete"), button:has-text("Confirm"), .ant-btn-primary'
        )
        .first();
      await confirmButton.waitFor({ state: 'visible', timeout: 2000 });
      await confirmButton.click();

      // Wait for deletion to process
      await keyboardPage.page.waitForTimeout(1000);

      // Then: Paragraph count should decrease
      const finalCount = await keyboardPage.getParagraphCount();
      expect(finalCount).toEqual(currentCount - 1);
    });
  });

  test.describe('ParagraphActions.InsertAbove: Control+Alt+A', () => {
    test('should insert paragraph above with Control+Alt+A', async () => {
      // Given: A single paragraph with content
      await keyboardPage.focusCodeEditor();
      const originalContent = '%python\n# Original Paragraph\nprint("Content for insert above test")';
      await keyboardPage.setCodeEditorContent(originalContent);

      const initialCount = await keyboardPage.getParagraphCount();

      await keyboardPage.focusCodeEditor(0);

      // When: User presses Control+Alt+A
      await keyboardPage.pressInsertAbove();

      // Then: A new paragraph should be inserted above
      await keyboardPage.waitForParagraphCountChange(initialCount + 1);
      const finalCount = await keyboardPage.getParagraphCount();
      expect(finalCount).toBe(initialCount + 1);

      // And: The new paragraph should be at index 0 (above the original)
      const newParagraphContent = await keyboardPage.getCodeEditorContentByIndex(0);
      const originalParagraphContent = await keyboardPage.getCodeEditorContentByIndex(1);

      expect(newParagraphContent).toBe(''); // New paragraph should be empty
      expect(originalParagraphContent).toContain(originalContent); // Original content should be at index 1
    });
  });

  test.describe('ParagraphActions.InsertBelow: Control+Alt+B', () => {
    test('should insert paragraph below with Control+Alt+B', async () => {
      // Given: A single paragraph with content
      await keyboardPage.focusCodeEditor();
      const originalContent = '%md\n# Original Paragraph\nContent for insert below test';
      await keyboardPage.setCodeEditorContent(originalContent);

      const initialCount = await keyboardPage.getParagraphCount();

      // When: User presses Control+Alt+B
      await keyboardPage.addParagraph();

      // Then: A new paragraph should be inserted below
      await keyboardPage.waitForParagraphCountChange(initialCount + 1);
      const finalCount = await keyboardPage.getParagraphCount();
      expect(finalCount).toBe(initialCount + 1);

      // And: The new paragraph should be at index 1 (below the original)
      const originalParagraphContent = await keyboardPage.getCodeEditorContentByIndex(0);
      const newParagraphContent = await keyboardPage.getCodeEditorContentByIndex(1);

      expect(originalParagraphContent).toBe(originalContent); // Original content should remain at index 0
      expect(newParagraphContent).toBe(''); // New paragraph should be empty at index 1
    });
  });

  // Note (ZEPPELIN-6294):
  // This test appears to be related to ZEPPELIN-6294.
  // A proper fix or verification should be added based on the issue details.
  // In the New UI, the cloned paragraph’s text is empty on PARAGRAPH_ADDED,
  // while the Classic UI receives the correct text. This discrepancy should be addressed
  // when applying the proper fix for the issue.
  test.describe('ParagraphActions.InsertCopyOfParagraphBelow: Control+Shift+C', () => {
    test('should insert copy of paragraph below with Control+Shift+C', async () => {
      test.skip();
      // Given: A paragraph with content
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%md\n# Copy Test\nContent to be copied below');

      const initialCount = await keyboardPage.getParagraphCount();

      // Capture the original paragraph content to verify the copy
      const originalContent = await keyboardPage.getCodeEditorContentByIndex(0);

      // When: User presses Control+Shift+C
      await keyboardPage.pressInsertCopy();

      // Then: A copy of the paragraph should be inserted below
      await keyboardPage.waitForParagraphCountChange(initialCount + 1);
      const finalCount = await keyboardPage.getParagraphCount();
      expect(finalCount).toBe(initialCount + 1);

      // And: The copied content should be identical to the original
      const originalParagraphContent = await keyboardPage.getCodeEditorContentByIndex(0);
      const copiedParagraphContent = await keyboardPage.getCodeEditorContentByIndex(1);

      expect(originalParagraphContent).toBe(originalContent); // Original should remain unchanged
      expect(copiedParagraphContent).toBe(originalContent); // Copied content should match original exactly
    });
  });

  test.describe('ParagraphActions.MoveParagraphUp: Control+Alt+K', () => {
    test('should move paragraph up with Control+Alt+K', async () => {
      // Given: Create two paragraphs using keyboard shortcut
      const firstContent = '%python\nprint("First Paragraph - Content for move up test")';
      const secondContent = '%python\nprint("Second Paragraph - This should move up")';

      // Set first paragraph content
      await keyboardPage.focusCodeEditor(0);
      await keyboardPage.setCodeEditorContent(firstContent, 0);
      await keyboardPage.page.waitForTimeout(300);

      // Create second paragraph using InsertBelow shortcut (Control+Alt+B)
      await keyboardPage.pressInsertBelow();
      await keyboardPage.page.waitForTimeout(1000);

      // Set second paragraph content
      await keyboardPage.focusCodeEditor(1);
      await keyboardPage.setCodeEditorContent(secondContent, 1);
      await keyboardPage.page.waitForTimeout(300);

      // Verify we have 2 paragraphs
      const paragraphCount = await keyboardPage.getParagraphCount();
      expect(paragraphCount).toBe(2);

      // Verify initial content before move
      const initialFirst = await keyboardPage.getCodeEditorContentByIndex(0);
      const initialSecond = await keyboardPage.getCodeEditorContentByIndex(1);

      // Focus on second paragraph for move operation
      await keyboardPage.focusCodeEditor(1);
      await keyboardPage.page.waitForTimeout(200);

      // When: User presses Control+Alt+K from second paragraph
      await keyboardPage.pressMoveParagraphUp();

      // Wait for move operation to complete
      await keyboardPage.page.waitForTimeout(1000);

      // Then: Paragraph count should remain the same
      const finalParagraphCount = await keyboardPage.getParagraphCount();
      expect(finalParagraphCount).toBe(2);

      // And: Paragraph positions should be swapped
      const newFirstParagraph = await keyboardPage.getCodeEditorContentByIndex(0);
      const newSecondParagraph = await keyboardPage.getCodeEditorContentByIndex(1);

      expect(newFirstParagraph).toBe(initialSecond); // Second paragraph moved to first position
      expect(newSecondParagraph).toBe(initialFirst); // First paragraph moved to second position
    });
  });

  test.describe('ParagraphActions.MoveParagraphDown: Control+Alt+J', () => {
    test('should move paragraph down with Control+Alt+J', async () => {
      // Given: Create two paragraphs using keyboard shortcut instead of addParagraph()
      const firstContent = '%python\nprint("First Paragraph - This should move down")';
      const secondContent = '%python\nprint("Second Paragraph - Content for second paragraph")';

      // Set first paragraph content
      await keyboardPage.focusCodeEditor(0);
      await keyboardPage.setCodeEditorContent(firstContent, 0);
      await keyboardPage.page.waitForTimeout(300);

      // Create second paragraph using InsertBelow shortcut (Control+Alt+B)
      await keyboardPage.pressInsertBelow();
      await keyboardPage.page.waitForTimeout(1000);

      // Set second paragraph content
      await keyboardPage.focusCodeEditor(1);
      await keyboardPage.setCodeEditorContent(secondContent, 1);
      await keyboardPage.page.waitForTimeout(300);

      // Verify we have 2 paragraphs
      const paragraphCount = await keyboardPage.getParagraphCount();
      expect(paragraphCount).toBe(2);

      // Verify initial content before move
      const initialFirst = await keyboardPage.getCodeEditorContentByIndex(0);
      const initialSecond = await keyboardPage.getCodeEditorContentByIndex(1);

      // Focus first paragraph for move operation
      await keyboardPage.focusCodeEditor(0);
      await keyboardPage.page.waitForTimeout(200);

      // When: User presses Control+Alt+J from first paragraph
      await keyboardPage.pressMoveParagraphDown();

      // Wait for move operation to complete
      await keyboardPage.page.waitForTimeout(1000);

      // Then: Paragraph count should remain the same
      const finalParagraphCount = await keyboardPage.getParagraphCount();
      expect(finalParagraphCount).toBe(2);

      // And: Paragraph positions should be swapped
      const newFirstParagraph = await keyboardPage.getCodeEditorContentByIndex(0);
      const newSecondParagraph = await keyboardPage.getCodeEditorContentByIndex(1);

      expect(newFirstParagraph).toBe(initialSecond); // Second paragraph moved to first position
      expect(newSecondParagraph).toBe(initialFirst); // First paragraph moved to second position
    });
  });

  // ===== UI TOGGLE SHORTCUTS =====

  test.describe('ParagraphActions.SwitchEditor: Control+Alt+E', () => {
    test('should toggle editor visibility with Control+Alt+E', async () => {
      // Given: A paragraph with visible editor
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\nprint("Test editor toggle")');

      const initialEditorVisibility = await keyboardPage.isEditorVisible(0);

      // When: User presses Control+Alt+E
      await keyboardPage.pressSwitchEditor();

      // Then: Editor visibility should toggle
      await keyboardPage.page.waitForTimeout(500);
      const finalEditorVisibility = await keyboardPage.isEditorVisible(0);
      expect(finalEditorVisibility).not.toBe(initialEditorVisibility);
    });
  });

  test.describe('ParagraphActions.SwitchEnable: Control+Alt+R', () => {
    test('should toggle paragraph enable/disable with Control+Alt+R', async () => {
      // Given: An enabled paragraph
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\nprint("Test enable toggle")');

      const initialEnabledState = await keyboardPage.isParagraphEnabled(0);

      // When: User presses Control+Alt+R
      await keyboardPage.pressSwitchEnable();

      // Then: Paragraph enabled state should toggle
      await keyboardPage.page.waitForTimeout(1000);
      const finalEnabledState = await keyboardPage.isParagraphEnabled(0);
      expect(finalEnabledState).not.toBe(initialEnabledState);
    });
  });

  test.describe('ParagraphActions.SwitchOutputShow: Control+Alt+O', () => {
    test('should toggle output visibility with Control+Alt+O', async () => {
      // Given: A paragraph with output
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%md\n# Test Output Toggle\nThis creates immediate output');
      await keyboardPage.pressRunParagraph();
      await keyboardPage.waitForParagraphExecution(0);

      const resultLocator = keyboardPage.getParagraphByIndex(0).locator('[data-testid="paragraph-result"]');
      await expect(resultLocator).toBeVisible();

      const initialOutputVisibility = await keyboardPage.isOutputVisible(0);

      // When: User presses Control+Alt+O
      await keyboardPage.focusCodeEditor(0);
      await keyboardPage.pressSwitchOutputShow();

      const finalOutputVisibility = await keyboardPage.isOutputVisible(0);
      expect(finalOutputVisibility).not.toBe(initialOutputVisibility);
    });
  });

  test.describe('ParagraphActions.SwitchLineNumber: Control+Alt+M', () => {
    test('should toggle line numbers with Control+Alt+M', async () => {
      // Given: A paragraph with code
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\nprint("Test line numbers")');

      const initialLineNumbersVisibility = await keyboardPage.areLineNumbersVisible(0);

      // When: User presses Control+Alt+M
      await keyboardPage.pressSwitchLineNumber();

      // Then: Line numbers visibility should toggle
      await keyboardPage.page.waitForTimeout(500);
      const finalLineNumbersVisibility = await keyboardPage.areLineNumbersVisible(0);
      expect(finalLineNumbersVisibility).not.toBe(initialLineNumbersVisibility);
    });
  });

  test.describe('ParagraphActions.SwitchTitleShow: Control+Alt+T', () => {
    test('should toggle title visibility with Control+Alt+T', async () => {
      // Given: A paragraph
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\nprint("Test title toggle")');

      const initialTitleVisibility = await keyboardPage.isTitleVisible(0);

      // When: User presses Control+Alt+T
      await keyboardPage.pressSwitchTitleShow();

      // Then: Title visibility should toggle
      const finalTitleVisibility = await keyboardPage.isTitleVisible(0);
      expect(finalTitleVisibility).not.toBe(initialTitleVisibility);
    });
  });

  test.describe('ParagraphActions.Clear: Control+Alt+L', () => {
    test('should clear output with Control+Alt+L', async () => {
      // Given: A paragraph with executed content that has output
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%md\n# Test Content\nFor clear output test');
      await keyboardPage.pressRunParagraph();
      await keyboardPage.waitForParagraphExecution(0);

      // Verify there is output to clear
      const hasResult = await keyboardPage.isParagraphResultSettled(0);
      expect(hasResult).toBe(true);

      // When: User presses Control+Alt+L
      await keyboardPage.focusCodeEditor(0);
      await keyboardPage.pressClearOutput();

      // Then: Output should be cleared
      const resultLocator = keyboardPage.getParagraphByIndex(0).locator('[data-testid="paragraph-result"]');
      await expect(resultLocator).not.toBeVisible();
    });
  });

  test.describe('ParagraphActions.Link: Control+Alt+W', () => {
    test('should trigger link paragraph with Control+Alt+W', async () => {
      // Given: A paragraph with content
      await keyboardPage.focusCodeEditor();
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
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\nprint("Test width reduction")');

      const initialWidth = await keyboardPage.getParagraphWidth(0);

      // When: User presses Control+Shift+-
      await keyboardPage.pressReduceWidth();

      // Then: Paragraph width should be reduced
      const finalWidth = await keyboardPage.getParagraphWidth(0);
      expect(finalWidth).toBeLessThan(initialWidth);
    });
  });

  test.describe('ParagraphActions.IncreaseWidth: Control+Shift+=', () => {
    test('should increase paragraph width with Control+Shift+=', async () => {
      // Given: A paragraph
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\nprint("Test width increase")');

      // First, reduce width to ensure there's room to increase
      await keyboardPage.pressReduceWidth();
      await keyboardPage.page.waitForTimeout(500); // Give UI a moment to update after reduction

      const initialWidth = await keyboardPage.getParagraphWidth(0);

      // When: User presses Control+Shift+=
      await keyboardPage.pressIncreaseWidth();

      // Then: Paragraph width should be increased
      const finalWidth = await keyboardPage.getParagraphWidth(0);
      expect(finalWidth).toBeGreaterThan(initialWidth);
    });
  });

  // ===== EDITOR LINE OPERATIONS =====

  // TODO: Fix the previously skipped tests - ZEPPELIN-6379
  test.describe('ParagraphActions.CutLine: Control+K', () => {
    test.skip();
    test('should cut line with Control+K', async () => {
      // Given: Code editor with content
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('first line\nsecond line\nthird line');

      const initialContent = await keyboardPage.getCodeEditorContent();
      expect(initialContent).toContain('first line');

      // Additional wait and focus for Firefox compatibility
      const browserName = test.info().project.name;
      if (browserName === 'firefox') {
        await keyboardPage.page.waitForTimeout(200);
        // Ensure Monaco editor is properly focused
        const editorTextarea = keyboardPage.page.locator('.monaco-editor textarea').first();
        await editorTextarea.click();
        await editorTextarea.focus();
        await keyboardPage.page.waitForTimeout(200);
      }

      // When: User presses Control+K (cut to end of line)
      await keyboardPage.pressCutLine();

      // Then: First line content should be cut (cut from cursor position to end of line)
      await keyboardPage.page.waitForTimeout(500);
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
      await keyboardPage.focusCodeEditor();
      const originalContent = 'line to cut and paste';
      await keyboardPage.setCodeEditorContent(originalContent);

      // Wait for content to be properly set and verify it
      await keyboardPage.page.waitForTimeout(500);
      const initialContent = await keyboardPage.getCodeEditorContent();
      expect(initialContent.replace(/\s+/g, ' ').trim()).toContain(originalContent);

      // When: User presses Control+K to cut the line
      await keyboardPage.pressCutLine();
      await keyboardPage.page.waitForTimeout(500);

      // Then: Content should be reduced (line was cut)
      const afterCutContent = await keyboardPage.getCodeEditorContent();
      expect(afterCutContent.length).toBeLessThan(initialContent.length);

      // Clear the editor to verify paste works from clipboard
      await keyboardPage.setCodeEditorContent('');
      await keyboardPage.page.waitForTimeout(200);
      const emptyContent = await keyboardPage.getCodeEditorContent();
      expect(emptyContent.trim()).toBe('');

      // When: User presses Control+Y to paste
      await keyboardPage.pressPasteLine();
      await keyboardPage.page.waitForTimeout(500);

      // Then: Original content should be restored from clipboard
      const finalContent = await keyboardPage.getCodeEditorContent();
      expect(finalContent.replace(/\s+/g, ' ').trim()).toContain(originalContent);
    });
  });

  // ===== SEARCH SHORTCUTS =====

  test.describe('ParagraphActions.SearchInsideCode: Control+S', () => {
    test('should open search with Control+S', async () => {
      // Given: A paragraph with content
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\nprint("Search test content")');

      // When: User presses Control+S
      await keyboardPage.pressSearchInsideCode();

      // Then: Search functionality should be triggered
      const isSearchVisible = await keyboardPage.isSearchDialogVisible();
      expect(isSearchVisible).toBe(true);
    });
  });

  test.describe('ParagraphActions.FindInCode: Control+Alt+F', () => {
    test('should open find in code with Control+Alt+F', async () => {
      // Given: A paragraph with content
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\nprint("Find test content")');

      // When: User presses Control+Alt+F
      await keyboardPage.pressFindInCode();

      // Then: Find functionality should be triggered
      await keyboardPage.page.waitForTimeout(1000);
      const isSearchVisible = await keyboardPage.isSearchDialogVisible();
      expect(isSearchVisible).toBe(true);

      // Close search dialog
      if (isSearchVisible) {
        await keyboardPage.pressEscape();
      }
    });
  });

  // ===== AUTOCOMPLETION AND NAVIGATION =====

  test.describe('Control+Space: Code Autocompletion', () => {
    test('should trigger autocomplete for Python code', async () => {
      // Given: Code editor with partial Python function
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\npr');
      await keyboardPage.pressKey('End'); // Position cursor at end

      // When: User presses Control+Space to trigger autocomplete
      await keyboardPage.pressControlSpace();
      await keyboardPage.page.waitForTimeout(1000);

      // Then: Either autocomplete appears OR system handles it gracefully
      const isAutocompleteVisible = await keyboardPage.isAutocompleteVisible();
      if (isAutocompleteVisible) {
        // If autocomplete is visible, verify we can interact with it
        const autocompletePopup = keyboardPage.page
          .locator('.monaco-editor .suggest-widget, .autocomplete-popup, [role="listbox"]')
          .first();
        await expect(autocompletePopup).toBeVisible();

        // Close autocomplete cleanly
        await keyboardPage.pressEscape();
      } else {
        // If no autocomplete (e.g., no Python kernel), verify editor still works
        await keyboardPage.setCodeEditorContent('%python\nprint("test")');
        const content = await keyboardPage.getCodeEditorContent();
        expect(content).toContain('print');
      }
    });

    test('should complete autocomplete selection when available', async () => {
      // Given: Code editor with content likely to have autocomplete suggestions
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\nimport os\nos.');
      await keyboardPage.pressKey('End');

      // When: User triggers autocomplete and selects an option
      await keyboardPage.pressControlSpace();
      await keyboardPage.page.waitForTimeout(1000);

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
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\ndef function():');
      await keyboardPage.pressKey('End');
      await keyboardPage.pressKey('Enter');

      const contentBeforeTab = await keyboardPage.getCodeEditorContent();

      // When: User presses Tab for indentation
      await keyboardPage.pressTab();

      // Then: Content should be longer (indentation added)
      const contentAfterTab = await keyboardPage.getCodeEditorContent();
      expect(contentAfterTab.length).toBeGreaterThan(contentBeforeTab.length);

      // And: The difference should be the addition of indentation characters
      const addedContent = contentAfterTab.substring(contentBeforeTab.length);
      expect(addedContent).toMatch(/^[\t ]+$/); // Should be only tabs or spaces
      expect(addedContent.length).toBeGreaterThan(0); // Should have added some indentation

      // Verify the last line has indentation at the beginning
      const lines = contentAfterTab.split('\n');
      const lastLine = lines[lines.length - 1];
      expect(lastLine).toMatch(/^[\t ]+/); // Last line should start with indentation
    });
  });

  test.describe('Arrow Keys: Cursor Navigation', () => {
    test('should move cursor position with arrow keys', async () => {
      // Given: Code editor with multi-line content
      await keyboardPage.focusCodeEditor();
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

      // The 'X' should appear somewhere in the content (exact position may vary by editor)
      const lines = finalContent.split('\n');
      expect(lines.length).toBeGreaterThanOrEqual(3); // Should still have multiple lines
    });
  });

  test.describe('Interpreter Selection', () => {
    test('should recognize and highlight interpreter directives', async () => {
      // Given: Empty code editor
      await keyboardPage.focusCodeEditor();
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
      expect(scalaContent).toContain('val x = 1');

      // When: User types markdown directive
      await keyboardPage.setCodeEditorContent('%md\n# Header\nMarkdown content');

      // Then: Markdown directive should be recognized
      const markdownContent = await keyboardPage.getCodeEditorContent();
      expect(markdownContent).toContain('%md');
      expect(markdownContent).toContain('# Header');
    });
  });

  test.describe('Comprehensive Shortcuts Integration', () => {
    test('should maintain shortcut functionality after errors', async () => {
      // Given: An error has occurred
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('invalid python syntax here');
      await keyboardPage.pressRunParagraph();
      await keyboardPage.waitForParagraphExecution(0);

      // Verify error result exists
      const hasErrorResult = await keyboardPage.isParagraphResultSettled(0);
      expect(hasErrorResult).toBe(true);

      // When: User continues with shortcuts (insert new paragraph)
      const initialCount = await keyboardPage.getParagraphCount();
      await keyboardPage.addParagraph();
      await keyboardPage.waitForParagraphCountChange(initialCount + 1, 10000);

      // Set valid content in new paragraph and run
      const newParagraphIndex = (await keyboardPage.getParagraphCount()) - 1;
      await keyboardPage.focusCodeEditor(newParagraphIndex);
      await keyboardPage.setCodeEditorContent('%md\n# Recovery Test\nShortcuts work after error', newParagraphIndex);
      await keyboardPage.pressRunParagraph();

      // Then: New paragraph should execute successfully
      await keyboardPage.waitForParagraphExecution(newParagraphIndex);
      const hasResult = await keyboardPage.isParagraphResultSettled(newParagraphIndex);
      expect(hasResult).toBe(true);
    });

    test('should gracefully handle shortcuts when no paragraph is focused', async () => {
      // Given: A notebook with at least one paragraph but no focus
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%md\n# Test paragraph');

      // Remove focus by clicking on empty area
      await keyboardPage.page.click('body');
      await keyboardPage.page.waitForTimeout(500);

      const initialCount = await keyboardPage.getParagraphCount();

      // When: User tries keyboard shortcuts that require paragraph focus
      // These should either not work or gracefully handle the lack of focus
      try {
        await keyboardPage.pressInsertBelow(); // This may not work without focus
        await keyboardPage.page.waitForTimeout(1000);

        const afterShortcut = await keyboardPage.getParagraphCount();

        // Then: Either the shortcut works (creates new paragraph) or is gracefully ignored
        if (afterShortcut > initialCount) {
          // Shortcut worked despite no focus - this is acceptable behavior
          expect(afterShortcut).toBe(initialCount + 1);
        } else {
          // Shortcut was ignored - this is also acceptable behavior
          expect(afterShortcut).toBe(initialCount);
        }
      } catch (error) {
        // If shortcut throws an error, verify the system remains stable
        const finalCount = await keyboardPage.getParagraphCount();
        expect(finalCount).toBe(initialCount);

        // Verify the notebook is still functional
        await keyboardPage.focusCodeEditor(0);
        const content = await keyboardPage.getCodeEditorContent();
        expect(content).toContain('Test paragraph');
      }
    });

    test('should handle rapid keyboard operations without instability', async () => {
      // Given: User performs rapid keyboard operations
      await testUtil.verifyRapidKeyboardOperations();

      // Then: System should remain stable
      const isEditorVisible = await keyboardPage.codeEditor.first().isVisible();
      expect(isEditorVisible).toBe(true);
    });
  });
});
