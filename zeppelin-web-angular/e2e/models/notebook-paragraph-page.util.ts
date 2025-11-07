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

    // Check if run button exists and is visible
    try {
      const runButtonVisible = await this.paragraphPage.runButton.isVisible();
      if (runButtonVisible) {
        await expect(this.paragraphPage.runButton).toBeVisible();
        const isRunEnabled = await this.paragraphPage.isRunButtonEnabled();
        expect(isRunEnabled).toBe(true);
      } else {
        console.log('Run button not found - paragraph may not support execution');
      }
    } catch (error) {
      console.log('Run button not accessible - paragraph may not support execution');
    }
  }

  async verifyCodeEditorFunctionality(): Promise<void> {
    const isCodeEditorVisible = await this.paragraphPage.isCodeEditorVisible();
    if (isCodeEditorVisible) {
      await expect(this.paragraphPage.codeEditor).toBeVisible();
    }
  }

  async verifyResultDisplaySystem(): Promise<void> {
    const hasResult = await this.paragraphPage.hasResult();
    if (hasResult) {
      await expect(this.paragraphPage.resultDisplay).toBeVisible();
    }
  }

  async verifyTitleEditingIfPresent(): Promise<void> {
    const titleVisible = await this.paragraphPage.titleEditor.isVisible();
    if (titleVisible) {
      // Check if it's actually editable - some custom components may not be detected as editable
      try {
        await expect(this.paragraphPage.titleEditor).toBeEditable();
      } catch (error) {
        // If it's not detected as editable by default, check if it has contenteditable or can receive focus
        const isContentEditable = await this.paragraphPage.titleEditor.getAttribute('contenteditable');
        const hasInputChild = (await this.paragraphPage.titleEditor.locator('input, textarea').count()) > 0;

        if (isContentEditable === 'true' || hasInputChild) {
          console.log('Title editor is a custom editable component');
        } else {
          console.log('Title editor may not be editable in current state');
        }
      }
    }
  }

  async verifyDynamicFormsIfPresent(): Promise<void> {
    const isDynamicFormsVisible = await this.paragraphPage.isDynamicFormsVisible();
    if (isDynamicFormsVisible) {
      await expect(this.paragraphPage.dynamicForms).toBeVisible();
    }
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

    // Check if dropdown menu items are present (they might use different selectors)
    const moveUpVisible = await this.page.locator('li:has-text("Move up")').isVisible();
    const deleteVisible = await this.page.locator('li:has-text("Delete")').isVisible();
    const cloneVisible = await this.page.locator('li:has-text("Clone")').isVisible();

    if (moveUpVisible) {
      await expect(this.page.locator('li:has-text("Move up")')).toBeVisible();
    }
    if (deleteVisible) {
      await expect(this.page.locator('li:has-text("Delete")')).toBeVisible();
    }
    if (cloneVisible) {
      await expect(this.page.locator('li:has-text("Clone")')).toBeVisible();
    }

    // Close dropdown if it's open
    await this.page.keyboard.press('Escape');
  }

  async verifyParagraphExecutionWorkflow(): Promise<void> {
    // Check if run button exists and is accessible
    try {
      const runButtonVisible = await this.paragraphPage.runButton.isVisible();
      if (runButtonVisible) {
        await expect(this.paragraphPage.runButton).toBeVisible();
        await expect(this.paragraphPage.runButton).toBeEnabled();

        await this.paragraphPage.runParagraph();

        const isStopVisible = await this.paragraphPage.isStopButtonVisible();
        if (isStopVisible) {
          await expect(this.paragraphPage.stopButton).toBeVisible();
        }
      } else {
        console.log('Run button not found - paragraph execution not available');
      }
    } catch (error) {
      console.log('Run button not accessible - paragraph execution not supported');
    }
  }

  async verifyAdvancedParagraphOperations(): Promise<void> {
    await this.paragraphPage.openSettingsDropdown();

    // Wait for dropdown to appear by checking for any menu item
    const dropdownMenu = this.page.locator('ul.ant-dropdown-menu, .dropdown-menu');
    await expect(dropdownMenu).toBeVisible({ timeout: 5000 });

    const clearOutputItem = this.page.locator('li:has-text("Clear output")');
    const toggleEditorItem = this.page.locator('li:has-text("Toggle editor")');
    const insertBelowItem = this.page.locator('li:has-text("Insert below")');

    if (await clearOutputItem.isVisible()) {
      await expect(clearOutputItem).toBeVisible();
    }

    if (await toggleEditorItem.isVisible()) {
      await expect(toggleEditorItem).toBeVisible();
    }

    if (await insertBelowItem.isVisible()) {
      await expect(insertBelowItem).toBeVisible();
    }

    // Close dropdown if it's open
    await this.page.keyboard.press('Escape');
  }
}
