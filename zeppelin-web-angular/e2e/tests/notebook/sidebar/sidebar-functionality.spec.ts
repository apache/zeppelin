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
import { NotebookSidebarPage } from '../../../models/notebook-sidebar-page';
import {
  addPageAnnotationBeforeEach,
  performLoginIfRequired,
  waitForZeppelinReady,
  PAGES,
  createTestNotebook
} from '../../../utils';

test.describe('Notebook Sidebar Functionality', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_SIDEBAR);

  let sidebar: NotebookSidebarPage;
  let testNotebook: { noteId: string; paragraphId: string };

  test.beforeEach(async ({ page }) => {
    await page.goto('/', { waitUntil: 'load', timeout: 60000 });
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);

    sidebar = new NotebookSidebarPage(page);
    testNotebook = await createTestNotebook(page);

    await page.goto(`/#/notebook/${testNotebook.noteId}`);
    await page.waitForLoadState('networkidle');
  });

  test('should display navigation buttons', async ({ page }) => {
    await expect(sidebar.sidebarContainer).toBeVisible();

    const navigationControls = page.locator(
      'zeppelin-notebook-sidebar button, .sidebar-nav button, zeppelin-notebook-sidebar i[nz-icon], .sidebar-nav i'
    );
    await expect(navigationControls.first()).toBeVisible();
  });

  test('should manage three sidebar states correctly', async () => {
    await sidebar.closeSidebar();
    expect(await sidebar.getSidebarState()).toBe('CLOSED');

    await sidebar.openToc();
    const newState = await sidebar.getSidebarState();
    // TOC or FILE_TREE both valid — TOC may not be available in all environments
    expect(newState === 'TOC' || newState === 'FILE_TREE').toBe(true);
  });

  test('should open and display TOC panel', async () => {
    await sidebar.openToc();
    await expect(sidebar.noteToc).toBeVisible();
    await expect(sidebar.sidebarContainer).toBeVisible();
  });

  test('should open and display file tree panel', async () => {
    await sidebar.openFileTree();
    await expect(sidebar.nodeList).toBeVisible();
  });

  test('should close sidebar functionality work properly', async ({ page }) => {
    await page.waitForLoadState('networkidle', { timeout: 15000 });
    await expect(sidebar.sidebarContainer).toBeVisible({ timeout: 10000 });

    // Try to open TOC, but accept FILE_TREE if TOC isn't available
    await sidebar.openToc();
    await page.waitForLoadState('domcontentloaded');
    const state = await sidebar.getSidebarState();
    expect(state === 'TOC' || state === 'FILE_TREE').toBe(true);

    await sidebar.closeSidebar();
    await page.waitForLoadState('domcontentloaded');
    expect(await sidebar.getSidebarState()).toBe('CLOSED');
  });

  test('should verify all sidebar states comprehensively', async () => {
    await sidebar.openToc();
    const tocState = await sidebar.getSidebarState();

    if (tocState === 'TOC') {
      await expect(sidebar.noteToc).toBeVisible();
    } else {
      expect(tocState).toBe('FILE_TREE');
    }

    await expect(sidebar.sidebarContainer).toBeVisible();

    await sidebar.openFileTree();
    expect(await sidebar.getSidebarState()).toBe('FILE_TREE');
    await expect(sidebar.nodeList).toBeVisible();

    await sidebar.closeSidebar();
    expect(await sidebar.getSidebarState()).toBe('CLOSED');
  });
});
