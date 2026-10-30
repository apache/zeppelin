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

/// <reference lib="es2024.promise" />

import { readFile } from 'node:fs/promises';
import { Download, expect, test } from '@playwright/test';
import { NotebookActionBarPage } from '../../../models/notebook-action-bar-page';
import { deleteNote } from '../../../models/notebook-core-read-page.util';
import {
  addPageAnnotationBeforeEach,
  waitForZeppelinReady,
  PAGES,
  createTestNotebookWithName,
  navigateToNotebookWithFallback,
  setParagraphText
} from '../../../utils';

const MARKDOWN = '# Export regression 한글 📝\nMarkdown keeps café and 日本語.';
const CODE = 'val message = "Hello 🌍"\nprintln(message)';
const PARAGRAPHS = [`%md\n${MARKDOWN}`, CODE];

interface ExportedNbformat {
  nbformat: number;
  nbformat_minor: number;
  metadata: { name: string };
  cells: Array<{ cell_type: string; source: string | string[]; outputs?: unknown[] }>;
}

interface ExportedZeppelinNote {
  id: string;
  name: string;
  paragraphs: Array<{ text: string }>;
}

const readDownload = async (download: Download): Promise<string> => {
  const file = await download.path();
  if (!file) {
    throw new Error('The notebook export did not produce a local file');
  }
  return readFile(file, 'utf8');
};

