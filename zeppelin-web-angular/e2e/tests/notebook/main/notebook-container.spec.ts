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
import { NotebookPageUtil } from '../../../models/notebook-page.util';
import { PublishedParagraphTestUtil } from '../../../models/published-paragraph-page.util';
import { addPageAnnotationBeforeEach, performLoginIfRequired, waitForZeppelinReady, PAGES } from '../../../utils';

test.describe('Notebook Container Component', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK);

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

  test('should display notebook container with proper structure', async ({ page }) => {
    // Then: Notebook container should be properly structured
    const notebookUtil = new NotebookPageUtil(page);
    await notebookUtil.verifyNotebookContainerStructure();
  });

  test('should display action bar component', async ({ page }) => {
    // Then: Action bar should be displayed
    const notebookUtil = new NotebookPageUtil(page);
    await notebookUtil.verifyActionBarComponent();
  });

  test('should display resizable sidebar with width constraints', async ({ page }) => {
    // Then: Sidebar should be resizable with proper constraints
    const notebookUtil = new NotebookPageUtil(page);
    await notebookUtil.verifyResizableSidebarWithConstraints();
  });

  test('should display paragraph container with grid layout', async ({ page }) => {
    // Then: Paragraph container should have grid layout
    const notebookUtil = new NotebookPageUtil(page);
    await notebookUtil.verifyParagraphContainerGridLayout();
  });

  test('should display extension area when activated', async ({ page }) => {
    // Wait for notebook page to be fully loaded with action bar
    await page.waitForSelector('zeppelin-notebook-action-bar', { state: 'visible' });

    // Given: Click setting button to activate extension area
    await page.click('button i[nztype="setting"]');

    // Wait for extension area to appear
    await page.waitForSelector('.extension-area', { state: 'visible' });

    // Then: Extension area should be displayed when activated
    const notebookUtil = new NotebookPageUtil(page);
    await notebookUtil.verifyExtensionAreaWhenActivated();
  });
});
