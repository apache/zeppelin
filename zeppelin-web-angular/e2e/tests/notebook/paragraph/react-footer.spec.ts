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
import {
  addPageAnnotationBeforeEach,
  createTestNotebook,
  PAGES,
  performLoginIfRequired,
  waitForNotebookLinks,
  waitForZeppelinReady
} from '../../../utils';

test.describe('React Paragraph Footer', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK);

  let testNotebook: { noteId: string; paragraphId: string };

  test.beforeEach(async ({ page }) => {
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
    await waitForNotebookLinks(page);
    testNotebook = await createTestNotebook(page);
  });

  test('without reactFooter flag, Angular footer renders', async ({ page }) => {
    const { noteId } = testNotebook;

    await page.goto(`/#/notebook/${noteId}`);
    await waitForZeppelinReady(page);

    await expect(page.locator('[data-testid="angular-paragraph-footer"]').first()).toBeAttached({ timeout: 15000 });
    await expect(page.locator('[data-testid="react-paragraph-footer"]')).toHaveCount(0);
  });

  test('with reactFooter=true, React footer renders', async ({ page }) => {
    const { noteId } = testNotebook;

    await page.goto(`/#/notebook/${noteId}?reactFooter=true`);
    await waitForZeppelinReady(page);

    await expect(page.locator('[data-testid="react-paragraph-footer"]').first()).toBeAttached({ timeout: 15000 });
    await expect(page.locator('[data-testid="angular-paragraph-footer"]')).toHaveCount(0);
  });

  test('reactFooter=true preserves the paragraph query param', async ({ page }) => {
    const { noteId, paragraphId } = testNotebook;

    await page.goto(`/#/notebook/${noteId}?paragraph=${paragraphId}&reactFooter=true`);
    await waitForZeppelinReady(page);

    await expect(page).toHaveURL(/reactFooter=true/);
    await expect(page).toHaveURL(new RegExp(`paragraph=${paragraphId}`));
    await expect(page.locator('[data-testid="react-paragraph-footer"]').first()).toBeAttached({ timeout: 15000 });
  });

  test('when the remote fails to load, paragraphs fall back to the Angular footer', async ({ page }) => {
    const { noteId } = testNotebook;

    // Simulate a dead remote: every remoteEntry.js request fails
    await page.route('**/remoteEntry.js', route => route.abort());

    await page.goto(`/#/notebook/${noteId}?reactFooter=true`);
    await waitForZeppelinReady(page);

    // The loader rejection reaches each paragraph's onError, which flips
    // reactFooterFailed and re-renders the Angular footer
    await expect(page.locator('[data-testid="angular-paragraph-footer"]').first()).toBeAttached({ timeout: 15000 });
    await expect(page.locator('[data-testid="react-paragraph-footer"]')).toHaveCount(0);
  });

  test('navigating away during remoteEntry load does not throw', async ({ page }) => {
    const { noteId } = testNotebook;

    // Delay remoteEntry.js to widen the destroy-while-loading window
    await page.route('**/remoteEntry.js', async route => {
      await new Promise(r => setTimeout(r, 1500));
      await route.continue();
    });

    const consoleErrors: string[] = [];
    page.on('pageerror', err => consoleErrors.push(err.message));

    await page.goto(`/#/notebook/${noteId}?reactFooter=true`);
    // Navigate away before the remote can possibly mount
    await page.waitForTimeout(100);
    await page.goto('/#/');
    await waitForZeppelinReady(page);

    await page.waitForTimeout(2500);

    expect(consoleErrors).toEqual([]);
  });
});
