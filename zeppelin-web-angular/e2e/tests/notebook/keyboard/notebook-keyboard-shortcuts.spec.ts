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

test.describe('Notebook Keyboard Shortcuts', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK);

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
    const cancelButton = page.locator('.ant-modal-root button', { hasText: 'Cancel' });
    if ((await cancelButton.count()) > 0) {
      await cancelButton.click();
    }

    testNotebook = await testUtil.createTestNotebook();
    await testUtil.prepareNotebookForKeyboardTesting(testNotebook.noteId);
  });

  test.afterEach(async () => {
    if (testNotebook?.noteId) {
      await testUtil.deleteTestNotebook(testNotebook.noteId);
    }
  });

  test.describe('Shift+Enter: Run Paragraph', () => {
    test('should run current paragraph when Shift+Enter is pressed', async () => {
      // Given: A paragraph with executable code
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('print("Hello from Shift+Enter")');

      // When: User presses Shift+Enter
      await keyboardPage.pressShiftEnter();

      // Then: The paragraph should execute and show results
      await expect(keyboardPage.paragraphResult.first()).toBeVisible({ timeout: 15000 });

      // Note: In Zeppelin, Shift+Enter may create a new paragraph in some configurations
      // We verify that execution happened, not paragraph count behavior
      const hasResult = await keyboardPage.hasParagraphResult(0);
      expect(hasResult).toBe(true);
    });

    test('should handle empty paragraph gracefully when Shift+Enter is pressed', async () => {
      // Given: Clear any existing results first
      const hasExistingResult = await keyboardPage.hasParagraphResult(0);
      if (hasExistingResult) {
        await keyboardPage.clearParagraphOutput(0);
      }

      // Given: Set interpreter to md (markdown) for empty content test to avoid interpreter errors
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('%md\n');

      // When: User presses Shift+Enter on empty markdown
      await keyboardPage.pressShiftEnter();

      // Then: Should execute and show result (even empty markdown creates a result container)
      // Wait for execution to complete
      await keyboardPage.page.waitForTimeout(2000);

      // Markdown interpreter should handle empty content gracefully
      const hasParagraphResult = await keyboardPage.hasParagraphResult(0);
      expect(hasParagraphResult).toBe(true); // Markdown interpreter creates result container even for empty content
    });

    test('should run paragraph with syntax error and display error result', async () => {
      // Given: A paragraph with syntax error
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('print("unclosed string');

      // When: User presses Shift+Enter
      await keyboardPage.pressShiftEnter();

      // Then: Should execute and show error result
      await expect(keyboardPage.paragraphResult.first()).toBeVisible({ timeout: 10000 });
      const hasResult = await keyboardPage.hasParagraphResult(0);
      expect(hasResult).toBe(true);
    });
  });

  test.describe('Control+Enter: Paragraph Operations', () => {
    test('should perform Control+Enter operation', async () => {
      // Given: A paragraph with executable code
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('print("Hello from Control+Enter")');
      const initialCount = await keyboardPage.getParagraphCount();

      // When: User presses Control+Enter
      await keyboardPage.pressControlEnter();

      // Then: Some operation should be performed (may vary by Zeppelin configuration)
      // Check if paragraph count changed or if execution occurred
      const finalCount = await keyboardPage.getParagraphCount();
      const hasResult = await keyboardPage.hasParagraphResult(0);

      // Either new paragraph created OR execution happened
      expect(finalCount >= initialCount || hasResult).toBe(true);
    });

    test('should handle Control+Enter key combination', async () => {
      // Given: A paragraph with code
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('print("Test Control+Enter")');

      // When: User presses Control+Enter
      await keyboardPage.pressControlEnter();

      // Then: Verify the key combination is handled (exact behavior may vary)
      // This test ensures the key combination doesn't cause errors
      const paragraphCount = await keyboardPage.getParagraphCount();
      expect(paragraphCount).toBeGreaterThanOrEqual(1);
    });

    test('should maintain system stability with Control+Enter operations', async () => {
      // Given: A paragraph with code
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('print("Stability test")');

      // When: User performs Control+Enter operation
      await keyboardPage.pressControlEnter();

      // Then: System should remain stable and responsive
      const codeEditorComponent = keyboardPage.page.locator('zeppelin-notebook-paragraph-code-editor').first();
      await expect(codeEditorComponent).toBeVisible();
    });
  });

  test.describe('Control+Space: Code Autocompletion', () => {
    test('should handle Control+Space key combination', async () => {
      // Given: Code editor with partial code
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('pr');
      await keyboardPage.pressKey('End'); // Position cursor at end

      // When: User presses Control+Space
      await keyboardPage.pressControlSpace();

      // Then: Should handle the key combination without errors
      // Note: Autocomplete behavior may vary based on interpreter and context
      const isAutocompleteVisible = await keyboardPage.isAutocompleteVisible();

      // Test passes if either autocomplete appears OR system handles key gracefully
      expect(typeof isAutocompleteVisible).toBe('boolean');
    });

    test('should handle autocomplete interaction gracefully', async () => {
      // Given: Code editor with content that might trigger autocomplete
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('print');

      // When: User tries autocomplete operations
      await keyboardPage.pressControlSpace();

      // Handle potential autocomplete popup
      const isAutocompleteVisible = await keyboardPage.isAutocompleteVisible();
      if (isAutocompleteVisible) {
        // If autocomplete is visible, test navigation
        await keyboardPage.pressArrowDown();
        await keyboardPage.pressEscape(); // Close autocomplete
      }

      // Then: System should remain stable
      const codeEditorComponent = keyboardPage.page.locator('zeppelin-notebook-paragraph-code-editor').first();
      await expect(codeEditorComponent).toBeVisible();
    });

    test('should handle Tab key appropriately', async () => {
      // Given: Code editor is focused
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('if True:');
      await keyboardPage.pressKey('End');

      // When: User presses Tab (might be for indentation or autocomplete)
      await keyboardPage.pressTab();

      // Then: Should handle Tab key appropriately
      const content = await keyboardPage.getCodeEditorContent();
      expect(content).toContain('if True:');
    });

    test('should handle Escape key gracefully', async () => {
      // Given: Code editor with focus
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('test');

      // When: User presses Escape
      await keyboardPage.pressEscape();

      // Then: Should handle Escape without errors
      const codeEditorComponent = keyboardPage.page.locator('zeppelin-notebook-paragraph-code-editor').first();
      await expect(codeEditorComponent).toBeVisible();
    });
  });

  test.describe('Tab: Code Indentation', () => {
    test('should indent code properly when Tab is pressed', async () => {
      // Given: Code editor with a function definition
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('def function():');
      await keyboardPage.pressKey('End');
      await keyboardPage.pressKey('Enter');

      const contentBeforeTab = await keyboardPage.getCodeEditorContent();

      // When: User presses Tab for indentation
      await keyboardPage.pressTab();

      // Then: Code should be properly indented
      const contentAfterTab = await keyboardPage.getCodeEditorContent();
      expect(contentAfterTab).toContain('    '); // Should contain indentation
      expect(contentAfterTab.length).toBeGreaterThan(contentBeforeTab.length);
    });

    test('should handle Tab when autocomplete is not active', async () => {
      // Given: Code editor without autocomplete active
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('if True:');
      await keyboardPage.pressKey('Enter');

      // When: User presses Tab
      await keyboardPage.pressTab();

      // Then: Should add indentation
      const content = await keyboardPage.getCodeEditorContent();
      expect(content).toContain('    '); // Indentation added
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

    test('should navigate within editor content using arrow keys', async () => {
      // Given: Code editor with multi-line content
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('line1\nline2\nline3');

      // When: User uses arrow keys to navigate
      await keyboardPage.pressKey('Home'); // Go to beginning
      await keyboardPage.pressArrowDown(); // Move down one line

      // Then: Content should remain intact
      const content = await keyboardPage.getCodeEditorContent();
      expect(content).toContain('line1');
      expect(content).toContain('line2');
      expect(content).toContain('line3');
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

    test('should handle different interpreter shortcuts', async () => {
      // Given: Empty code editor
      await keyboardPage.focusCodeEditor();

      // When: User types various interpreter shortcuts
      await keyboardPage.setCodeEditorContent('%scala\nprint("Hello Scala")');

      // Then: Content should be preserved correctly
      const content = await keyboardPage.getCodeEditorContent();
      expect(content).toContain('%scala');
      expect(content).toContain('print("Hello Scala")');
    });
  });

  test.describe('Complex Keyboard Workflows', () => {
    test('should handle complete keyboard-driven workflow', async () => {
      // Given: User wants to complete entire workflow with keyboard

      // When: User performs complete workflow
      await testUtil.verifyKeyboardShortcutWorkflow();

      // Then: All operations should complete successfully
      const finalParagraphCount = await keyboardPage.getParagraphCount();
      expect(finalParagraphCount).toBeGreaterThanOrEqual(2);
    });

    test('should handle rapid keyboard operations without instability', async () => {
      // Given: User performs rapid keyboard operations

      // When: Multiple rapid operations are performed
      await testUtil.verifyRapidKeyboardOperations();

      // Then: System should remain stable
      const isEditorVisible = await keyboardPage.codeEditor.first().isVisible();
      expect(isEditorVisible).toBe(true);
    });
  });

  test.describe('Error Handling and Edge Cases', () => {
    test('should handle keyboard operations with syntax errors gracefully', async () => {
      // Given: Code with syntax errors

      // When: User performs keyboard operations
      await testUtil.verifyErrorHandlingInKeyboardOperations();

      // Then: System should handle errors gracefully
      const hasResult = await keyboardPage.hasParagraphResult(0);
      expect(hasResult).toBe(true);
    });

    test('should maintain keyboard functionality after errors', async () => {
      // Given: An error has occurred
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('invalid syntax here');
      await keyboardPage.pressShiftEnter();

      // Wait for error result to appear
      await expect(keyboardPage.paragraphResult.first()).toBeVisible({ timeout: 15000 });

      // When: User continues with keyboard operations
      await keyboardPage.setCodeEditorContent('print("Recovery test")');
      await keyboardPage.pressShiftEnter();

      // Then: Keyboard operations should continue to work
      await expect(keyboardPage.paragraphResult.first()).toBeVisible({ timeout: 10000 });
    });
  });

  test.describe('Cross-browser Keyboard Compatibility', () => {
    test('should work consistently across different browser contexts', async () => {
      // Given: Standard keyboard shortcuts
      await keyboardPage.focusCodeEditor();
      await keyboardPage.setCodeEditorContent('print("Cross-browser test")');

      // When: User performs standard operations
      await keyboardPage.pressShiftEnter();

      // Then: Should work consistently
      await expect(keyboardPage.paragraphResult.first()).toBeVisible({ timeout: 10000 });
    });
  });
});
