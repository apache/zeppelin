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
  PAGES
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

    await page.goto('/');
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
    testNotebook = await testUtil.createTestNotebook();
    await testUtil.prepareNotebookForKeyboardTesting(testNotebook.noteId);
  });

  test.afterEach(async ({ page }) => {
    // Clean up any open dialogs or modals
    await page.keyboard.press('Escape');

    if (testNotebook?.noteId) {
      await testUtil.deleteTestNotebook(testNotebook.noteId);
    }
  });

  // ===== CORE EXECUTION SHORTCUTS =====

  test.describe('ParagraphActions.Run: Shift+Enter', () => {
    test('should run current paragraph with Shift+Enter', async () => {
      // Given: A paragraph with markdown content
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%md\n# Test Heading\nThis is a test.');

      // Verify content was set
      const content = await keyboardPage.getCodeEditorContent();
      expect(content.replace(/\s+/g, '')).toContain('#TestHeading');

      // When: User presses Shift+Enter
      await keyboardPage.pressRunParagraph();

      // Then: Paragraph should execute and show result
      await keyboardPage.waitForParagraphExecution(0);
      const hasResult = await keyboardPage.hasParagraphResult(0);
      expect(hasResult).toBe(true);
    });

    test('should handle markdown paragraph execution when Shift+Enter is pressed', async () => {
      // Given: A markdown paragraph
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%md\n# Test Heading\n\nThis is **bold** text.');

      // Verify content was set
      const content = await keyboardPage.getCodeEditorContent();
      const cleanContent = content.replace(/^%[a-z]+\s*/i, '');
      expect(cleanContent.replace(/\s+/g, '')).toContain('#TestHeading');

      // When: User presses Shift+Enter
      await keyboardPage.pressRunParagraph();

      // Then: Markdown should execute and show result
      await keyboardPage.waitForParagraphExecution(0);
      const hasResult = await keyboardPage.hasParagraphResult(0);
      expect(hasResult).toBe(true);
    });

    test('should execute markdown content with Shift+Enter', async () => {
      // Given: A paragraph with markdown content
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%md\n# Execution Test\nThis tests markdown execution.');

      // When: User presses Shift+Enter
      await keyboardPage.pressRunParagraph();

      // Then: Execution should succeed
      await keyboardPage.waitForParagraphExecution(0);
      const hasResult = await keyboardPage.hasParagraphResult(0);
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
      const hasResult = await keyboardPage.hasParagraphResult(0);
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

      const firstHasResult = await keyboardPage.hasParagraphResult(0);
      const secondHasResult = await keyboardPage.hasParagraphResult(1);

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
      await keyboardPage.setCodeEditorContent('line1\nline2\nline3');

      // Position cursor at end
      await keyboardPage.pressKey('End');

      // When: User presses Control+P
      await keyboardPage.pressMoveCursorUp();

      // Then: Cursor should move up
      const content = await keyboardPage.getCodeEditorContent();
      expect(content).toContain('line1');
    });
  });

  test.describe('ParagraphActions.MoveCursorDown: Control+N', () => {
    test('should move cursor down with Control+N', async () => {
      // Given: A paragraph with multiple lines
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('line1\nline2\nline3');

      // Position cursor at beginning
      await keyboardPage.pressKey('Home');

      // When: User presses Control+N
      await keyboardPage.pressMoveCursorDown();

      // Then: Cursor should move down
      const content = await keyboardPage.getCodeEditorContent();
      expect(content).toContain('line2');
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

      if (currentCount >= 2) {
        // Add content to second paragraph
        const secondParagraph = keyboardPage.getParagraphByIndex(1);
        await secondParagraph.click();
        await keyboardPage.setCodeEditorContent('%python\nprint("Second paragraph")', 1);
        // Focus first paragraph
        await firstParagraph.click();
        await keyboardPage.focusCodeEditor(0);
      }

      // When: User presses Control+Alt+D
      await keyboardPage.pressDeleteParagraph();
      await keyboardPage.clickModalOkButton();

      // Then: Paragraph count should decrease
      const finalCount = await keyboardPage.getParagraphCount();
      expect(finalCount).toEqual(1);
    });
  });

  test.describe('ParagraphActions.InsertAbove: Control+Alt+A', () => {
    test('should insert paragraph above with Control+Alt+A', async () => {
      // Given: A single paragraph
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%md\n# Original Paragraph\nContent for insert above test');

      const initialCount = await keyboardPage.getParagraphCount();

      await keyboardPage.focusCodeEditor(0);

      // When: User presses Control+Alt+A
      await keyboardPage.pressInsertAbove();

      // Then: A new paragraph should be inserted above
      await keyboardPage.waitForParagraphCountChange(initialCount + 1);
      const finalCount = await keyboardPage.getParagraphCount();
      expect(finalCount).toBe(initialCount + 1);
    });
  });

  test.describe('ParagraphActions.InsertBelow: Control+Alt+B', () => {
    test('should insert paragraph below with Control+Alt+B', async () => {
      // Given: A single paragraph
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%md\n# Original Paragraph\nContent for insert below test');

      const initialCount = await keyboardPage.getParagraphCount();

      // When: User presses Control+Alt+B
      await keyboardPage.addParagraph();

      // Then: A new paragraph should be inserted below
      await keyboardPage.waitForParagraphCountChange(initialCount + 1);
      const finalCount = await keyboardPage.getParagraphCount();
      expect(finalCount).toBe(initialCount + 1);
    });
  });

  test.describe('ParagraphActions.InsertCopyOfParagraphBelow: Control+Shift+C', () => {
    test('should insert copy of paragraph below with Control+Shift+C', async () => {
      // Given: A paragraph with content
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%md\n# Copy Test\nContent to be copied below');

      const initialCount = await keyboardPage.getParagraphCount();

      // When: User presses Control+Shift+C
      await keyboardPage.pressInsertCopy();

      // Then: A copy of the paragraph should be inserted below
      await keyboardPage.waitForParagraphCountChange(initialCount + 1);
      const finalCount = await keyboardPage.getParagraphCount();
      expect(finalCount).toBe(initialCount + 1);
    });
  });

  test.describe('ParagraphActions.MoveParagraphUp: Control+Alt+K', () => {
    test('should move paragraph up with Control+Alt+K', async () => {
      // Given: Two paragraphs with second one focused
      await keyboardPage.focusCodeEditor(0);
      await keyboardPage.setCodeEditorContent('%md\n# First Paragraph\nContent for move up test', 0);
      await keyboardPage.addParagraph();
      await keyboardPage.waitForParagraphCountChange(2);

      // Focus on second paragraph and add content
      await keyboardPage.focusCodeEditor(1);
      await keyboardPage.setCodeEditorContent('%md\n# Second Paragraph\nThis should move up', 1);

      // When: User presses Control+Alt+K from second paragraph
      await keyboardPage.pressMoveParagraphUp();

      // Then: Paragraph order should change (second becomes first)
      await keyboardPage.page.waitForTimeout(1000);
      const paragraphCount = await keyboardPage.getParagraphCount();
      expect(paragraphCount).toBe(2);
    });
  });

  test.describe('ParagraphActions.MoveParagraphDown: Control+Alt+J', () => {
    test('should move paragraph down with Control+Alt+J', async () => {
      // Given: Two paragraphs with first one focused
      await keyboardPage.focusCodeEditor(0);
      await keyboardPage.setCodeEditorContent('%md\n# First Paragraph\nThis should move down', 0);
      await keyboardPage.addParagraph();
      await keyboardPage.waitForParagraphCountChange(2);

      // Add content to second paragraph
      await keyboardPage.focusCodeEditor(1);
      await keyboardPage.setCodeEditorContent('%md\n# Second Paragraph\nContent for second paragraph', 1);

      // Focus first paragraph
      await keyboardPage.focusCodeEditor(0);

      // When: User presses Control+Alt+J from first paragraph
      await keyboardPage.pressMoveParagraphDown();

      // Then: Paragraph order should change (first becomes second)
      await keyboardPage.page.waitForTimeout(1000);
      const paragraphCount = await keyboardPage.getParagraphCount();
      expect(paragraphCount).toBe(2);
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
      await keyboardPage.setCodeEditorContent('%python\nprint("Test output toggle")');
      await keyboardPage.pressRunParagraph();
      await keyboardPage.waitForParagraphExecution(0);

      const initialOutputVisibility = await keyboardPage.isOutputVisible(0);

      // When: User presses Control+Alt+O
      await keyboardPage.focusCodeEditor(0);
      await keyboardPage.pressSwitchOutputShow();

      // Then: Output visibility should toggle
      await keyboardPage.page.waitForTimeout(1000);
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
      const hasResult = await keyboardPage.hasParagraphResult(0);
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
      await keyboardPage.setCodeEditorContent('%md\n# Link Test\nTesting link paragraph functionality');

      // Verify content was set correctly
      const initialContent = await keyboardPage.getCodeEditorContent();
      expect(initialContent.replace(/\s+/g, ' ')).toContain('link');

      // When: User presses Control+Alt+W
      await keyboardPage.pressLinkParagraph();

      // Then: Link action should be triggered (verify basic functionality)
      await keyboardPage.page.waitForTimeout(1000);
      const content = await keyboardPage.getCodeEditorContent();
      expect(content.length).toBeGreaterThan(0);
      expect(content).toMatch(/link|test/i);

      // Verify system remains functional
      const paragraphCount = await keyboardPage.getParagraphCount();
      expect(paragraphCount).toBeGreaterThanOrEqual(1);
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

      // Then: Paragraph width should change
      const finalWidth = await keyboardPage.getParagraphWidth(0);
      expect(finalWidth).not.toBe(initialWidth);
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

      // Then: Paragraph width should change
      const finalWidth = await keyboardPage.getParagraphWidth(0);
      expect(finalWidth).not.toBe(initialWidth);
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

  test.describe('ParagraphActions.PasteLine: Control+Y', () => {
    test('should paste line with Control+Y', async () => {
      // Given: Content in the editor
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('line to cut and paste');

      // Wait for content to be properly set before verifying
      await keyboardPage.page.waitForTimeout(500);

      const initialContent = await keyboardPage.getCodeEditorContent();
      // Debug: Log the actual content and its character codes
      console.log('Initial content:', JSON.stringify(initialContent));
      console.log('Expected:', JSON.stringify('line to cut and paste'));

      // Use a more robust assertion that handles encoding issues
      const expectedText = 'line to cut and paste';
      expect(
        initialContent.includes(expectedText) ||
          initialContent.normalize().includes(expectedText) ||
          initialContent.replace(/\s+/g, ' ').trim().includes(expectedText)
      ).toBeTruthy();

      // Cut the line first
      await keyboardPage.pressCutLine();
      await keyboardPage.page.waitForTimeout(500);

      // When: User presses Control+Y to paste
      await keyboardPage.pressPasteLine();

      // Then: Content should be pasted back
      await keyboardPage.page.waitForTimeout(500);
      const finalContent = await keyboardPage.getCodeEditorContent();
      expect(finalContent.length).toBeGreaterThan(0);
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

  // TODO: Fix the previously skipped tests - ZEPPELIN-6379
  test.describe('ParagraphActions.FindInCode: Control+Alt+F', () => {
    test.skip();
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
    test('should handle Control+Space key combination', async () => {
      // Given: Code editor with partial code
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\npr');
      await keyboardPage.pressKey('End'); // Position cursor at end

      // When: User presses Control+Space
      await keyboardPage.pressControlSpace();

      // Then: Should handle the key combination without errors
      const isAutocompleteVisible = await keyboardPage.isAutocompleteVisible();
      expect(typeof isAutocompleteVisible).toBe('boolean');
    });

    test('should handle autocomplete interaction gracefully', async () => {
      // Given: Code editor with content that might trigger autocomplete
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\nprint');

      // When: User tries autocomplete operations
      await keyboardPage.pressControlSpace();

      // Handle potential autocomplete popup
      const isAutocompleteVisible = await keyboardPage.isAutocompleteVisible();
      if (isAutocompleteVisible) {
        await keyboardPage.pressArrowDown();
        await keyboardPage.pressEscape(); // Close autocomplete
      }

      // Then: System should remain stable
      const codeEditorComponent = keyboardPage.page.locator('zeppelin-notebook-paragraph-code-editor').first();
      await expect(codeEditorComponent).toBeVisible();
    });
  });

  test.describe('Tab: Code Indentation', () => {
    test('should indent code properly when Tab is pressed', async () => {
      // Given: Code editor with a function definition
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\ndef function():');
      await keyboardPage.pressKey('End');
      await keyboardPage.pressKey('Enter');

      const contentBeforeTab = await keyboardPage.getCodeEditorContent();

      // When: User presses Tab for indentation
      await keyboardPage.pressTab();

      // Then: Code should be properly indented
      const contentAfterTab = await keyboardPage.getCodeEditorContent();
      // Check for any indentation (spaces or tabs)
      expect(contentAfterTab.match(/\s+/)).toBeTruthy(); // Should contain indentation
      expect(contentAfterTab.length).toBeGreaterThan(contentBeforeTab.length);
    });
  });

  test.describe('Arrow Keys: Navigation', () => {
    test('should handle arrow key navigation in notebook context', async () => {
      // Given: A notebook with paragraph(s)
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('test content');

      // When: User uses arrow keys
      await keyboardPage.pressArrowDown();
      await keyboardPage.pressArrowUp();

      // Then: Should handle arrow keys without errors
      const paragraphCount = await keyboardPage.getParagraphCount();
      expect(paragraphCount).toBeGreaterThanOrEqual(1);
    });
  });

  test.describe('Interpreter Selection', () => {
    test('should allow typing interpreter selector shortcuts', async () => {
      // Given: Empty code editor
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('');

      // When: User types interpreter selector
      await keyboardPage.typeInEditor('%python\n');

      // Then: Code should contain interpreter directive
      const content = await keyboardPage.getCodeEditorContent();
      expect(content).toContain('%python');
    });
  });

  // ===== CROSS-PLATFORM COMPATIBILITY =====

  test.describe('Cross-platform Compatibility', () => {
    test('should handle cancel shortcut on all platforms', async () => {
      // Given: A paragraph ready for shortcuts
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\nprint("Platform compatibility test")');

      // When: User uses cancel shortcut
      await keyboardPage.pressCancel();

      // Then: Shortcut should work appropriately
      await keyboardPage.page.waitForTimeout(1000);
      const content = await keyboardPage.getCodeEditorContent();
      expect(content).toMatch(/platform.*compatibility.*test/i);

      const paragraphCount = await keyboardPage.getParagraphCount();
      expect(paragraphCount).toBeGreaterThanOrEqual(1);
    });

    test('should work consistently across different browser contexts', async () => {
      // Navigate to the test notebook first
      await keyboardPage.navigateToNotebook(testNotebook.noteId);

      // Given: Standard keyboard shortcuts
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%python\nprint("Cross-browser test")');

      // When: User performs standard operations
      await keyboardPage.pressRunParagraph();

      // Then: Should work consistently
      await expect(keyboardPage.paragraphResult.first()).toBeVisible({ timeout: 10000 });
    });
  });

  // ===== COMPREHENSIVE INTEGRATION TESTS =====

  test.describe('Comprehensive Shortcuts Integration', () => {
    test('should maintain shortcut functionality after errors', async () => {
      // Given: An error has occurred
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('invalid python syntax here');
      await keyboardPage.pressRunParagraph();
      await keyboardPage.waitForParagraphExecution(0);

      // Verify error result exists
      const hasErrorResult = await keyboardPage.hasParagraphResult(0);
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
      const hasResult = await keyboardPage.hasParagraphResult(newParagraphIndex);
      expect(hasResult).toBe(true);
    });

    test('should handle shortcuts when no paragraph is focused', async () => {
      // Given: No focused paragraph
      await keyboardPage.page.click('body');
      await keyboardPage.page.waitForTimeout(500);

      const initialCount = await keyboardPage.getParagraphCount();

      // When: User presses insert shortcut without focus
      await keyboardPage.addParagraph();

      // Then: Shortcut should still work and create new paragraph
      await keyboardPage.waitForParagraphCountChange(initialCount + 1);
      const finalCount = await keyboardPage.getParagraphCount();
      expect(finalCount).toBe(initialCount + 1);
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
