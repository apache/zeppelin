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

import { Locator, Page } from '@playwright/test';
import { BasePage } from './base-page';

export class NotebookParagraphPage extends BasePage {
  readonly paragraphContainer: Locator;
  readonly addParagraphAbove: Locator;
  readonly addParagraphBelow: Locator;
  readonly titleEditor: Locator;
  readonly controlPanel: Locator;
  readonly codeEditor: Locator;
  readonly progressIndicator: Locator;
  readonly dynamicForms: Locator;
  readonly resultDisplay: Locator;
  readonly footerInfo: Locator;
  readonly runButton: Locator;
  readonly stopButton: Locator;
  readonly settingsDropdown: Locator;

  constructor(page: Page) {
    super(page);
    this.paragraphContainer = page.locator('.paragraph-container').first();
    this.addParagraphAbove = page.locator('zeppelin-notebook-add-paragraph').first();
    this.addParagraphBelow = page.locator('zeppelin-notebook-add-paragraph').last();
    this.titleEditor = page.locator('zeppelin-elastic-input').first();
    this.controlPanel = page.locator('zeppelin-notebook-paragraph-control').first();
    this.codeEditor = page.locator('zeppelin-notebook-paragraph-code-editor').first();
    this.progressIndicator = page.locator('zeppelin-notebook-paragraph-progress').first();
    this.dynamicForms = page.locator('zeppelin-notebook-paragraph-dynamic-forms').first();
    this.resultDisplay = page.locator('zeppelin-notebook-paragraph-result').first();
    this.footerInfo = page.locator('zeppelin-notebook-paragraph-footer').first();
    this.runButton = page
      .locator('.paragraph-container')
      .first()
      .locator(
        'button[nzTooltipTitle*="Run"], button[title*="Run"], button:has-text("Run"), .run-button, [aria-label*="Run"], i[nzType="play-circle"]:visible, button:has(i[nzType="play-circle"])'
      )
      .first();
    this.stopButton = page.getByRole('button', { name: 'Cancel' }).first();
    this.settingsDropdown = page
      .locator('.paragraph-container')
      .first()
      .locator('zeppelin-notebook-paragraph-control a[nz-dropdown]')
      .first();
  }

  async doubleClickToEdit(): Promise<void> {
    await this.paragraphContainer.dblclick();
  }

  async runParagraph(): Promise<void> {
    await this.runButton.click();
  }

  async openSettingsDropdown(): Promise<void> {
    await this.settingsDropdown.click();
  }

  async hasResult(): Promise<boolean> {
    return await this.resultDisplay.isVisible();
  }

  async isCodeEditorVisible(): Promise<boolean> {
    return await this.codeEditor.isVisible();
  }

  async getFooterText(): Promise<string> {
    return (await this.footerInfo.textContent()) || '';
  }

  async isRunButtonEnabled(): Promise<boolean> {
    return await this.runButton.isEnabled();
  }
}
