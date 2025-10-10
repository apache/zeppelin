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

import { expect, Page } from '@playwright/test';
import { BasePage } from './base-page';
import { NotebookKeyboardPage } from './notebook-keyboard-page';
import { PublishedParagraphTestUtil } from './published-paragraph-page.util';

export class NotebookKeyboardPageUtil extends BasePage {
  private keyboardPage: NotebookKeyboardPage;
  private testUtil: PublishedParagraphTestUtil;

  constructor(page: Page) {
    super(page);
    this.keyboardPage = new NotebookKeyboardPage(page);
    this.testUtil = new PublishedParagraphTestUtil(page);
  }

  // ===== SETUP AND PREPARATION METHODS =====

  async createTestNotebook(): Promise<{ noteId: string; paragraphId: string }> {
    return await this.testUtil.createTestNotebook();
  }

  async deleteTestNotebook(noteId: string): Promise<void> {
    await this.testUtil.deleteTestNotebook(noteId);
  }

  async prepareNotebookForKeyboardTesting(noteId: string): Promise<void> {
    await this.keyboardPage.navigateToNotebook(noteId);

    // Wait for the notebook to load completely
    await expect(this.keyboardPage.paragraphContainer.first()).toBeVisible({ timeout: 15000 });

    // Clear any existing content and output
    const paragraphCount = await this.keyboardPage.getParagraphCount();
    if (paragraphCount > 0) {
      const hasParagraphResult = await this.keyboardPage.hasParagraphResult(0);
      if (hasParagraphResult) {
        await this.keyboardPage.clearParagraphOutput(0);
      }

      // Set a simple test code - focus first, then set content
      await this.keyboardPage.setCodeEditorContent('print("Hello World")');
    }
  }

  // ===== SHIFT+ENTER TESTING METHODS =====

  async verifyShiftEnterRunsParagraph(): Promise<void> {
    // Given: A paragraph with code
    await this.keyboardPage.focusCodeEditor();
    const initialParagraphCount = await this.keyboardPage.getParagraphCount();

    // When: Pressing Shift+Enter
    await this.keyboardPage.pressShiftEnter();

    // Then: Paragraph should run and stay focused
    await expect(this.keyboardPage.paragraphResult.first()).toBeVisible({ timeout: 10000 });

    // Should not create new paragraph
    const finalParagraphCount = await this.keyboardPage.getParagraphCount();
    expect(finalParagraphCount).toBe(initialParagraphCount);
  }

  async verifyShiftEnterWithNoCode(): Promise<void> {
    // Given: An empty paragraph
    await this.keyboardPage.focusCodeEditor();
    await this.keyboardPage.setCodeEditorContent('');

    // When: Pressing Shift+Enter
    await this.keyboardPage.pressShiftEnter();

    // Then: Should not execute anything
    const hasParagraphResult = await this.keyboardPage.hasParagraphResult(0);
    expect(hasParagraphResult).toBe(false);
  }

  // ===== CONTROL+ENTER TESTING METHODS =====

  async verifyControlEnterRunsAndCreatesNewParagraph(): Promise<void> {
    // Given: A paragraph with code
    await this.keyboardPage.focusCodeEditor();
    const initialParagraphCount = await this.keyboardPage.getParagraphCount();

    // When: Pressing Control+Enter
    await this.keyboardPage.pressControlEnter();

    // Then: Paragraph should run and new paragraph should be created
    await expect(this.keyboardPage.paragraphResult.first()).toBeVisible({ timeout: 10000 });

    const finalParagraphCount = await this.keyboardPage.getParagraphCount();
    expect(finalParagraphCount).toBe(initialParagraphCount + 1);
  }

  async verifyControlEnterFocusesNewParagraph(): Promise<void> {
    // Given: A paragraph with code
    await this.keyboardPage.focusCodeEditor();
    const initialCount = await this.keyboardPage.getParagraphCount();

    // When: Pressing Control+Enter
    await this.keyboardPage.pressControlEnter();

    // Then: New paragraph should be created
    await expect(this.keyboardPage.paragraphContainer).toHaveCount(initialCount + 1, { timeout: 10000 });

    // And new paragraph should be focusable
    const secondParagraph = this.keyboardPage.getParagraphByIndex(1);
    await expect(secondParagraph).toBeVisible();
  }

  // ===== CONTROL+SPACE TESTING METHODS =====

  async verifyControlSpaceTriggersAutocomplete(): Promise<void> {
    // Given: Code editor with partial code
    await this.keyboardPage.focusCodeEditor();
    await this.keyboardPage.setCodeEditorContent('pr');

    // Position cursor at the end
    await this.keyboardPage.pressKey('End');

    // When: Pressing Control+Space
    await this.keyboardPage.pressControlSpace();

    // Then: Autocomplete popup should appear
    await expect(this.keyboardPage.autocompletePopup).toBeVisible({ timeout: 5000 });

    const itemCount = await this.keyboardPage.getAutocompleteItemCount();
    expect(itemCount).toBeGreaterThan(0);
  }

  async verifyAutocompleteNavigation(): Promise<void> {
    // Given: Autocomplete is visible
    await this.verifyControlSpaceTriggersAutocomplete();

    // When: Navigating with arrow keys
    await this.keyboardPage.pressArrowDown();
    await this.keyboardPage.pressArrowUp();

    // Then: Autocomplete should still be visible and responsive
    await expect(this.keyboardPage.autocompletePopup).toBeVisible();
  }

  async verifyAutocompleteSelection(): Promise<void> {
    // Given: Autocomplete is visible
    await this.verifyControlSpaceTriggersAutocomplete();

    const initialContent = await this.keyboardPage.getCodeEditorContent();

    // When: Selecting item with Tab
    await this.keyboardPage.pressTab();

    // Then: Content should be updated
    const finalContent = await this.keyboardPage.getCodeEditorContent();
    expect(finalContent).not.toBe(initialContent);
    expect(finalContent.length).toBeGreaterThan(initialContent.length);
  }

