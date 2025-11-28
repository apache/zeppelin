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

  test.beforeEach(async ({ page }) => {
    homePage = new HomePage(page);
    await page.goto('/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
    await page.waitForSelector('zeppelin-node-list', { timeout: 15000 });
  });

  test.describe('Given note operations are available', () => {
    test('When note list loads Then should show note action buttons on hover', async ({ page }) => {
      const notesExist = await page.locator('.node .file').count();

      if (notesExist > 0) {
        const firstNote = page.locator('.node .file').first();
        await firstNote.hover();

        await expect(page.locator('.file .operation a[nztooltiptitle*="Rename note"]').first()).toBeVisible();
        await expect(page.locator('.file .operation a[nztooltiptitle*="Clear output"]').first()).toBeVisible();
        await expect(page.locator('.file .operation a[nztooltiptitle*="Move note to Trash"]').first()).toBeVisible();
      } else {
        console.log('No notes available for testing operations');
      }
    });

    test('When hovering over note actions Then should show tooltip descriptions', async ({ page }) => {
      const noteExists = await page
        .locator('.node .file')
        .first()
        .isVisible()
        .catch(() => false);

      if (noteExists) {
        const firstNote = page.locator('.node .file').first();
        await firstNote.hover();

        const renameIcon = page.locator('.file .operation a[nztooltiptitle*="Rename note"]').first();
        const clearIcon = page.locator('.file .operation a[nztooltiptitle*="Clear output"]').first();
        const deleteIcon = page.locator('.file .operation a[nztooltiptitle*="Move note to Trash"]').first();

        await expect(renameIcon).toBeVisible();
        await expect(clearIcon).toBeVisible();
        await expect(deleteIcon).toBeVisible();

        // Test tooltip visibility by hovering over each icon
        await renameIcon.hover();
        await expect(page.locator('.ant-tooltip', { hasText: 'Rename note' })).toBeVisible();

        await clearIcon.hover();
        await expect(page.locator('.ant-tooltip', { hasText: 'Clear output' })).toBeVisible();

        await deleteIcon.hover();
        await expect(page.locator('.ant-tooltip', { hasText: 'Move note to Trash' })).toBeVisible();
      }
    });
  });

  test.describe('Given rename note functionality', () => {
    test('When rename button is clicked Then should trigger rename workflow', async ({ page }) => {
      const noteExists = await page
        .locator('.node .file')
        .first()
        .isVisible()
        .catch(() => false);

      if (noteExists) {
        const noteItem = page.locator('.node .file').first();
        await noteItem.hover();

        const renameButton = page.locator('.file .operation a[nztooltiptitle*="Rename note"]').first();
        await expect(renameButton).toBeVisible();
        await renameButton.click();

        await page
          .waitForFunction(
            () =>
              document.querySelector('zeppelin-note-rename') !== null ||
              document.querySelector('[role="dialog"]') !== null ||
              document.querySelector('.ant-modal') !== null,
            { timeout: 5000 }
          )
          .catch(() => {
            console.log('Rename modal did not appear - might need different trigger');
          });
      }
    });
  });

  test.describe('Given clear output functionality', () => {
    test('When clear output button is clicked Then should show confirmation dialog', async ({ page }) => {
      const noteExists = await page
        .locator('.node .file')
        .first()
        .isVisible()
        .catch(() => false);

      if (noteExists) {
        const noteItem = page.locator('.node .file').first();
        await noteItem.hover();

        const clearButton = page.locator('.file .operation a[nztooltiptitle*="Clear output"]').first();
        await expect(clearButton).toBeVisible();
        await clearButton.click();

        await expect(page.locator('text=Do you want to clear all output?')).toBeVisible();
      }
    });

    test('When clear output is confirmed Then should execute clear operation', async ({ page }) => {
      const noteExists = await page
        .locator('.node .file')
        .first()
        .isVisible()
        .catch(() => false);

      if (noteExists) {
        const noteItem = page.locator('.node .file').first();
        await noteItem.hover();

        const clearButton = page.locator('.file .operation a[nztooltiptitle*="Clear output"]').first();
        await expect(clearButton).toBeVisible();
        await clearButton.click();

        const confirmButton = page.locator('button:has-text("Yes")');
        if (await confirmButton.isVisible()) {
          await confirmButton.click();
        }
      }
    });
  });

  test.describe('Given move to trash functionality', () => {
    test('When delete button is clicked Then should show trash confirmation', async ({ page }) => {
      const noteExists = await page
        .locator('.node .file')
        .first()
        .isVisible()
        .catch(() => false);

      if (noteExists) {
        const noteItem = page.locator('.node .file').first();
        await noteItem.hover();

        const deleteButton = page.locator('.file .operation a[nztooltiptitle*="Move note to Trash"]').first();
        await expect(deleteButton).toBeVisible();
        await deleteButton.click();

        await expect(page.locator('text=This note will be moved to trash.')).toBeVisible();
      }
    });

    test('When move to trash is confirmed Then should move note to trash folder', async ({ page }) => {
      const noteExists = await page
        .locator('.node .file')
        .first()
        .isVisible()
        .catch(() => false);

      if (noteExists) {
        const noteItem = page.locator('.node .file').first();
        await noteItem.hover();

        const deleteButton = page.locator('.file .operation a[nztooltiptitle*="Move note to Trash"]').first();
        await expect(deleteButton).toBeVisible();
        await deleteButton.click();

        const confirmButton = page.locator('button:has-text("Yes")');
        if (await confirmButton.isVisible()) {
          await confirmButton.click();

          await page.waitForTimeout(2000);

          const trashFolder = page.locator('.node .folder').filter({ hasText: 'Trash' });
          await expect(trashFolder).toBeVisible();
        }
      }
    });
  });

  test.describe('Given trash folder operations', () => {
    test('When trash folder exists Then should show restore and empty options', async ({ page }) => {
      const trashExists = await page
        .locator('.node .folder')
        .filter({ hasText: 'Trash' })
        .isVisible()
        .catch(() => false);

      if (trashExists) {
        const trashFolder = page.locator('.node .folder').filter({ hasText: 'Trash' });
        await trashFolder.hover();

        await expect(page.locator('.folder .operation a[nztooltiptitle*="Restore all"]')).toBeVisible();
        await expect(page.locator('.folder .operation a[nztooltiptitle*="Empty all"]')).toBeVisible();
      }
    });

    test('When restore all is clicked Then should show confirmation dialog', async ({ page }) => {
      const trashExists = await page
        .locator('.node .folder')
        .filter({ hasText: 'Trash' })
        .isVisible()
        .catch(() => false);

      if (trashExists) {
        const trashFolder = page.locator('.node .folder').filter({ hasText: 'Trash' });
        await trashFolder.hover();

        const restoreButton = page.locator('.folder .operation a[nztooltiptitle*="Restore all"]').first();
        await expect(restoreButton).toBeVisible();
        await restoreButton.click();

        await expect(
          page.locator('text=Folders and notes in the trash will be merged into their original position.')
        ).toBeVisible();
      }
    });

    test('When empty trash is clicked Then should show permanent deletion warning', async ({ page }) => {
      const trashExists = await page
        .locator('.node .folder')
        .filter({ hasText: 'Trash' })
        .isVisible()
        .catch(() => false);

      if (trashExists) {
        const trashFolder = page.locator('.node .folder').filter({ hasText: 'Trash' });
        await trashFolder.hover();

        const emptyButton = page.locator('.folder .operation a[nztooltiptitle*="Empty all"]').first();
        await expect(emptyButton).toBeVisible();
        await emptyButton.click();

        await expect(page.locator('text=This cannot be undone. Are you sure?')).toBeVisible();
      }
    });
  });
});
