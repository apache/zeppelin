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

  async verifyTitleEditingIfPresent(): Promise<void> {
    const titleVisible = await this.paragraphPage.titleEditor.isVisible();
    if (titleVisible) {
      await expect(this.paragraphPage.titleEditor).toBeEditable();
    }
  }

  async verifyDynamicFormsIfPresent(): Promise<void> {
    await this.paragraphPage.isDynamicFormsVisible();
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
    await expect(this.page.locator('li:has-text("Move up")')).toBeVisible();
    await expect(this.page.locator('li:has-text("Delete")')).toBeVisible();
    await expect(this.page.locator('li:has-text("Clone")')).toBeVisible();

    // Close dropdown if it's open
    await this.page.keyboard.press('Escape');
  }

  async verifyParagraphExecutionWorkflow(): Promise<void> {
    await this.paragraphPage.runButton.isVisible();
    await expect(this.paragraphPage.runButton).toBeVisible();
    await expect(this.paragraphPage.runButton).toBeEnabled();

    await this.paragraphPage.runParagraph();

    await this.paragraphPage.isStopButtonVisible();
    await expect(this.paragraphPage.stopButton).toBeVisible();
  }

  async verifyAdvancedParagraphOperations(): Promise<void> {
    await this.paragraphPage.openSettingsDropdown();

    // Wait for dropdown to appear by checking for any menu item
    const dropdownMenu = this.page.locator('ul.ant-dropdown-menu, .dropdown-menu');
    await expect(dropdownMenu).toBeVisible({ timeout: 5000 });

    const clearOutputItem = this.page.locator('li:has-text("Clear output")');
    const toggleEditorItem = this.page.locator('li:has-text("Toggle editor")');
    const insertBelowItem = this.page.locator('li:has-text("Insert below")');

    // These menu items should be available in the dropdown
    await expect(clearOutputItem).toBeVisible();
    await expect(toggleEditorItem).toBeVisible();
    await expect(insertBelowItem).toBeVisible();

    // Close dropdown if it's open
    await this.page.keyboard.press('Escape');
  }
}
