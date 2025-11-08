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
import { NotebookActionBarPage } from './notebook-action-bar-page';

export class NotebookActionBarUtil {
  private page: Page;
  private actionBarPage: NotebookActionBarPage;

  constructor(page: Page) {
    this.page = page;
    this.actionBarPage = new NotebookActionBarPage(page);
  }

  private async handleOptionalConfirmation(logMessage: string): Promise<void> {
    const confirmSelector = this.page
      .locator('nz-popconfirm button:has-text("OK"), .ant-popconfirm button:has-text("OK"), button:has-text("OK")')
      .first();

    if (await confirmSelector.isVisible({ timeout: 2000 })) {
      await confirmSelector.click();
      await expect(confirmSelector).not.toBeVisible();
    } else {
      console.log(logMessage);
    }
  }

  async verifyTitleEditingFunctionality(newTitle: string): Promise<void> {
    await expect(this.actionBarPage.titleEditor).toBeVisible();

    await this.actionBarPage.titleEditor.click();

    const titleInputField = this.actionBarPage.titleEditor.locator('input');
    await expect(titleInputField).toBeVisible();

    await titleInputField.fill(newTitle);

    await this.page.keyboard.press('Enter');

    await expect(this.actionBarPage.titleEditor).toHaveText(newTitle, { timeout: 10000 });
  }

  async verifyRunAllWorkflow(): Promise<void> {
    await expect(this.actionBarPage.runAllButton).toBeVisible();
    await expect(this.actionBarPage.runAllButton).toBeEnabled();

    await this.actionBarPage.clickRunAll();

    // Check if confirmation dialog appears (it might not in some configurations)
    await this.handleOptionalConfirmation('Run all executed without confirmation dialog');
  }

  async verifyCodeVisibilityToggle(): Promise<void> {
    await expect(this.actionBarPage.showHideCodeButton).toBeVisible();
    await expect(this.actionBarPage.showHideCodeButton).toBeEnabled();

    const initialCodeVisibility = await this.actionBarPage.isCodeVisible();
    await this.actionBarPage.toggleCodeVisibility();

    // Wait for the icon to change by checking for the expected icon
    const expectedIcon = initialCodeVisibility ? 'fullscreen' : 'fullscreen-exit';
    const icon = this.actionBarPage.showHideCodeButton.locator('i[nz-icon] svg');
    await expect(icon).toHaveAttribute('data-icon', expectedIcon, { timeout: 5000 });

    const newCodeVisibility = await this.actionBarPage.isCodeVisible();
    expect(newCodeVisibility).toBe(!initialCodeVisibility);

    // Verify the button is still functional after click
    await expect(this.actionBarPage.showHideCodeButton).toBeEnabled();
  }

  async verifyOutputVisibilityToggle(): Promise<void> {
    await expect(this.actionBarPage.showHideOutputButton).toBeVisible();
    await expect(this.actionBarPage.showHideOutputButton).toBeEnabled();

    const initialOutputVisibility = await this.actionBarPage.isOutputVisible();
    await this.actionBarPage.toggleOutputVisibility();

    // Wait for the icon to change by checking for the expected icon
    const expectedIcon = initialOutputVisibility ? 'book' : 'read';
    const icon = this.actionBarPage.showHideOutputButton.locator('i[nz-icon] svg');
    await expect(icon).toHaveAttribute('data-icon', expectedIcon, { timeout: 5000 });

    const newOutputVisibility = await this.actionBarPage.isOutputVisible();
    expect(newOutputVisibility).toBe(!initialOutputVisibility);

    // Verify the button is still functional after click
    await expect(this.actionBarPage.showHideOutputButton).toBeEnabled();
  }

  async verifyClearOutputWorkflow(): Promise<void> {
    await expect(this.actionBarPage.clearOutputButton).toBeVisible();
    await expect(this.actionBarPage.clearOutputButton).toBeEnabled();

    await this.actionBarPage.clickClearOutput();

    // Check if confirmation dialog appears (it might not in some configurations)
    await this.handleOptionalConfirmation('Clear output executed without confirmation dialog');

    // Verify that paragraph outputs are actually cleared
    await this.page.waitForLoadState('networkidle');
    const paragraphResults = this.page.locator('zeppelin-notebook-paragraph-result');
    const resultCount = await paragraphResults.count();

    if (resultCount > 0) {
      // If results exist, check that they are empty or hidden
      for (let i = 0; i < resultCount; i++) {
        const result = paragraphResults.nth(i);
        const isVisible = await result.isVisible();
        if (isVisible) {
          // Result is visible, check if it's empty
          const textContent = await result.textContent();
          expect(textContent?.trim() || '').toBe('');
        }
      }
    }
  }

