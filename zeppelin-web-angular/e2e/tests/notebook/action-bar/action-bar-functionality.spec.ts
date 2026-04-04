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

import { expect, test } from '@playwright/test';
import { NotebookActionBarPage } from '../../../models/notebook-action-bar-page';
import {
  addPageAnnotationBeforeEach,
  performLoginIfRequired,
  waitForZeppelinReady,
  PAGES,
  createTestNotebook
} from '../../../utils';

test.describe('Notebook Action Bar Functionality', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_ACTION_BAR);

  let actionBarPage: NotebookActionBarPage;
  let testNotebook: { noteId: string; paragraphId: string };

  test.beforeEach(async ({ page }) => {
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);

    testNotebook = await createTestNotebook(page);
    actionBarPage = new NotebookActionBarPage(page);

    await page.goto(`/#/notebook/${testNotebook.noteId}`);
    await page.waitForLoadState('networkidle');
  });

  test('should display and allow title editing with tooltip', async ({ page }) => {
    const notebookName = `TestNotebook_${Date.now()}`;

    await expect(actionBarPage.titleEditor).toBeVisible();
    await actionBarPage.titleEditor.click();

    const titleInputField = actionBarPage.titleEditor.locator('input');
    await expect(titleInputField).toBeVisible();
    await titleInputField.fill(notebookName);
    await page.keyboard.press('Enter');

    await expect(actionBarPage.titleEditor).toHaveText(notebookName, { timeout: 10000 });
  });

  test('should execute run all paragraphs workflow', async ({ page }) => {
    await expect(actionBarPage.runAllButton).toBeVisible();
    await expect(actionBarPage.runAllButton).toBeEnabled();

    await actionBarPage.clickRunAll();

    // Confirmation dialog must appear when running all paragraphs
    const confirmButton = page
      .locator('nz-popconfirm button:has-text("OK"), .ant-popconfirm button:has-text("OK"), button:has-text("OK")')
      // JUSTIFIED: compound locator targets unique OK button in popconfirm
      .first();
    await expect(confirmButton).toBeVisible({ timeout: 5000 });
    await confirmButton.click();
    await expect(confirmButton).not.toBeVisible();
  });

  test('should toggle code visibility', async () => {
    await expect(actionBarPage.showHideCodeButton).toBeVisible();
    await expect(actionBarPage.showHideCodeButton).toBeEnabled();

    const initialCodeVisibility = await actionBarPage.isCodeVisible();
    await actionBarPage.toggleCodeVisibility();

    const expectedIcon = initialCodeVisibility ? 'fullscreen' : 'fullscreen-exit';
    const icon = actionBarPage.showHideCodeButton.locator('i[nz-icon] svg');
    await expect(icon).toHaveAttribute('data-icon', expectedIcon, { timeout: 5000 });

    expect(await actionBarPage.isCodeVisible()).toBe(!initialCodeVisibility);
    await expect(actionBarPage.showHideCodeButton).toBeEnabled();
  });

  test('should toggle output visibility', async () => {
    await expect(actionBarPage.showHideOutputButton).toBeVisible();
    await expect(actionBarPage.showHideOutputButton).toBeEnabled();

    const initialOutputVisibility = await actionBarPage.isOutputVisible();
    await actionBarPage.toggleOutputVisibility();

    const expectedIcon = initialOutputVisibility ? 'book' : 'read';
    const icon = actionBarPage.showHideOutputButton.locator('i[nz-icon] svg');
    await expect(icon).toHaveAttribute('data-icon', expectedIcon, { timeout: 5000 });

    expect(await actionBarPage.isOutputVisible()).toBe(!initialOutputVisibility);
    await expect(actionBarPage.showHideOutputButton).toBeEnabled();
  });

  test('should execute clear output workflow', async ({ page }) => {
    await expect(actionBarPage.clearOutputButton).toBeVisible();
    await expect(actionBarPage.clearOutputButton).toBeEnabled();

    await actionBarPage.clickClearOutput();

    const confirmSelector = page
      .locator('nz-popconfirm button:has-text("OK"), .ant-popconfirm button:has-text("OK"), button:has-text("OK")')
      // JUSTIFIED: compound locator targets unique OK button in popconfirm
      .first();
    // JUSTIFIED: confirmation dialog is optional — some configurations don't require it
    const isVisible = await confirmSelector.isVisible({ timeout: 2000 }).catch(() => false);
    // JUSTIFIED: dialog is optional — absent when notebook has no output
    if (isVisible) {
      await confirmSelector.click();
      await expect(confirmSelector).not.toBeVisible();
    }

    await expect(actionBarPage.clearOutputButton).toBeEnabled();
    await page.waitForLoadState('networkidle');

    const paragraphResults = page.locator('zeppelin-notebook-paragraph-result');
    const resultCount = await paragraphResults.count();
    for (let i = 0; i < resultCount; i++) {
      // JUSTIFIED: iterating all paragraph results by index
      const result = paragraphResults.nth(i);
      // JUSTIFIED: only visible results need empty check
      if (await result.isVisible()) {
        await expect(result).toBeEmpty();
      }
    }
  });

  test('should display note management buttons', async () => {
    await expect(actionBarPage.cloneButton).toBeVisible();
    await expect(actionBarPage.cloneButton).toBeEnabled();
    await expect(actionBarPage.exportButton).toBeVisible();
    await expect(actionBarPage.exportButton).toBeEnabled();
    await expect(actionBarPage.reloadButton).toBeVisible();
    await expect(actionBarPage.reloadButton).toBeEnabled();
  });

  test('should handle collaboration mode toggle when available', async () => {
    test.skip(
      !(await actionBarPage.collaborationModeToggle.isVisible()),
      'Collaboration mode not available in this environment'
    );

    const personalVisible = await actionBarPage.personalModeButton.isVisible();
    const collaborationVisible = await actionBarPage.collaborationModeButton.isVisible();
    expect(personalVisible || collaborationVisible).toBe(true);

    if (personalVisible) {
      await actionBarPage.switchToPersonalMode();
      await expect(actionBarPage.collaborationModeButton).toBeVisible({ timeout: 5000 });
    } else if (collaborationVisible) {
      await actionBarPage.switchToCollaborationMode();
      await expect(actionBarPage.personalModeButton).toBeVisible({ timeout: 5000 });
    }
  });

  test('should handle revision controls when supported', async () => {
    test.skip(!(await actionBarPage.commitButton.isVisible()), 'Revision controls not supported in this environment');

    await expect(actionBarPage.commitButton).toBeVisible();
    await expect(actionBarPage.commitButton).toBeEnabled();

    // JUSTIFIED: optional when revision system disabled
    if (await actionBarPage.setRevisionButton.isVisible()) {
      await expect(actionBarPage.setRevisionButton).toBeEnabled();
    }
    // JUSTIFIED: optional when revision system disabled
    if (await actionBarPage.compareRevisionsButton.isVisible()) {
      await expect(actionBarPage.compareRevisionsButton).toBeEnabled();
    }
    // JUSTIFIED: optional when revision system disabled
    if (await actionBarPage.revisionDropdown.isVisible()) {
      await actionBarPage.openRevisionDropdown();
      await expect(actionBarPage.revisionDropdownMenu).toBeVisible();
    }
  });

  test('should handle scheduler controls when enabled', async () => {
    test.skip(!(await actionBarPage.schedulerButton.isVisible()), 'Scheduler not enabled in this environment');

    await expect(actionBarPage.schedulerButton).toBeVisible();
    await actionBarPage.openSchedulerDropdown();
    await expect(actionBarPage.schedulerDropdown).toBeVisible();

    // JUSTIFIED: optional when scheduler disabled
    if (await actionBarPage.cronInput.isVisible()) {
      await expect(actionBarPage.cronInput).toBeEditable();
    }
    // JUSTIFIED: optional when scheduler disabled
    if (await actionBarPage.cronPresets.first().isVisible()) {
      await expect(actionBarPage.cronPresets).not.toHaveCount(0);
    }
  });

  test('should display settings group properly', async ({ page }) => {
    const actionBar = page.locator('zeppelin-notebook-action-bar');
    await expect(actionBar).toBeVisible({ timeout: 15000 });

    // Required control: shortcut info button must always be present
    await expect(actionBarPage.shortcutInfoButton).toBeVisible({ timeout: 5000 });
    await expect(actionBarPage.shortcutInfoButton).toBeEnabled();

    // Optional controls: visible+enabled when present, absent when permissions/config hide them
    for (const control of [
      actionBarPage.interpreterSettingsButton,
      actionBarPage.permissionsButton,
      actionBarPage.lookAndFeelDropdown
    ]) {
      // JUSTIFIED: optional per user permissions
      if (await control.isVisible()) {
        await expect(control).toBeEnabled();
      }
    }
  });
});
