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

import { test } from '@playwright/test';
import { NotebookActionBarUtil } from '../../../models/notebook-action-bar-page.util';
import { PublishedParagraphTestUtil } from '../../../models/published-paragraph-page.util';
import { addPageAnnotationBeforeEach, performLoginIfRequired, waitForZeppelinReady, PAGES } from '../../../utils';

test.describe('Notebook Action Bar Functionality', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_ACTION_BAR);

  let testUtil: PublishedParagraphTestUtil;
  let testNotebook: { noteId: string; paragraphId: string };

  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);

    testUtil = new PublishedParagraphTestUtil(page);
    testNotebook = await testUtil.createTestNotebook();

    // Navigate to the test notebook
    await page.goto(`/#/notebook/${testNotebook.noteId}`);
    await page.waitForLoadState('networkidle');
  });

  test.afterEach(async () => {
    if (testNotebook?.noteId) {
      await testUtil.deleteTestNotebook(testNotebook.noteId);
    }
  });

  test('should display and allow title editing with tooltip', async ({ page }) => {
    // Then: Title editor should be functional with proper tooltip
    const actionBarUtil = new NotebookActionBarUtil(page);
    const notebookName = `Test Notebook ${Date.now()}`;
    await actionBarUtil.verifyTitleEditingFunctionality(notebookName);
  });

  test('should execute run all paragraphs workflow', async ({ page }) => {
    // Then: Run all workflow should complete successfully
    const actionBarUtil = new NotebookActionBarUtil(page);
    await actionBarUtil.verifyRunAllWorkflow();
  });

  test('should toggle code visibility', async ({ page }) => {
    // Then: Code visibility should toggle properly
    const actionBarUtil = new NotebookActionBarUtil(page);
    await actionBarUtil.verifyCodeVisibilityToggle();
  });

  test('should toggle output visibility', async ({ page }) => {
    // Then: Output visibility toggle should work properly
    const actionBarUtil = new NotebookActionBarUtil(page);
    await actionBarUtil.verifyOutputVisibilityToggle();
  });

  test('should execute clear output workflow', async ({ page }) => {
    // Then: Clear output workflow should function properly
    const actionBarUtil = new NotebookActionBarUtil(page);
    await actionBarUtil.verifyClearOutputWorkflow();
  });

  test('should display note management buttons', async ({ page }) => {
    // Then: Note management buttons should be displayed
    const actionBarUtil = new NotebookActionBarUtil(page);
    await actionBarUtil.verifyNoteManagementButtons();
  });

  test('should handle collaboration mode toggle', async ({ page }) => {
    // Then: Collaboration mode toggle should be handled properly
    const actionBarUtil = new NotebookActionBarUtil(page);
    await actionBarUtil.verifyCollaborationModeToggle();
  });

  test('should handle revision controls when supported', async ({ page }) => {
    // Then: Revision controls should be handled when supported
    const actionBarUtil = new NotebookActionBarUtil(page);
    await actionBarUtil.verifyRevisionControlsIfSupported();
  });

  test('should handle scheduler controls when enabled', async ({ page }) => {
    // Then: Scheduler controls should be handled when enabled
    const actionBarUtil = new NotebookActionBarUtil(page);
    await actionBarUtil.verifySchedulerControlsIfEnabled();
  });

  test('should display settings group properly', async ({ page }) => {
    // Then: Settings group should be displayed properly
    const actionBarUtil = new NotebookActionBarUtil(page);
    await actionBarUtil.verifySettingsGroup();
  });

  test('should verify all action bar functionality', async ({ page }) => {
    // Then: All action bar functionality should work properly
    const actionBarUtil = new NotebookActionBarUtil(page);
    await actionBarUtil.verifyAllActionBarFunctionality();
  });
});