  async verifyNoteManagementButtons(): Promise<void> {
    await expect(this.actionBarPage.cloneButton).toBeVisible();
    await expect(this.actionBarPage.exportButton).toBeVisible();
    await expect(this.actionBarPage.reloadButton).toBeVisible();
  }

  async verifyCollaborationModeToggle(): Promise<void> {
    if (await this.actionBarPage.collaborationModeToggle.isVisible()) {
      const personalVisible = await this.actionBarPage.personalModeButton.isVisible();
      const collaborationVisible = await this.actionBarPage.collaborationModeButton.isVisible();

      expect(personalVisible || collaborationVisible).toBe(true);

      if (personalVisible) {
        await this.actionBarPage.switchToPersonalMode();
        // Verify the switch was successful - collaboration button should now be visible
        await expect(this.actionBarPage.collaborationModeButton).toBeVisible({ timeout: 5000 });
      } else if (collaborationVisible) {
        await this.actionBarPage.switchToCollaborationMode();
        // Verify the switch was successful - personal button should now be visible
        await expect(this.actionBarPage.personalModeButton).toBeVisible({ timeout: 5000 });
      }
    }
  }

  async verifyRevisionControlsIfSupported(): Promise<void> {
    if (await this.actionBarPage.commitButton.isVisible()) {
      await expect(this.actionBarPage.commitButton).toBeEnabled();

      if (await this.actionBarPage.setRevisionButton.isVisible()) {
        await expect(this.actionBarPage.setRevisionButton).toBeEnabled();
      }

      if (await this.actionBarPage.compareRevisionsButton.isVisible()) {
        await expect(this.actionBarPage.compareRevisionsButton).toBeEnabled();
      }

      if (await this.actionBarPage.revisionDropdown.isVisible()) {
        await this.actionBarPage.openRevisionDropdown();
        await expect(this.actionBarPage.revisionDropdownMenu).toBeVisible();
      }
    }
  }

  async verifySchedulerControlsIfEnabled(): Promise<void> {
    if (await this.actionBarPage.schedulerButton.isVisible()) {
      await this.actionBarPage.openSchedulerDropdown();
      await expect(this.actionBarPage.schedulerDropdown).toBeVisible();

      if (await this.actionBarPage.cronInput.isVisible()) {
        await expect(this.actionBarPage.cronInput).toBeEditable();
      }

      if (await this.actionBarPage.cronPresets.first().isVisible()) {
        const presetsCount = await this.actionBarPage.cronPresets.count();
        expect(presetsCount).toBeGreaterThan(0);
      }
    }
  }

  async verifyActionBarPresence(): Promise<void> {
    // Wait for the action bar to be visible before checking its components
    const actionBar = this.page.locator('zeppelin-notebook-action-bar');
    await expect(actionBar).toBeVisible({ timeout: 15000 });
  }

  async verifySettingsGroup(): Promise<void> {
    // Settings buttons may be conditionally displayed based on permissions/configuration
    // At minimum, at least one settings control should be available
    const settingsControls = [
      this.actionBarPage.shortcutInfoButton,
      this.actionBarPage.interpreterSettingsButton,
      this.actionBarPage.permissionsButton,
      this.actionBarPage.lookAndFeelDropdown
    ];

    let visibleControlsCount = 0;
    for (const control of settingsControls) {
      const isVisible = await control.isVisible();
      if (isVisible) {
        visibleControlsCount++;
        await expect(control).toBeEnabled();
        console.log(`Settings control is visible and enabled: ${control}`);
      }
    }

    // Verify at least one settings control is available
    expect(visibleControlsCount).toBeGreaterThan(0);
    console.log(`Total visible settings controls: ${visibleControlsCount}`);
  }

  async verifyAllActionBarFunctionality(): Promise<void> {
    await this.verifyNoteManagementButtons();
    await this.verifyCodeVisibilityToggle();
    await this.verifyOutputVisibilityToggle();
    await this.verifyCollaborationModeToggle();
    await this.verifyRevisionControlsIfSupported();
    await this.verifySchedulerControlsIfEnabled();
    await this.verifySettingsGroup();
  }
}
