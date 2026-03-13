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
import { HomePage } from '../../models/home-page';
import { addPageAnnotationBeforeEach, performLoginIfRequired, waitForZeppelinReady, PAGES } from '../../utils';

addPageAnnotationBeforeEach(PAGES.WORKSPACE.HOME);

test.describe('Home Page Note Operations', () => {
  let homePage: HomePage;
  let testNoteName: string;

  test.beforeEach(async ({ page }) => {
    homePage = new HomePage(page);
    testNoteName = `_e2e_ops_test_${Date.now()}`;

    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);

    // Create a test note so all operation tests have a real target
    await homePage.createNote(testNoteName);
    await page.goto('/#/');
    await waitForZeppelinReady(page);

    await expect(page.locator('zeppelin-node-list')).toBeVisible({ timeout: 15000 });

    // Force a note list refresh so the newly created note is guaranteed to appear
    await homePage.clickRefreshNotes();
    await expect(page.locator('.node .file').filter({ hasText: testNoteName })).toBeVisible({ timeout: 15000 });
  });

  test.describe('Given note operations are available', () => {
    test('When hovering over note Then should show rename, clear, and delete action buttons', async ({ page }) => {
      const testNote = page.locator('.node .file').filter({ hasText: testNoteName });
      await testNote.hover();

      // Scoped to testNote: CSS .node:hover reveals .operation icons only for the hovered node
      await expect(testNote.locator('.operation a[nztooltiptitle="Rename note"]')).toBeVisible();
      await expect(testNote.locator('.operation a[nztooltiptitle="Clear output"]')).toBeVisible();
      await expect(testNote.locator('.operation a[nztooltiptitle="Move note to Trash"]')).toBeVisible();
    });

    test('When hovering over note actions Then should show tooltip descriptions', async ({ page }) => {
      const testNote = page.locator('.node .file').filter({ hasText: testNoteName });
      await testNote.hover();
      // Wait for the buttons to become visible — CSS .node:hover makes <i> display:inline-block
      const renameBtn = testNote.locator('.operation a[nztooltiptitle="Rename note"]');
      const clearBtn = testNote.locator('.operation a[nztooltiptitle="Clear output"]');
      const trashBtn = testNote.locator('.operation a[nztooltiptitle="Move note to Trash"]');
      await renameBtn.waitFor({ state: 'visible' });

      // dispatchEvent mouseenter — justified: nz-tooltip listens to mouseenter; direct hover() on a child
      // causes a CSS :hover race where the mouse leaves the parent .node .file mid-movement, hiding the button.
      await renameBtn.dispatchEvent('mouseenter');
      await expect(page.locator('.ant-tooltip', { hasText: 'Rename note' })).toBeVisible();
      await renameBtn.dispatchEvent('mouseleave');

      await clearBtn.dispatchEvent('mouseenter');
      await expect(page.locator('.ant-tooltip', { hasText: 'Clear output' })).toBeVisible();
      await clearBtn.dispatchEvent('mouseleave');

      await trashBtn.dispatchEvent('mouseenter');
      await expect(page.locator('.ant-tooltip', { hasText: 'Move note to Trash' })).toBeVisible();
      await trashBtn.dispatchEvent('mouseleave');
    });
  });

  test.describe('Given rename note functionality', () => {
    test('When rename button is clicked Then should open rename dialog', async ({ page }) => {
      const testNote = page.locator('.node .file').filter({ hasText: testNoteName });
      await testNote.hover();

      const renameButton = testNote.locator('.operation a[nztooltiptitle="Rename note"]');
      await expect(renameButton).toBeVisible();
      await renameButton.click();

      await expect(page.locator('zeppelin-note-rename, [role="dialog"].ant-modal').first()).toBeVisible({
        timeout: 5000
      });
    });
  });

  test.describe('Given clear output functionality', () => {
    test('When clear output button is clicked Then should show and dismiss confirmation dialog', async ({ page }) => {
      const testNote = page.locator('.node .file').filter({ hasText: testNoteName });
      await testNote.hover();

      const clearButton = testNote.locator('.operation a[nztooltiptitle="Clear output"]');
      await expect(clearButton).toBeVisible();
      await clearButton.click();

      await expect(page.locator('text=Do you want to clear all output?')).toBeVisible();
      await page.locator('.ant-popover button:has-text("OK")').click();

      // Popover should close after confirming the operation
      await expect(page.locator('text=Do you want to clear all output?')).not.toBeVisible();
    });
  });

  test.describe('Given move to trash functionality', () => {
    test('When move to trash is confirmed Then should move note to trash folder', async ({ page }) => {
      const testNote = page.locator('.node .file').filter({ hasText: testNoteName });
      await testNote.hover();

      const deleteButton = testNote.locator('.operation a[nztooltiptitle="Move note to Trash"]');
      await expect(deleteButton).toBeVisible();
      await deleteButton.click();

      await expect(page.locator('text=This note will be moved to trash.')).toBeVisible();
      await page.locator('.ant-popover button:has-text("OK")').click();

      // Source note must disappear from the list — not just that Trash folder appears
      await expect(testNote).not.toBeVisible({ timeout: 10000 });
      await expect(page.locator('.node .folder').filter({ hasText: 'Trash' })).toBeVisible({ timeout: 10000 });
    });
  });

  test.describe('Given note filter with special characters', () => {
    test('When filtering with special characters Then should not crash and should show empty results', async ({
      page
    }) => {
      for (const char of ['#', '%', '"']) {
        await homePage.filterNotes(char);

        // App must not crash — node list container remains present
        await expect(page.locator('zeppelin-node-list')).toBeVisible();
        // Test note name contains none of these chars, so it must not appear
        await expect(page.locator('.node .file').filter({ hasText: testNoteName })).not.toBeVisible();
      }

      // Clearing filter restores the note
      await homePage.filterNotes('');
      await expect(page.locator('.node .file').filter({ hasText: testNoteName })).toBeVisible({ timeout: 5000 });
    });
  });

  test.describe('Given max length note name input', () => {
    test('When note name input is filled with a very long string Then input should cap or accept gracefully', async ({
      page
    }) => {
      await homePage.clickCreateNewNote();
      await page.waitForSelector('nz-form-label', { timeout: 10000 });

      const notebookNameInput = page.locator('div.ant-modal-content input[name="noteName"]');
      const maxLengthAttr = await notebookNameInput.getAttribute('maxlength');
      const longName = `_e2e_ml_${'a'.repeat(300)}`;

      await notebookNameInput.fill(longName);
      const actualValue = await notebookNameInput.inputValue();

      // Must have content — input did not silently reject the fill
      expect(actualValue.length).toBeGreaterThan(0);

      if (maxLengthAttr !== null) {
        // If the element enforces maxlength, the value must be capped at that limit
        expect(actualValue.length).toBeLessThanOrEqual(parseInt(maxLengthAttr, 10));
      } else {
        // No client-side cap — the full value passes through
        expect(actualValue).toContain('_e2e_ml_');
      }

      // Dismiss the modal without creating
      await page.keyboard.press('Escape');
      await page.locator('div.ant-modal-content').waitFor({ state: 'detached', timeout: 5000 });
    });
  });

  test.describe('Given trash folder operations', () => {
    test.beforeEach(async ({ page }) => {
      // Move the test note to trash to put the trash folder into a known state
      const testNote = page.locator('.node .file').filter({ hasText: testNoteName });
      await testNote.hover();
      await testNote.locator('.operation').waitFor({ state: 'visible' });

      const deleteButton = testNote.locator('.operation a[nztooltiptitle="Move note to Trash"]');
      await deleteButton.click();

      await expect(page.locator('text=This note will be moved to trash.')).toBeVisible();
      await page.locator('.ant-popover button:has-text("OK")').click();
      await expect(page.locator('.node .folder').filter({ hasText: 'Trash' })).toBeVisible({ timeout: 10000 });
    });

    test('When trash folder exists Then should show restore and empty options', async ({ page }) => {
      const trashFolder = page.locator('.node .folder').filter({ hasText: 'Trash' });
      await trashFolder.hover();
      await trashFolder.locator('.operation').waitFor({ state: 'visible' });

      // Scoped to trashFolder: same CSS hover rule applies to .node:hover for folder nodes
      await expect(trashFolder.locator('.operation a[nztooltiptitle*="Restore all"]')).toBeVisible();
      await expect(trashFolder.locator('.operation a[nztooltiptitle*="Empty all"]')).toBeVisible();
    });

    test('When restore all is clicked Then should show confirmation dialog', async ({ page }) => {
      const trashFolder = page.locator('.node .folder').filter({ hasText: 'Trash' });
      await trashFolder.hover();
      await trashFolder.locator('.operation').waitFor({ state: 'visible' });

      const restoreButton = trashFolder.locator('.operation a[nztooltiptitle*="Restore all"]');
      await expect(restoreButton).toBeVisible();
      // Use force:true — hovering restoreButton directly can briefly exit .folder:hover and hide it
      await restoreButton.click({ force: true });

      await expect(
        page.locator('text=Folders and notes in the trash will be merged into their original position.')
      ).toBeVisible();
    });

    test('When empty trash is clicked Then should show permanent deletion warning', async ({ page }) => {
      const trashFolder = page.locator('.node .folder').filter({ hasText: 'Trash' });
      await trashFolder.hover();
      await trashFolder.locator('.operation').waitFor({ state: 'visible' });

      const emptyButton = trashFolder.locator('.operation a[nztooltiptitle*="Empty all"]');
      await expect(emptyButton).toBeVisible();
      await emptyButton.hover();
      await emptyButton.click();

      await expect(page.locator('text=This cannot be undone. Are you sure?')).toBeVisible();
    });
  });
});
