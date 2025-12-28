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
export class NotebookKeyboardPageUtil extends BasePage {
  private keyboardPage: NotebookKeyboardPage;

  constructor(page: Page) {
    super(page);
    this.keyboardPage = new NotebookKeyboardPage(page);
  }

  async prepareNotebookForKeyboardTesting(noteId: string): Promise<void> {
    console.log(`Preparing notebook for keyboard testing. noteId: ${noteId}`);

    // Verify we're starting from a valid state
    const urlBefore = this.page.url();
    console.log(`Current URL before navigation: ${urlBefore}`);

    await this.keyboardPage.navigateToNotebook(noteId);

    // Verify navigation succeeded
    const urlAfter = this.page.url();
    console.log(`Current URL after navigation: ${urlAfter}`);

    if (!urlAfter.includes(`/notebook/${noteId}`)) {
      throw new Error(
        `Navigation to notebook ${noteId} failed. ` +
          `Expected URL to contain '/notebook/${noteId}', but got: ${urlAfter}`
      );
    }

    // Wait for the notebook to load
    await expect(this.keyboardPage.paragraphContainer.first()).toBeVisible({ timeout: 30000 });

    const paragraphCount = await this.keyboardPage.getParagraphCount();
    console.log(`Paragraph count after navigation: ${paragraphCount}`);

    await this.keyboardPage.setCodeEditorContent('%python\nprint("Hello World")');

    console.log(`Notebook preparation complete for noteId: ${noteId}`);
  }

  async verifyRapidKeyboardOperations(): Promise<void> {
    // Test rapid keyboard operations for stability

    await this.keyboardPage.focusCodeEditor();
    await this.keyboardPage.setCodeEditorContent('%python\nprint("test")');

    // Rapid Shift+Enter operations
    for (let i = 0; i < 3; i++) {
      await this.keyboardPage.pressRunParagraph();
      // Wait for result to appear before next operation
      await expect(this.keyboardPage.paragraphResult.first()).toBeVisible({ timeout: 15000 });
      await this.page.waitForTimeout(500); // Prevent overlap between runs
    }

    // Verify system remains stable
    const codeEditorComponent = this.page.locator('zeppelin-notebook-paragraph-code-editor').first();
    await expect(codeEditorComponent).toBeVisible();
  }
}
