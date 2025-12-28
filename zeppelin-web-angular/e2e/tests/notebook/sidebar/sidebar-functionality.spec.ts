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
import { NotebookSidebarUtil } from '../../../models/notebook-sidebar-page.util';
import {
  addPageAnnotationBeforeEach,
  performLoginIfRequired,
  waitForZeppelinReady,
  PAGES,
  createTestNotebook
} from '../../../utils';

test.describe('Notebook Sidebar Functionality', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_SIDEBAR);

  let testUtil: NotebookSidebarUtil;
  let testNotebook: { noteId: string; paragraphId: string };

  test.beforeEach(async ({ page }) => {
    await page.goto('/', {
      waitUntil: 'load',
      timeout: 60000
    });
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);

    testUtil = new NotebookSidebarUtil(page);
    testNotebook = await createTestNotebook(page);

    // Navigate to the test notebook
    await page.goto(`/#/notebook/${testNotebook.noteId}`);
    await page.waitForLoadState('networkidle');
  });

  test('should display navigation buttons', async () => {
    // Then: Navigation buttons should be visible
    await testUtil.verifyNavigationButtons();
  });

  test('should manage three sidebar states correctly', async () => {
    // Then: State management should work properly
    await testUtil.verifyStateManagement();
  });

  test('should toggle between states correctly', async () => {
    // Then: Toggle behavior should work correctly
    await testUtil.verifyToggleBehavior();
  });

  test('should load TOC content properly', async () => {
    // Then: TOC content should load properly
    await testUtil.verifyTocContentLoading();
  });

  test('should load file tree content properly', async () => {
    // Then: File tree content should load properly
    await testUtil.verifyFileTreeContentLoading();
  });

  test('should support TOC item interaction', async () => {
    // Then: TOC interaction should work properly
    await testUtil.verifyTocInteraction();
  });

  test('should support file tree item interaction', async () => {
    // Then: File tree interaction should work properly
    await testUtil.verifyFileTreeInteraction();
  });

  test('should close sidebar functionality work properly', async () => {
    // Then: Close functionality should work properly
    await testUtil.verifyCloseFunctionality();
  });

  test('should verify all sidebar states comprehensively', async () => {
    // Then: All sidebar states should work properly
    await testUtil.verifyAllSidebarStates();
  });
});