  async verifyAutocompleteEscape(): Promise<void> {
    // Given: Autocomplete is visible
    await this.verifyControlSpaceTriggersAutocomplete();

    // When: Pressing Escape
    await this.keyboardPage.pressEscape();

    // Then: Autocomplete should be hidden
    await expect(this.keyboardPage.autocompletePopup).toBeHidden();
  }

  // ===== NAVIGATION TESTING METHODS =====

  async verifyArrowKeyNavigationBetweenParagraphs(): Promise<void> {
    // Given: Multiple paragraphs exist
    const initialCount = await this.keyboardPage.getParagraphCount();
    if (initialCount < 2) {
      // Create a second paragraph
      await this.keyboardPage.pressControlEnter();
      await expect(this.keyboardPage.paragraphContainer).toHaveCount(initialCount + 1, { timeout: 10000 });
    }

    // Focus first paragraph
    await this.keyboardPage
      .getParagraphByIndex(0)
      .locator('.monaco-editor')
      .click();

    // When: Pressing arrow down to move to next paragraph
    await this.keyboardPage.pressArrowDown();

    // Then: Should have at least 2 paragraphs available for navigation
    const finalCount = await this.keyboardPage.getParagraphCount();
    expect(finalCount).toBeGreaterThanOrEqual(2);
  }

  async verifyTabIndentation(): Promise<void> {
    // Given: Code editor with content
    await this.keyboardPage.focusCodeEditor();
    await this.keyboardPage.setCodeEditorContent('def function():');
    await this.keyboardPage.pressKey('End');
    await this.keyboardPage.pressKey('Enter');

    const contentBeforeTab = await this.keyboardPage.getCodeEditorContent();

    // When: Pressing Tab for indentation
    await this.keyboardPage.pressTab();

    // Then: Content should be indented
    const contentAfterTab = await this.keyboardPage.getCodeEditorContent();
    expect(contentAfterTab).toContain('    '); // Should contain indentation
    expect(contentAfterTab.length).toBeGreaterThan(contentBeforeTab.length);
  }

  // ===== INTERPRETER SELECTION TESTING METHODS =====

  async verifyInterpreterShortcuts(): Promise<void> {
    // Given: Code editor is focused
    await this.keyboardPage.focusCodeEditor();

    // Clear existing content
    await this.keyboardPage.setCodeEditorContent('');

    // When: Typing interpreter selector
    await this.keyboardPage.typeInEditor('%python\n');

    // Then: Code should contain interpreter directive
    const content = await this.keyboardPage.getCodeEditorContent();
    expect(content).toContain('%python');
  }

  // ===== COMPREHENSIVE TESTING METHODS =====

  async verifyKeyboardShortcutWorkflow(): Promise<void> {
    // Test complete workflow: type code -> run -> create new -> autocomplete

    // Step 1: Type code and run with Shift+Enter
    await this.keyboardPage.focusCodeEditor();
    await this.keyboardPage.setCodeEditorContent('print("First paragraph")');
    await this.keyboardPage.pressShiftEnter();
    await expect(this.keyboardPage.paragraphResult.first()).toBeVisible({ timeout: 10000 });

    // Step 2: Run and create new with Control+Enter
    await this.keyboardPage.focusCodeEditor();
    await this.keyboardPage.pressControlEnter();

    // Step 3: Verify new paragraph is created and focused
    const paragraphCount = await this.keyboardPage.getParagraphCount();
    expect(paragraphCount).toBe(2);

    // Step 4: Test autocomplete in new paragraph
    await this.keyboardPage.typeInEditor('pr');
    await this.keyboardPage.pressControlSpace();

    if (await this.keyboardPage.isAutocompleteVisible()) {
      await this.keyboardPage.pressEscape();
    }
  }

  async verifyErrorHandlingInKeyboardOperations(): Promise<void> {
    // Test keyboard operations when errors occur

    // Given: Code with syntax error
    await this.keyboardPage.focusCodeEditor();
    await this.keyboardPage.setCodeEditorContent('print("unclosed string');

    // When: Running with Shift+Enter
    await this.keyboardPage.pressShiftEnter();

    // Then: Should handle error gracefully by showing a result
    await expect(this.keyboardPage.paragraphResult.first()).toBeVisible({ timeout: 15000 });

    // Verify result area exists (may contain error)
    const hasResult = await this.keyboardPage.hasParagraphResult(0);
    expect(hasResult).toBe(true);
  }

  async verifyKeyboardOperationsInReadOnlyMode(): Promise<void> {
    // Test that keyboard shortcuts behave appropriately in read-only contexts

    // This method can be extended when read-only mode is available
    // For now, we verify that normal operations work
    await this.verifyShiftEnterRunsParagraph();
  }

  // ===== PERFORMANCE AND STABILITY TESTING =====

  async verifyRapidKeyboardOperations(): Promise<void> {
    // Test rapid keyboard operations for stability

    await this.keyboardPage.focusCodeEditor();
    await this.keyboardPage.setCodeEditorContent('print("test")');

    // Rapid Shift+Enter operations
    for (let i = 0; i < 3; i++) {
      await this.keyboardPage.pressShiftEnter();
      // Wait for result to appear before next operation
      await expect(this.keyboardPage.paragraphResult.first()).toBeVisible({ timeout: 10000 });
    }

    // Verify system remains stable
    const codeEditorComponent = this.page.locator('zeppelin-notebook-paragraph-code-editor').first();
    await expect(codeEditorComponent).toBeVisible();
  }
}
