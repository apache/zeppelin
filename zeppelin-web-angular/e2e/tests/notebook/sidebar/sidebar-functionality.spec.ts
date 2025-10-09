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
import { NotebookSidebarUtil } from '../../../models/notebook-sidebar-page.util';
import { addPageAnnotationBeforeEach, performLoginIfRequired, waitForZeppelinReady, PAGES } from '../../../utils';

test.describe('Notebook Sidebar Functionality', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_SIDEBAR);

  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
  });

  test('should display navigation buttons', async ({ page }) => {
    // Given: User is on the home page
    await page.goto('/');
    await waitForZeppelinReady(page);

    // When: User opens first available notebook
    await page.waitForSelector('a[href*="#/notebook/"]', { timeout: 10000 });
    const firstNotebookLink = page.locator('a[href*="#/notebook/"]').first();
    await expect(firstNotebookLink).toBeVisible();
    await firstNotebookLink.click();
    await page.waitForLoadState('networkidle');

    // Then: Navigation buttons should be visible
    const sidebarUtil = new NotebookSidebarUtil(page);
    await sidebarUtil.verifyNavigationButtons();
  });

  test('should manage three sidebar states correctly', async ({ page }) => {
    // Given: User is on the home page with a notebook open
    await page.goto('/');
    await waitForZeppelinReady(page);
    await page.waitForSelector('a[href*="#/notebook/"]', { timeout: 10000 });
    const firstNotebookLink = page.locator('a[href*="#/notebook/"]').first();
    await expect(firstNotebookLink).toBeVisible();
    await firstNotebookLink.click();
    await page.waitForLoadState('networkidle');

    // When: User interacts with sidebar state management
    const sidebarUtil = new NotebookSidebarUtil(page);

    // Then: State management should work properly
    await sidebarUtil.verifyStateManagement();
  });

  test('should toggle between states correctly', async ({ page }) => {
    // Given: User is on the home page with a notebook open
    await page.goto('/');
    await waitForZeppelinReady(page);
    await page.waitForSelector('a[href*="#/notebook/"]', { timeout: 10000 });
    const firstNotebookLink = page.locator('a[href*="#/notebook/"]').first();
    await expect(firstNotebookLink).toBeVisible();
    await firstNotebookLink.click();
    await page.waitForLoadState('networkidle');

    // When: User toggles between different sidebar states
    const sidebarUtil = new NotebookSidebarUtil(page);

    // Then: Toggle behavior should work correctly
    await sidebarUtil.verifyToggleBehavior();
  });

  test('should load TOC content properly', async ({ page }) => {
    // Given: User is on the home page with a notebook open
    await page.goto('/');
    await waitForZeppelinReady(page);
    await page.waitForSelector('a[href*="#/notebook/"]', { timeout: 10000 });
    const firstNotebookLink = page.locator('a[href*="#/notebook/"]').first();
    await expect(firstNotebookLink).toBeVisible();
    await firstNotebookLink.click();
    await page.waitForLoadState('networkidle');

    // When: User opens TOC
    const sidebarUtil = new NotebookSidebarUtil(page);

    // Then: TOC content should load properly
    await sidebarUtil.verifyTocContentLoading();
  });

  test('should load file tree content properly', async ({ page }) => {
    // Given: User is on the home page with a notebook open
    await page.goto('/');
    await waitForZeppelinReady(page);
    await page.waitForSelector('a[href*="#/notebook/"]', { timeout: 10000 });
    const firstNotebookLink = page.locator('a[href*="#/notebook/"]').first();
    await expect(firstNotebookLink).toBeVisible();
    await firstNotebookLink.click();
    await page.waitForLoadState('networkidle');

    // When: User opens file tree
    const sidebarUtil = new NotebookSidebarUtil(page);

    // Then: File tree content should load properly
    await sidebarUtil.verifyFileTreeContentLoading();
  });

  test('should support TOC item interaction', async ({ page }) => {
    // Given: User is on the home page with a notebook open
    await page.goto('/');
    await waitForZeppelinReady(page);
    await page.waitForSelector('a[href*="#/notebook/"]', { timeout: 10000 });
    const firstNotebookLink = page.locator('a[href*="#/notebook/"]').first();
    await expect(firstNotebookLink).toBeVisible();
    await firstNotebookLink.click();
    await page.waitForLoadState('networkidle');

    // When: User interacts with TOC items
    const sidebarUtil = new NotebookSidebarUtil(page);

    // Then: TOC interaction should work properly
    await sidebarUtil.verifyTocInteraction();
  });

  test('should support file tree item interaction', async ({ page }) => {
    // Given: User is on the home page with a notebook open
    await page.goto('/');
    await waitForZeppelinReady(page);
    await page.waitForSelector('a[href*="#/notebook/"]', { timeout: 10000 });
    const firstNotebookLink = page.locator('a[href*="#/notebook/"]').first();
    await expect(firstNotebookLink).toBeVisible();
    await firstNotebookLink.click();
    await page.waitForLoadState('networkidle');

    // When: User interacts with file tree items
    const sidebarUtil = new NotebookSidebarUtil(page);

    // Then: File tree interaction should work properly
    await sidebarUtil.verifyFileTreeInteraction();
  });

  test('should close sidebar functionality work properly', async ({ page }) => {
    // Given: User is on the home page with a notebook open
    await page.goto('/');
    await waitForZeppelinReady(page);
    await page.waitForSelector('a[href*="#/notebook/"]', { timeout: 10000 });
    const firstNotebookLink = page.locator('a[href*="#/notebook/"]').first();
    await expect(firstNotebookLink).toBeVisible();
    await firstNotebookLink.click();
    await page.waitForLoadState('networkidle');

    // When: User closes the sidebar
    const sidebarUtil = new NotebookSidebarUtil(page);

    // Then: Close functionality should work properly
    await sidebarUtil.verifyCloseFunctionality();
  });

  test('should verify all sidebar states comprehensively', async ({ page }) => {
    // Given: User is on the home page with a notebook open
    await page.goto('/');
    await waitForZeppelinReady(page);
    await page.waitForSelector('a[href*="#/notebook/"]', { timeout: 10000 });
    const firstNotebookLink = page.locator('a[href*="#/notebook/"]').first();
    await expect(firstNotebookLink).toBeVisible();
    await firstNotebookLink.click();
    await page.waitForLoadState('networkidle');

    // When: User tests all sidebar states
    const sidebarUtil = new NotebookSidebarUtil(page);

    // Then: All sidebar states should work properly
    await sidebarUtil.verifyAllSidebarStates();
  });
});