test.describe('Notebook Action Bar Functionality', () => {
  // JUSTIFIED: page objects and notebook ids are stored in describe scope; fullyParallel can overwrite them.
  test.describe.configure({ mode: 'default' });

  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_ACTION_BAR);

  let actionBarPage: NotebookActionBarPage;
  let testNotebook: { noteId: string; paragraphId: string; notebookName: string };

  test.beforeEach(async ({ page }) => {
    await page.goto('/#/');
    await waitForZeppelinReady(page);

    testNotebook = await createTestNotebookWithName(page, { namePrefix: 'TestNotebook_한글' });
    actionBarPage = new NotebookActionBarPage(page);

    await navigateToNotebookWithFallback(page, testNotebook.noteId);
  });

  test.afterEach(async ({ page }) => {
    await deleteNote(page.request, testNotebook?.noteId);
  });

  test('should display and allow title editing with tooltip', { tag: '@NB-PARITY-002' }, async ({ page }) => {
    const notebookName = `TestNotebook_${Date.now()}`;

    await expect(actionBarPage.titleEditor).toBeVisible();
    await actionBarPage.titleEditor.click();

    const titleInputField = actionBarPage.titleEditor.locator('input');
    await actionBarPage.fillAndVerifyInput(titleInputField, notebookName);
    await page.keyboard.press('Enter');

    await expect(actionBarPage.titleEditor).toHaveText(notebookName, { timeout: 10000 });
  });

  test('should execute run all paragraphs workflow', async ({ page }) => {
    await expect(actionBarPage.runAllButton).toBeVisible();
    await expect(actionBarPage.runAllButton).toBeEnabled();

    await actionBarPage.clickRunAll();

    // Confirmation dialog must appear when running all paragraphs
    const confirmButton = page
      .locator('nz-popconfirm button:has-text("OK"), .ant-popconfirm button:has-text("OK"), button:has-text("OK")')
      // JUSTIFIED: compound locator targets unique OK button in popconfirm
      .first();
    await expect(confirmButton).toBeVisible({ timeout: 5000 });
    await confirmButton.click();
    await expect(confirmButton).not.toBeVisible();
  });

  test('should display cancel all button as disabled when note is idle', async () => {
    await expect(actionBarPage.cancelAllButton).toBeVisible();

    // Given: an idle note (no paragraph running), Cancel all is disabled and Run all is enabled — the two buttons are mutually exclusive
    await expect(actionBarPage.cancelAllButton).toBeDisabled();
    await expect(actionBarPage.runAllButton).toBeEnabled();
  });

  test('should toggle code visibility', async () => {
    await expect(actionBarPage.showHideCodeButton).toBeVisible();
    await expect(actionBarPage.showHideCodeButton).toBeEnabled();

    const initialCodeVisibility = await actionBarPage.isCodeVisible();
    await actionBarPage.toggleCodeVisibility();

    const expectedIcon = initialCodeVisibility ? 'fullscreen' : 'fullscreen-exit';
    const icon = actionBarPage.showHideCodeButton.locator('i[nz-icon] svg');
    await expect(icon).toHaveAttribute('data-icon', expectedIcon, { timeout: 5000 });

    expect(await actionBarPage.isCodeVisible()).toBe(!initialCodeVisibility);
    await expect(actionBarPage.showHideCodeButton).toBeEnabled();
  });

  test('should toggle output visibility', async () => {
    await expect(actionBarPage.showHideOutputButton).toBeVisible();
    await expect(actionBarPage.showHideOutputButton).toBeEnabled();

    const initialOutputVisibility = await actionBarPage.isOutputVisible();
    await actionBarPage.toggleOutputVisibility();

    const expectedIcon = initialOutputVisibility ? 'book' : 'read';
    const icon = actionBarPage.showHideOutputButton.locator('i[nz-icon] svg');
    await expect(icon).toHaveAttribute('data-icon', expectedIcon, { timeout: 5000 });

    expect(await actionBarPage.isOutputVisible()).toBe(!initialOutputVisibility);
    await expect(actionBarPage.showHideOutputButton).toBeEnabled();
  });

  test('should execute clear output workflow', async ({ page }) => {
    await expect(actionBarPage.clearOutputButton).toBeVisible();
    await expect(actionBarPage.clearOutputButton).toBeEnabled();

    await actionBarPage.clickClearOutput();

    const confirmSelector = page
      .locator('nz-popconfirm button:has-text("OK"), .ant-popconfirm button:has-text("OK"), button:has-text("OK")')
      // JUSTIFIED: compound locator targets unique OK button in popconfirm
      .first();
    // JUSTIFIED: confirmation dialog is optional — some configurations don't require it
    const isVisible = await confirmSelector.isVisible({ timeout: 2000 }).catch(() => false);
    // JUSTIFIED: dialog is optional — absent when notebook has no output
    if (isVisible) {
      await confirmSelector.click();
      await expect(confirmSelector).not.toBeVisible();
    }

    await expect(actionBarPage.clearOutputButton).toBeEnabled();

    const paragraphResults = page.locator('zeppelin-notebook-paragraph-result');
    const resultCount = await paragraphResults.count();
    for (let i = 0; i < resultCount; i++) {
      // JUSTIFIED: iterating all paragraph results by index
      const result = paragraphResults.nth(i);
      // JUSTIFIED: only visible results need empty check
      if (await result.isVisible()) {
        await expect(result).toBeEmpty();
      }
    }
  });

  test('should display note management buttons', async () => {
    await expect(actionBarPage.cloneButton).toBeVisible();
    await expect(actionBarPage.cloneButton).toBeEnabled();
    await expect(actionBarPage.exportButton).toBeVisible();
    await expect(actionBarPage.exportButton).toBeEnabled();
    await expect(actionBarPage.reloadButton).toBeVisible();
    await expect(actionBarPage.reloadButton).toBeEnabled();
  });

  test('downloads converted ipynb once per click after SPA reopen and preserves zpln export', async ({ page }) => {
    const notebook = testNotebook;
    const downloads: Download[] = [];
    const recordDownload = (download: Download): void => {
      downloads.push(download);
    };
    page.on('download', recordDownload);

    try {
      await test.step('Given a saved note with deterministic Unicode markdown and code', async () => {
        await setParagraphText(page, notebook.noteId, notebook.paragraphId, PARAGRAPHS[0]);
        const response = await page.request.post(`/api/notebook/${notebook.noteId}/paragraph`, {
          data: { text: PARAGRAPHS[1] }
        });
        expect(response.ok(), `Code paragraph creation failed (${response.status()}): ${await response.text()}`).toBe(
          true
        );
        await navigateToNotebookWithFallback(page, notebook.noteId);
        await expect(actionBarPage.titleEditor).toHaveText(notebook.notebookName);
      });

      await test.step('Then both export formats are available with distinct accessible names', async () => {
        await expect(actionBarPage.exportIpynbButton).toBeVisible();
        await expect(actionBarPage.exportIpynbButton).toBeEnabled();
        await expect(actionBarPage.exportButton).toBeVisible();
        await expect(actionBarPage.exportButton).toBeEnabled();
      });

      const exportAndVerifyIpynb = async (): Promise<void> => {
        const downloadEvent = page.waitForEvent('download');
        await actionBarPage.exportIpynbButton.click();
        const download = await downloadEvent;
        expect(download.suggestedFilename()).toBe(`${notebook.notebookName}.ipynb`);
        const content = await readDownload(download);
        expect(content.charCodeAt(0)).not.toBe(0xfeff);
        const converted = JSON.parse(content) as ExportedNbformat;
        expect(converted.nbformat).toBe(4);
        expect(converted.metadata.name).toBe(notebook.notebookName);
        expect(
          converted.cells.map(cell => ({
            type: cell.cell_type,
            source: Array.isArray(cell.source) ? cell.source.join('') : cell.source
          }))
        ).toEqual([
          { type: 'markdown', source: MARKDOWN },
          { type: 'code', source: CODE }
        ]);
        expect(converted.cells[1].outputs).toEqual([]);
      };

      await test.step(
        'When ipynb is exported, then it contains real converted Unicode cells without a BOM',
        exportAndVerifyIpynb
      );

      await test.step('When the user leaves the notebook through SPA navigation and returns', async () => {
        await page.evaluate(() => {
          window.location.hash = '/';
        });
        await expect(actionBarPage.exportIpynbButton).toHaveCount(0);
        await waitForZeppelinReady(page);
        await page.evaluate(id => {
          window.location.hash = `/notebook/${id}`;
        }, notebook.noteId);
        await waitForZeppelinReady(page);
        await expect(actionBarPage.titleEditor).toHaveText(notebook.notebookName);
        await expect(actionBarPage.exportIpynbButton).toBeVisible();
      });

      await test.step(
        'When ipynb is exported again, then the reopened note downloads the same converted cells',
        exportAndVerifyIpynb
      );

      await test.step('Then zpln still downloads the original Zeppelin note and no duplicate ipynb download occurred', async () => {
        const downloadEvent = page.waitForEvent('download');
        await actionBarPage.exportButton.click();
        const download = await downloadEvent;
        expect(download.suggestedFilename()).toBe(`${notebook.notebookName}.zpln`);
        const content = await readDownload(download);
        expect(content.charCodeAt(0)).not.toBe(0xfeff);
        const exported = JSON.parse(content) as ExportedZeppelinNote;
        expect(exported.id).toBe(notebook.noteId);
        expect(exported.name).toBe(notebook.notebookName);
        expect(exported.paragraphs.map(paragraph => paragraph.text)).toEqual(PARAGRAPHS);
        // Completed files and the next render task bound synchronous response listeners without a sleep.
        await page.evaluate(() => {
          const { promise, resolve } = Promise.withResolvers<void>();
          requestAnimationFrame(() => resolve());
          return promise;
        });
        expect(downloads.map(item => item.suggestedFilename())).toEqual([
          `${notebook.notebookName}.ipynb`,
          `${notebook.notebookName}.ipynb`,
          `${notebook.notebookName}.zpln`
        ]);
      });
    } finally {
      page.off('download', recordDownload);
    }
  });

  // Toggle coverage lives in collaboration/collaborative-mode.spec.ts.

  test('should handle revision controls when supported', async () => {
    test.skip(!(await actionBarPage.commitButton.isVisible()), 'Revision controls not supported in this environment');

    await expect(actionBarPage.commitButton).toBeVisible();
    await expect(actionBarPage.commitButton).toBeEnabled();

    // JUSTIFIED: optional when revision system disabled
    if (await actionBarPage.setRevisionButton.isVisible()) {
      await expect(actionBarPage.setRevisionButton).toBeEnabled();
    }
    // JUSTIFIED: optional when revision system disabled
    if (await actionBarPage.compareRevisionsButton.isVisible()) {
      await expect(actionBarPage.compareRevisionsButton).toBeEnabled();
    }
    // JUSTIFIED: optional when revision system disabled
    if (await actionBarPage.revisionDropdown.isVisible()) {
      await actionBarPage.openRevisionDropdown();
      await expect(actionBarPage.revisionDropdownMenu).toBeVisible();
    }
  });

  test('should handle scheduler controls when enabled', async () => {
    test.skip(!(await actionBarPage.schedulerButton.isVisible()), 'Scheduler not enabled in this environment');

    await expect(actionBarPage.schedulerButton).toBeVisible();
    await actionBarPage.openSchedulerDropdown();
    await expect(actionBarPage.schedulerDropdown).toBeVisible();

    // JUSTIFIED: optional when scheduler disabled
    if (await actionBarPage.cronInput.isVisible()) {
      await expect(actionBarPage.cronInput).toBeEditable();
    }
    // JUSTIFIED: optional when scheduler disabled
    if (await actionBarPage.cronPresets.first().isVisible()) {
      await expect(actionBarPage.cronPresets).not.toHaveCount(0);
    }
  });

  test('should display settings group properly', async ({ page }) => {
    const actionBar = page.locator('zeppelin-notebook-action-bar');
    await expect(actionBar).toBeVisible({ timeout: 15000 });

    // Required control: shortcut info button must always be present
    await expect(actionBarPage.shortcutInfoButton).toBeVisible({ timeout: 5000 });
    await expect(actionBarPage.shortcutInfoButton).toBeEnabled();

    // Optional controls: visible+enabled when present, absent when permissions/config hide them
    for (const control of [
      actionBarPage.interpreterSettingsButton,
      actionBarPage.permissionsButton,
      actionBarPage.lookAndFeelDropdown
    ]) {
      // JUSTIFIED: optional per user permissions
      if (await control.isVisible()) {
        await expect(control).toBeEnabled();
      }
    }
  });
});
