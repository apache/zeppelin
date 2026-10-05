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

import { expect, Locator, Page } from '@playwright/test';
import { BasePage } from './base-page';

type ParagraphSettingsMenuItem =
  | 'Width'
  | 'Font size'
  | 'Insert new'
  | 'Clone paragraph'
  | 'Remove'
  | 'Move paragraph up'
  | 'Move paragraph down'
  | 'Clear output'
  | 'Show Title'
  | 'Show line numbers'
  | 'Hide line numbers'
  | 'Disable run'
  | 'Enable run';

export class NotebookParagraphPage extends BasePage {
  readonly paragraphContainers: Locator;
  readonly paragraphContainer: Locator;
  readonly addParagraphAboveLink: Locator;
  readonly addParagraphBelowLink: Locator;
  readonly controlPanel: Locator;
  readonly codeEditor: Locator;
  readonly codeEditorHost: Locator;
  readonly editorInput: Locator;
  readonly editorViewLinesAll: Locator;
  readonly editorViewLines: Locator;
  readonly editorLines: Locator;
  readonly dynamicForms: Locator;
  readonly resultDisplay: Locator;
  readonly executionTime: Locator;
  readonly elapsedTime: Locator;
  readonly progressIndicator: Locator;
  readonly progressBar: Locator;
  readonly paragraphTitleText: Locator;
  readonly paragraphTitleInput: Locator;
  readonly lineNumbers: Locator;
  readonly runButton: Locator;
  readonly toggleEditorButton: Locator;
  readonly toggleOutputButton: Locator;
  readonly settingsDropdowns: Locator;
  readonly settingsMenu: Locator;
  readonly paragraphIdMenuItem: Locator;
  readonly confirmButton: Locator;
  readonly runAllButton: Locator;
  readonly status: Locator;
  readonly cancelButton: Locator;
  readonly exportDropdownTrigger: Locator;
  readonly exportMenu: Locator;
  private readonly settingsDropdown: Locator;
  private readonly addParagraphAbove: Locator;
  private readonly addParagraphBelow: Locator;

  constructor(page: Page) {
    super(page);
    this.paragraphContainers = page.locator('.paragraph-container');
    // JUSTIFIED: fresh test notebooks contain one paragraph; its container is the primary target.
    this.paragraphContainer = this.paragraphContainers.first();
    const addParagraphControls = page.locator('zeppelin-notebook-add-paragraph');
    // JUSTIFIED: the first add control inserts above the first paragraph.
    this.addParagraphAbove = addParagraphControls.first();
    // JUSTIFIED: the last add control trails the final paragraph and inserts below it.
    this.addParagraphBelow = addParagraphControls.last();
    this.addParagraphAboveLink = this.addParagraphAbove.getByText('Add Paragraph', { exact: true });
    this.addParagraphBelowLink = this.addParagraphBelow.getByText('Add Paragraph', { exact: true });
    const controlPanels = page.locator('zeppelin-notebook-paragraph-control');
    this.controlPanel = this.paragraphContainer.locator('zeppelin-notebook-paragraph-control');
    const codeEditors = page.locator('zeppelin-notebook-paragraph-code-editor');
    this.codeEditor = this.paragraphContainer.locator('zeppelin-notebook-paragraph-code-editor');
    this.codeEditorHost = this.codeEditor.locator('zeppelin-code-editor');
    this.editorInput = this.codeEditor.getByRole('textbox', { name: 'Editor content', exact: true });
    this.editorViewLines = this.codeEditor.locator('.view-lines');
    this.editorViewLinesAll = codeEditors.locator('.view-lines');
    this.editorLines = this.codeEditor.locator('.view-line');
    this.dynamicForms = this.paragraphContainer.locator('zeppelin-notebook-paragraph-dynamic-forms');
    this.resultDisplay = this.paragraphContainer.locator('zeppelin-notebook-paragraph-result');
    const footerInfo = this.paragraphContainer.getByTestId('angular-paragraph-footer');
    this.executionTime = footerInfo.locator('.execution-time');
    this.elapsedTime = footerInfo.locator('.elapsed-time');
    // JUSTIFIED: only the running paragraph renders a progress component.
    this.progressIndicator = page.locator('zeppelin-notebook-paragraph-progress').first();
    this.progressBar = this.progressIndicator.locator('.ant-progress');
    const paragraphTitle = this.paragraphContainer.locator('zeppelin-elastic-input');
    this.paragraphTitleText = paragraphTitle.locator('p');
    this.paragraphTitleInput = paragraphTitle.getByRole('textbox');
    // Monaco renders gutter line numbers inside its margin overlay.
    this.lineNumbers = this.codeEditor.locator('.margin-view-overlays .line-numbers');
    this.runButton = this.controlPanel.locator('.run-para');
    this.toggleEditorButton = this.controlPanel.locator('a[nzTooltipTitle="Show/hide the code"]');
    this.toggleOutputButton = this.controlPanel.locator('a[nzTooltipTitle="Show/hide the output"]');
    this.settingsDropdowns = controlPanels.locator('a[nz-dropdown]');
    this.settingsDropdown = this.controlPanel.locator('a[nz-dropdown]');
    this.settingsMenu = page.locator('ul.setting-menu');
    this.paragraphIdMenuItem = this.settingsMenu.locator('.paragraph-id');
    this.confirmButton = page.getByRole('button', { name: 'OK', exact: true });
    this.runAllButton = page.locator('button[nzTooltipTitle="Run all paragraphs"]');
    this.status = this.controlPanel.locator('.status');
    this.cancelButton = this.controlPanel.locator('.cancel-para');
    // The export controls render only for a TABLE result.
    this.exportDropdownTrigger = this.resultDisplay.locator('.export-dropdown-icon-btn');
    this.exportMenu = page.locator('.ant-dropdown-menu');
  }

  // The export dropdown is declared without nzTrigger, so ng-zorro opens it on hover, not click.
  async openExportMenu(): Promise<void> {
    await this.exportDropdownTrigger.hover();
    await expect(this.exportMenu).toBeVisible();
  }

  async doubleClickToEdit(): Promise<void> {
    await this.paragraphContainer.dblclick();
  }

  async clickAddParagraphAbove(): Promise<void> {
    await this.addParagraphAbove.hover();
    await this.addParagraphAboveLink.click();
  }

  async clickAddParagraphBelow(): Promise<void> {
    await this.addParagraphBelow.hover();
    await this.addParagraphBelowLink.click();
  }

  async runParagraph(): Promise<void> {
    await this.runButton.click();
  }

  settingsMenuItem(label: ParagraphSettingsMenuItem): Locator {
    return this.settingsMenu.locator('li.list-item').filter({ hasText: label });
  }

  async openSettingsDropdown(settingsDropdown: Locator = this.settingsDropdown): Promise<void> {
    await expect(this.settingsMenu).toBeHidden();
    await settingsDropdown.click();
    await expect(this.settingsMenu).toBeVisible();
  }
}
