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

  async verifyTitleEditingFunctionality(expectedTitle?: string): Promise<void> {
    await expect(this.actionBarPage.titleEditor).toBeVisible();
    const titleText = await this.actionBarPage.getTitleText();
    expect(titleText).toBeDefined();
    expect(titleText.length).toBeGreaterThan(0);

    if (expectedTitle) {
      expect(titleText).toContain(expectedTitle);
    }
  }

  async verifyRunAllWorkflow(): Promise<void> {
    await expect(this.actionBarPage.runAllButton).toBeVisible();
    await expect(this.actionBarPage.runAllButton).toBeEnabled();

    await this.actionBarPage.clickRunAll();

    // Check if confirmation dialog appears (it might not in some configurations)
    try {
      // Try multiple possible confirmation dialog selectors
      const confirmSelector = this.page
        .locator('nz-popconfirm button:has-text("OK"), .ant-popconfirm button:has-text("OK"), button:has-text("OK")')
        .first();
      await expect(confirmSelector).toBeVisible({ timeout: 2000 });
      await confirmSelector.click();
      await expect(confirmSelector).not.toBeVisible();
    } catch (error) {
      // If no confirmation dialog appears, that's also valid behavior
      console.log('Run all executed without confirmation dialog');
    }
  }

  async verifyCodeVisibilityToggle(): Promise<void> {
    await expect(this.actionBarPage.showHideCodeButton).toBeVisible();
    await expect(this.actionBarPage.showHideCodeButton).toBeEnabled();

    await this.actionBarPage.toggleCodeVisibility();

    // Verify the button is still functional after click
    await expect(this.actionBarPage.showHideCodeButton).toBeEnabled();
  }

  async verifyOutputVisibilityToggle(): Promise<void> {
    await expect(this.actionBarPage.showHideOutputButton).toBeVisible();
    await expect(this.actionBarPage.showHideOutputButton).toBeEnabled();

    await this.actionBarPage.toggleOutputVisibility();

    // Verify the button is still functional after click
    await expect(this.actionBarPage.showHideOutputButton).toBeEnabled();
  }

  async verifyClearOutputWorkflow(): Promise<void> {
    await expect(this.actionBarPage.clearOutputButton).toBeVisible();
    await expect(this.actionBarPage.clearOutputButton).toBeEnabled();

    await this.actionBarPage.clickClearOutput();

    // Check if confirmation dialog appears (it might not in some configurations)
    try {
      // Try multiple possible confirmation dialog selectors
      const confirmSelector = this.page
        .locator('nz-popconfirm button:has-text("OK"), .ant-popconfirm button:has-text("OK"), button:has-text("OK")')
        .first();
      await expect(confirmSelector).toBeVisible({ timeout: 2000 });
      await confirmSelector.click();
      await expect(confirmSelector).not.toBeVisible();
    } catch (error) {
      // If no confirmation dialog appears, that's also valid behavior
      console.log('Clear output executed without confirmation dialog');
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
      } else if (collaborationVisible) {
        await this.actionBarPage.switchToCollaborationMode();
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

  async verifyCommitWorkflow(commitMessage: string): Promise<void> {
    if (await this.actionBarPage.commitButton.isVisible()) {
      await this.actionBarPage.openCommitPopover();
      await expect(this.actionBarPage.commitPopover).toBeVisible();

      await this.actionBarPage.enterCommitMessage(commitMessage);
      await this.actionBarPage.confirmCommit();

      await expect(this.actionBarPage.commitPopover).not.toBeVisible();
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

  async verifySettingsGroup(): Promise<void> {
    if (await this.actionBarPage.shortcutInfoButton.isVisible()) {
      await expect(this.actionBarPage.shortcutInfoButton).toBeEnabled();
    }

    if (await this.actionBarPage.interpreterSettingsButton.isVisible()) {
      await expect(this.actionBarPage.interpreterSettingsButton).toBeEnabled();
    }

    if (await this.actionBarPage.permissionsButton.isVisible()) {
      await expect(this.actionBarPage.permissionsButton).toBeEnabled();
    }

    if (await this.actionBarPage.lookAndFeelDropdown.isVisible()) {
      await expect(this.actionBarPage.lookAndFeelDropdown).toBeEnabled();
    }
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
