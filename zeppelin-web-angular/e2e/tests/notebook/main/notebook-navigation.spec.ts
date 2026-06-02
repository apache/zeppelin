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

import { expect, Page, test } from '@playwright/test';
import { HeaderPage } from '../../../models/header-page';
import { HomePage } from '../../../models/home-page';
import { addPageAnnotationBeforeEach, PAGES, waitForZeppelinReady } from '../../../utils';

const noteIdFromUrl = (url: string): string => {
  const match = url.match(/\/notebook\/([^/?]+)/);
  if (!match) {
    throw new Error(`Could not extract noteId from URL: ${url}`);
  }
  return match[1];
};

const createRootNote = async (page: Page, homePage: HomePage, name: string): Promise<string> => {
  await page.goto('/#/');
  await waitForZeppelinReady(page);
  await homePage.createNote(name);
  await page.waitForURL(/\/notebook\//, { timeout: 45000 });
  return noteIdFromUrl(page.url());
};

test.describe('Notebook Navigation', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK);

  test.beforeEach(async ({ page }) => {
    await page.goto('/#/');
    await waitForZeppelinReady(page);
  });

  // Regression: ZEPPELIN-6387 moved the note fetch onto the WebSocket connectedStatus$
  // stream only. Because NotebookComponent is reused across :noteId param changes
  // (ngOnInit does not re-run) and the socket stays connected, navigating between notes
  // via the header list changed the URL but never re-fetched the note — the page kept
  // showing the previous note. The fetch must also fire on route param changes.
  test('Given the user is viewing a note, When they pick another note from the header list, Then the note content updates', async ({
    page
  }) => {
    const homePage = new HomePage(page);
    const headerPage = new HeaderPage(page);
    const stamp = Date.now();
    const nameA = `_e2e_nav_A_${stamp}`;
    const nameB = `_e2e_nav_B_${stamp}`;

    // Create two distinct root-level notes. createNote lands on each new note's page.
    const noteIdA = await createRootNote(page, homePage, nameA);
    const noteIdB = await createRootNote(page, homePage, nameB);

    // We are now on note B (freshly mounted) — the URL and title must both reflect note B.
    await expect(page).toHaveURL(new RegExp(`/notebook/${noteIdB}`));
    const title = page.locator('[data-testid="notebook-title"]');
    await expect(title).toContainText(nameB, { timeout: 15000 });

    // In-app navigation to note A via the header notebook list — this reuses the
    // already-mounted NotebookComponent, which is exactly what the regression broke.
    await headerPage.clickNotebookMenu();
    const noteALink = page.locator(`a[href*="/notebook/${noteIdA}"]`).first();
    await noteALink.waitFor({ state: 'visible', timeout: 10000 });
    await noteALink.click();

    // The URL changing alone never caught the bug — the content must change too.
    await expect(page).toHaveURL(new RegExp(`/notebook/${noteIdA}`));
    await expect(title).toContainText(nameA, { timeout: 15000 });
  });
});
