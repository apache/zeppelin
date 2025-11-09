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
import { NotebookParagraphPage } from './notebook-paragraph-page';

export class NotebookParagraphUtil {
  private page: Page;
  private paragraphPage: NotebookParagraphPage;

  constructor(page: Page) {
    this.page = page;
    this.paragraphPage = new NotebookParagraphPage(page);
  }

  async verifyParagraphContainerStructure(): Promise<void> {
    await expect(this.paragraphPage.paragraphContainer).toBeVisible();
    await expect(this.paragraphPage.controlPanel).toBeVisible();
  }

  async verifyDoubleClickEditingFunctionality(): Promise<void> {
    await expect(this.paragraphPage.paragraphContainer).toBeVisible();

    await this.paragraphPage.doubleClickToEdit();

    await expect(this.paragraphPage.codeEditor).toBeVisible();
  }

  async verifyAddParagraphButtons(): Promise<void> {
    await expect(this.paragraphPage.addParagraphAbove).toBeVisible();
    await expect(this.paragraphPage.addParagraphBelow).toBeVisible();

    const addAboveCount = await this.paragraphPage.addParagraphAbove.count();
    const addBelowCount = await this.paragraphPage.addParagraphBelow.count();

    expect(addAboveCount).toBeGreaterThan(0);
    expect(addBelowCount).toBeGreaterThan(0);
  }

  async verifyParagraphControlInterface(): Promise<void> {
    await expect(this.paragraphPage.controlPanel).toBeVisible();
    await this.paragraphPage.runButton.isVisible();
    await expect(this.paragraphPage.runButton).toBeVisible();
    const isRunEnabled = await this.paragraphPage.isRunButtonEnabled();
    expect(isRunEnabled).toBe(true);
  }

  async verifyCodeEditorFunctionality(): Promise<void> {
    await this.paragraphPage.isCodeEditorVisible();
    await expect(this.paragraphPage.codeEditor).toBeVisible();
  }

  async verifyResultDisplaySystem(): Promise<void> {
    await this.paragraphPage.hasResult();
    await expect(this.paragraphPage.resultDisplay).toBeVisible();
  }

  async verifyTitleEditing(): Promise<void> {
    // First ensure we're actually on a notebook page
    await expect(this.page).toHaveURL(/\/notebook\/[^\/]+/, { timeout: 10000 });

    // Title editor should be visible and editable
    await expect(this.paragraphPage.titleEditor).toBeVisible();

    // Look for the actual input element inside the elastic input component
    const titleInput = this.paragraphPage.titleEditor.locator('input, textarea').first();
    await expect(titleInput).toBeEditable();
  }

  async verifyDynamicForms(): Promise<void> {
    await expect(this.paragraphPage.dynamicForms).toBeVisible();
  }

  async verifyFooterInformation(): Promise<void> {
    const footerText = await this.paragraphPage.getFooterText();
    expect(footerText).toBeDefined();
  }

  async verifyParagraphControlActions(): Promise<void> {
    await this.paragraphPage.openSettingsDropdown();

    // Wait for dropdown to appear by checking for any menu item
    const dropdownMenu = this.page.locator('ul.ant-dropdown-menu, .dropdown-menu');
    await expect(dropdownMenu).toBeVisible({ timeout: 5000 });

    // These dropdown menu items should be available
    await expect(this.page.locator('li:has-text("Insert")')).toBeVisible();
    await expect(this.page.locator('li:has-text("Clone")')).toBeVisible();

    // Close dropdown if it's open
    await this.page.keyboard.press('Escape');
  }

  async verifyCancelParagraphButton(): Promise<void> {
    await this.paragraphPage.runButton.isVisible();
    await expect(this.paragraphPage.runButton).toBeVisible();
    await expect(this.paragraphPage.runButton).toBeEnabled();

    // Add long-running code to see cancel button
    await this.paragraphPage.doubleClickToEdit();
    await expect(this.paragraphPage.codeEditor).toBeVisible();

    const codeEditor = this.paragraphPage.codeEditor.locator('textarea, .monaco-editor .input-area').first();
    await expect(codeEditor).toBeAttached({ timeout: 10000 });
    await expect(codeEditor).toBeEnabled({ timeout: 10000 });

    await codeEditor.focus();
    await expect(codeEditor).toBeFocused({ timeout: 5000 });

    await this.page.keyboard.press('Control+a');
    await this.page.keyboard.type('%python\nimport time\ntime.sleep(5)\nprint("Done")');

    await this.paragraphPage.runParagraph();

    // Cancel button should appear during execution
    const cancelButton = this.page.locator(
      '.cancel-para, [nztooltiptitle="Cancel paragraph"], i[nztype="pause-circle"]'
    );
    await expect(cancelButton).toBeVisible({ timeout: 5000 });

    // Click cancel to stop execution
    await cancelButton.click();
  }

  async verifyAdvancedParagraphOperations(): Promise<void> {
    // First ensure we're actually on a notebook page
    await expect(this.page).toHaveURL(/\/notebook\/[^\/]+/, { timeout: 10000 });

    // Wait for paragraph to be visible before trying to interact with it
    await expect(this.paragraphPage.paragraphContainer).toBeVisible({ timeout: 15000 });

    await this.paragraphPage.openSettingsDropdown();

    // Wait for dropdown to appear by checking for any menu item
    const dropdownMenu = this.page.locator('ul.ant-dropdown-menu, .dropdown-menu');
    await expect(dropdownMenu).toBeVisible({ timeout: 5000 });

    await expect(this.page.locator('li:has-text("Insert")')).toBeVisible();
    await expect(this.page.locator('li:has-text("Clone")')).toBeVisible();

    // Close dropdown if it's open
    await this.page.keyboard.press('Escape');
  }
}
