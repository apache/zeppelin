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

export class NotebookActionBarPage extends BasePage {
  readonly titleEditor: Locator;
  readonly titleTooltip: Locator;
  readonly runAllButton: Locator;
  readonly runAllConfirm: Locator;
  readonly showHideCodeButton: Locator;
  readonly showHideOutputButton: Locator;
  readonly clearOutputButton: Locator;
  readonly clearOutputConfirm: Locator;
  readonly cloneButton: Locator;
  readonly exportButton: Locator;
  readonly reloadButton: Locator;
  readonly collaborationModeToggle: Locator;
  readonly personalModeButton: Locator;
  readonly collaborationModeButton: Locator;
  readonly commitButton: Locator;
  readonly commitPopover: Locator;
  readonly commitMessageInput: Locator;
  readonly commitConfirmButton: Locator;
  readonly setRevisionButton: Locator;
  readonly compareRevisionsButton: Locator;
  readonly revisionDropdown: Locator;
  readonly revisionDropdownMenu: Locator;
  readonly schedulerButton: Locator;
  readonly schedulerDropdown: Locator;
  readonly cronInput: Locator;
  readonly cronPresets: Locator;
  readonly shortcutInfoButton: Locator;
  readonly interpreterSettingsButton: Locator;
  readonly permissionsButton: Locator;
  readonly lookAndFeelDropdown: Locator;

  constructor(page: Page) {
    super(page);
    this.titleEditor = page.locator('zeppelin-elastic-input');
    this.titleTooltip = page.locator('[nzTooltipTitle]');
    this.runAllButton = page.locator('button[nzTooltipTitle="Run all paragraphs"]');
    this.runAllConfirm = page.locator('nz-popconfirm').getByRole('button', { name: 'OK' });
    this.showHideCodeButton = page.locator('button[nzTooltipTitle="Show/hide the code"]');
    this.showHideOutputButton = page.locator('button[nzTooltipTitle="Show/hide the output"]');
    this.clearOutputButton = page.locator('button[nzTooltipTitle="Clear all output"]');
    this.clearOutputConfirm = page.locator('nz-popconfirm').getByRole('button', { name: 'OK' });
    this.cloneButton = page.locator('button[nzTooltipTitle="Clone this note"]');
    this.exportButton = page.locator('button[nzTooltipTitle="Export this note"]');
    this.reloadButton = page.locator('button[nzTooltipTitle="Reload from note file"]');
    this.collaborationModeToggle = page.locator('ng-container[ngSwitch="note.config.personalizedMode"]');
    this.personalModeButton = page.getByRole('button', { name: 'Personal' });
    this.collaborationModeButton = page.getByRole('button', { name: 'Collaboration' });
    this.commitButton = page.getByRole('button', { name: 'Commit' });
    this.commitPopover = page.locator('.ant-popover');
    this.commitMessageInput = page.locator('input[placeholder*="commit message"]');
    this.commitConfirmButton = page.locator('.ant-popover').getByRole('button', { name: 'OK' });
    this.setRevisionButton = page.getByRole('button', { name: 'Set as default revision' });
    this.compareRevisionsButton = page.getByRole('button', { name: 'Compare with current revision' });
    this.revisionDropdown = page.locator('button[nz-dropdown]').filter({ hasText: 'Revision' });
    this.revisionDropdownMenu = page.locator('nz-dropdown-menu');
    this.schedulerButton = page.locator('button[nz-dropdown]').filter({ hasText: 'Scheduler' });
    this.schedulerDropdown = page.locator('.scheduler-dropdown');
    this.cronInput = page.locator('input[placeholder*="cron"]');
    this.cronPresets = page.locator('.cron-preset');
    this.shortcutInfoButton = page.getByRole('button', { name: 'Shortcut list' });
    this.interpreterSettingsButton = page.getByRole('button', { name: 'Interpreter binding' });
    this.permissionsButton = page.getByRole('button', { name: 'Permissions' });
    this.lookAndFeelDropdown = page.locator('button[nz-dropdown]').filter({ hasText: 'Look & feel' });
  }

  async clickRunAll(): Promise<void> {
    await this.runAllButton.click();
  }

  async confirmRunAll(): Promise<void> {
    await this.runAllConfirm.click();
  }

  async toggleCodeVisibility(): Promise<void> {
    await this.showHideCodeButton.click();
  }

  async toggleOutputVisibility(): Promise<void> {
    await this.showHideOutputButton.click();
  }

  async clickClearOutput(): Promise<void> {
    await this.clearOutputButton.click();
  }

  async confirmClearOutput(): Promise<void> {
    await this.clearOutputConfirm.click();
  }

  async clickClone(): Promise<void> {
    await this.cloneButton.click();
  }

  async clickExport(): Promise<void> {
    await this.exportButton.click();
  }

  async clickReload(): Promise<void> {
    await this.reloadButton.click();
  }

  async switchToPersonalMode(): Promise<void> {
    await this.personalModeButton.click();
  }

  async switchToCollaborationMode(): Promise<void> {
    await this.collaborationModeButton.click();
  }

  async openCommitPopover(): Promise<void> {
    await this.commitButton.click();
  }

  async enterCommitMessage(message: string): Promise<void> {
    await this.commitMessageInput.fill(message);
  }

  async confirmCommit(): Promise<void> {
    await this.commitConfirmButton.click();
  }

  async setAsDefaultRevision(): Promise<void> {
    await this.setRevisionButton.click();
  }

  async compareWithCurrentRevision(): Promise<void> {
    await this.compareRevisionsButton.click();
  }

  async openRevisionDropdown(): Promise<void> {
    await this.revisionDropdown.click();
  }

  async openSchedulerDropdown(): Promise<void> {
    await this.schedulerButton.click();
  }

  async enterCronExpression(expression: string): Promise<void> {
    await this.cronInput.fill(expression);
  }

  async selectCronPreset(preset: string): Promise<void> {
    await this.cronPresets.filter({ hasText: preset }).click();
  }

  async openShortcutInfo(): Promise<void> {
    await this.shortcutInfoButton.click();
  }

  async openInterpreterSettings(): Promise<void> {
    await this.interpreterSettingsButton.click();
  }

  async openPermissions(): Promise<void> {
    await this.permissionsButton.click();
  }

  async openLookAndFeelDropdown(): Promise<void> {
    await this.lookAndFeelDropdown.click();
  }

  async getTitleText(): Promise<string> {
    return (await this.titleEditor.textContent()) || '';
  }

  async isRunAllEnabled(): Promise<boolean> {
    return await this.runAllButton.isEnabled();
  }

  async isCodeVisible(): Promise<boolean> {
    const icon = this.showHideCodeButton.locator('i[nz-icon]');
    const iconType = await icon.getAttribute('nztype');
    return iconType === 'fullscreen-exit';
  }

  async isOutputVisible(): Promise<boolean> {
    const icon = this.showHideOutputButton.locator('i[nz-icon]');
    const iconType = await icon.getAttribute('nztype');
    return iconType === 'read';
  }
}
