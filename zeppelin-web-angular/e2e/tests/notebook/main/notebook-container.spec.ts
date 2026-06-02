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
import { NotebookPage } from '../../../models/notebook-page';
import {
  addPageAnnotationBeforeEach,
  waitForZeppelinReady,
  PAGES,
  createTestNotebook,
  navigateToNotebookWithFallback
} from '../../../utils';

test.describe('Notebook Container Component', () => {
  // JUSTIFIED: page objects and notebook ids are stored in describe scope; fullyParallel can overwrite them.
  test.describe.configure({ mode: 'default' });

  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK);

  let notebookPage: NotebookPage;
  let testNotebook: { noteId: string; paragraphId: string };

  test.beforeEach(async ({ page }) => {
    await page.goto('/#/');
    await waitForZeppelinReady(page);

    testNotebook = await createTestNotebook(page);
    notebookPage = new NotebookPage(page);

    await navigateToNotebookWithFallback(page, testNotebook.noteId);
  });

  test('should display notebook container with proper structure', async () => {
    await expect(notebookPage.notebookContainer).toBeVisible();
    expect(await notebookPage.getNotebookContainerClass()).toContain('notebook-container');
  });

  test('should display action bar component', async () => {
    await expect(notebookPage.notebookContainer).toBeVisible();
    await expect(notebookPage.actionBar).toBeVisible({ timeout: 15000 });
  });

  test('should display resizable sidebar with width constraints', async () => {
    await expect(notebookPage.notebookContainer).toBeVisible();
    await expect(notebookPage.sidebarArea).toBeVisible({ timeout: 15000 });

    const width = await notebookPage.getSidebarWidth();
    expect(width).toBeGreaterThanOrEqual(40);
    expect(width).toBeLessThanOrEqual(800);
  });

  test('should display paragraph container with grid layout', async () => {
    await expect(notebookPage.paragraphInner).toBeVisible();
    expect(await notebookPage.paragraphInner.getAttribute('class')).toContain('paragraph-inner');
    await expect(notebookPage.paragraphInner).toHaveAttribute('nz-row');
  });

  test('should display extension area when activated', async () => {
    await expect(notebookPage.notebookContainer).toBeVisible();
    await expect(notebookPage.actionBar).toBeVisible({ timeout: 15000 });

    await notebookPage.settingsButton.click();

    await expect(notebookPage.extensionArea).toBeVisible();
  });
});
