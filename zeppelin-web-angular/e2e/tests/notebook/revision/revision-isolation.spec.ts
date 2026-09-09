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

import { expect, Locator, Page, test } from '@playwright/test';

import {
  addPageAnnotationBeforeEach,
  createTestNotebookWithName,
  PAGES,
  performLoginIfRequired,
  skipWhenAuthenticationIsStillRequired,
  waitForNotebookLinks,
  waitForZeppelinReady
} from '../../../utils';

const prepareWorkspace = async (page: Page): Promise<void> => {
  await page.goto('/#/');
  await waitForZeppelinReady(page);
  await performLoginIfRequired(page);
  await skipWhenAuthenticationIsStillRequired(page);
  await waitForNotebookLinks(page);
};

const openNotebook = async (page: Page, noteId: string): Promise<void> => {
  await page.goto(`/#/notebook/${noteId}`);
  await waitForZeppelinReady(page);
};

/** Saves a revision. The commit control is an icon-only button that opens a popover. */
const commitRevision = async (page: Page, message: string): Promise<void> => {
  await page.locator('button:has(i[nzType="to-top"])').click();
  const commitInput = page.getByPlaceholder('commit message');
  await expect(commitInput).toBeVisible({ timeout: 15000 });
  await commitInput.fill(message);
  await page.getByRole('button', { name: 'commit', exact: true }).click();
  await expect(commitInput).toBeHidden({ timeout: 15000 });
};

/** The look and feel dropdown is labelled with the note's current value. */
const lookAndFeelButton = (page: Page): Locator => page.locator('button[nz-dropdown]:has(i[nzType="down"])').last();

const setLookAndFeel = async (page: Page, value: string): Promise<void> => {
  await lookAndFeelButton(page).click();
  await page.locator('li[nz-menu-item]').filter({ hasText: value }).last().click();
};

test.describe('Revision isolation', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK);

  // Both viewers share one principal (same storageState), matching the collaborative-mode spec.
  // Look and feel is what drives NOTE_UPDATE, and so the NOTE_UPDATED broadcast this covers;
  // renaming goes through a different op that resends the whole note.
  test('keeps a live NOTE_UPDATED from mutating an open revision snapshot', async ({ page, browser }) => {
    await prepareWorkspace(page);

    const { noteId } = await createTestNotebookWithName(page, { namePrefix: 'RevisionIsolation' });
    await openNotebook(page, noteId);

    // The snapshot has to capture the original look and feel, before the live change below.
    await expect(lookAndFeelButton(page)).toContainText('default', { timeout: 15000 });
    await commitRevision(page, 'snapshot before look and feel change');

    // The dropdown is labelled with the current revision, which is "Head" until one is chosen.
    await page.getByRole('button', { name: 'Head', exact: true }).click();
    const revisionItem = page.locator('li[nz-menu-item]').filter({ hasText: 'snapshot before look' });
    await expect(revisionItem).toBeVisible({ timeout: 15000 });
    await revisionItem.click();

    // Same socket, now showing the historical snapshot.
    await expect(page).toHaveURL(/\/revision\//, { timeout: 15000 });
    await expect(lookAndFeelButton(page)).toContainText('default', { timeout: 15000 });

    // The assertion below has to run after the revision view has actually received the event,
    // otherwise it would pass simply by checking too early. Message.receive logs every op.
    let sawNoteUpdated = false;
    page.on('console', message => {
      if (message.text().includes('Receive: NOTE_UPDATED')) {
        sawNoteUpdated = true;
      }
    });

    const liveContext = await browser.newContext({ storageState: await page.context().storageState() });
    const livePage = await liveContext.newPage();

    try {
      await livePage.goto('/#/');
      await waitForZeppelinReady(livePage);
      await performLoginIfRequired(livePage);
      await skipWhenAuthenticationIsStillRequired(livePage);
      await openNotebook(livePage, noteId);
      await expect(lookAndFeelButton(livePage)).toContainText('default', { timeout: 15000 });

      // Change the live note from an independent browser context; this broadcasts NOTE_UPDATED.
      await setLookAndFeel(livePage, 'simple');

      // The live follower applies it, which is how we know the broadcast actually went out and
      // that the revision view has had the same chance to receive it.
      await expect(lookAndFeelButton(livePage)).toContainText('simple', { timeout: 15000 });
      await expect.poll(() => sawNoteUpdated, { timeout: 15000 }).toBe(true);

      // The revision view got the event and must still show the snapshot as it was saved.
      await expect(lookAndFeelButton(page)).toContainText('default');
    } finally {
      await liveContext.close();
    }
  });
});
