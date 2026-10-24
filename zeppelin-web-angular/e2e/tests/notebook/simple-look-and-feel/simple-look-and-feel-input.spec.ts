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

import { devices, expect, Locator, Page, test } from '@playwright/test';

import { NotebookActionBarPage } from '../../../models/notebook-action-bar-page';
import { NotebookSidebarPage } from '../../../models/notebook-sidebar-page';
import {
  addPageAnnotationBeforeEach,
  createTestNotebook,
  navigateToNotebookWithFallback,
  PAGES,
  waitForZeppelinReady
} from '../../../utils';

const lookAndFeelButton = (page: Page): Locator => page.locator('button[nz-dropdown]:has(i[nzType="down"])').last();

const setLookAndFeel = async (page: Page, value: 'default' | 'simple'): Promise<void> => {
  await lookAndFeelButton(page).click();
  await page.locator('li[nz-menu-item]').filter({ hasText: value }).last().click();
  await expect(lookAndFeelButton(page)).toContainText(value, { timeout: 15000 });
};

const openTestNotebook = async (page: Page): Promise<void> => {
  await page.goto('/#/');
  await waitForZeppelinReady(page);

  const testNotebook = await createTestNotebook(page);
  await navigateToNotebookWithFallback(page, testNotebook.noteId);
};

const cssDisplay = async (locator: Locator): Promise<string> =>
  locator.evaluate(element => window.getComputedStyle(element).display);

const cssOpacity = async (locator: Locator): Promise<number> =>
  locator.evaluate(element => Number(window.getComputedStyle(element).opacity));

test.describe('Simple look-and-feel input capability handling', () => {
  // JUSTIFIED: each test creates and mutates its own notebook; keeping defaults avoids shared mutable page objects.
  test.describe.configure({ mode: 'default' });

  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK);
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_ACTION_BAR);
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_SIDEBAR);

  test('keeps notebook controls accessible in a touch-only WebKit context', async ({ browser, browserName }) => {
    test.skip(browserName !== 'webkit', 'Touch-only coverage uses the WebKit iPhone 15 Pro context from the issue');

    const context = await browser.newContext({
      ...devices['iPhone 15 Pro'],
      storageState: 'playwright/.auth/user.json'
    });

    try {
      const page = await context.newPage();
      const actionBar = new NotebookActionBarPage(page);
      const sidebar = new NotebookSidebarPage(page);

      await openTestNotebook(page);
      await setLookAndFeel(page, 'simple');

      await expect.poll(() => page.evaluate(() => window.matchMedia('(any-hover: none)').matches)).toBe(true);
      await page.touchscreen.tap(390, 640);

      const actionControls = page.locator('zeppelin-notebook-action-bar .control');
      const noteSettings = page.locator('zeppelin-notebook-action-bar .setting');
      await expect.poll(() => cssDisplay(actionControls)).not.toBe('none');
      await expect.poll(() => cssDisplay(noteSettings)).not.toBe('none');
      await expect(actionBar.runAllButton).toBeVisible();
      await expect(actionBar.runAllButton).toBeEnabled();
      await expect(actionBar.shortcutInfoButton).toBeVisible();
      await expect(actionBar.lookAndFeelDropdown).toBeVisible();

      const sidebarNav = page.locator('zeppelin-notebook-sidebar .sidebar-nav');
      await expect.poll(() => cssOpacity(sidebarNav)).toBe(1);
      await expect(sidebar.tocButton).toBeVisible();
      await expect(sidebar.fileTreeButton).toBeVisible();
      await expect(sidebar.tocButton).toBeEnabled();
      await expect(sidebar.fileTreeButton).toBeEnabled();

      await sidebar.openToc();
      await expect(sidebar.noteToc).toBeVisible();

      await sidebar.openFileTree();
      await expect(sidebar.nodeList).toBeVisible();
    } finally {
      await context.close();
    }
  });

  test('retains hover-to-reveal behavior for desktop input', async ({ page }) => {
    await openTestNotebook(page);
    await setLookAndFeel(page, 'simple');

    // Some headless browsers (e.g. Firefox on Linux CI) report no hover-capable pointer, so the
    // hover-to-reveal behavior cannot be exercised there.
    const supportsHover = await page.evaluate(() => window.matchMedia('(any-hover: hover)').matches);
    test.skip(!supportsHover, 'Requires a browser that reports a hover-capable pointer');

    const actionBar = page.locator('zeppelin-notebook-action-bar .bar');
    const actionControls = page.locator('zeppelin-notebook-action-bar .control');
    const noteSettings = page.locator('zeppelin-notebook-action-bar .setting');
    const sidebar = page.locator('zeppelin-notebook-sidebar .sidebar');
    const sidebarNav = page.locator('zeppelin-notebook-sidebar .sidebar-nav');

    await page.mouse.move(360, 360);
    await expect.poll(() => cssDisplay(actionControls)).toBe('none');
    await expect.poll(() => cssDisplay(noteSettings)).toBe('none');
    await expect.poll(() => cssOpacity(sidebarNav)).toBe(0);

    await actionBar.hover();
    await expect.poll(() => cssDisplay(actionControls)).not.toBe('none');
    await expect.poll(() => cssDisplay(noteSettings)).not.toBe('none');

    await page.mouse.move(360, 360);
    await expect.poll(() => cssOpacity(sidebarNav)).toBe(0);

    await sidebar.hover();
    await expect.poll(() => cssOpacity(sidebarNav)).toBe(1);
  });
});
