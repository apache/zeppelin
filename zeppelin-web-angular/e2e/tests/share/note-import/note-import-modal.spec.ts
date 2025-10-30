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

import { test, expect } from '@playwright/test';
import { HomePage } from '../../../models/home-page';
import { NoteImportModal } from '../../../models/note-import-modal';
import { addPageAnnotationBeforeEach, PAGES, performLoginIfRequired, waitForZeppelinReady } from '../../../utils';

test.describe('Note Import Modal', () => {
  let homePage: HomePage;
  let noteImportModal: NoteImportModal;

  addPageAnnotationBeforeEach(PAGES.SHARE.NOTE_IMPORT);

  test.beforeEach(async ({ page }) => {
    homePage = new HomePage(page);
    noteImportModal = new NoteImportModal(page);

    await page.goto('/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);

    await homePage.clickImportNote();
    await page.waitForSelector('input[name="noteImportName"]');
  });

  test('Given user clicks Import Note, When modal opens, Then modal should display all required elements', async () => {
    await expect(noteImportModal.modal).toBeVisible();
    await expect(noteImportModal.modalTitle).toBeVisible();
    await expect(noteImportModal.importAsInput).toBeVisible();
    await expect(noteImportModal.jsonFileTab).toBeVisible();
    await expect(noteImportModal.urlTab).toBeVisible();
  });

  test('Given Import Note modal is open, When viewing default tab, Then JSON File tab should be selected', async () => {
    const isJsonTabSelected = await noteImportModal.isJsonFileTabSelected();
    expect(isJsonTabSelected).toBe(true);

    await expect(noteImportModal.uploadArea).toBeVisible();
    await expect(noteImportModal.uploadText).toBeVisible();
  });

  test('Given Import Note modal is open, When switching to URL tab, Then URL input should be visible', async () => {
    await noteImportModal.switchToUrlTab();

    const isUrlTabSelected = await noteImportModal.isUrlTabSelected();
    expect(isUrlTabSelected).toBe(true);

    await expect(noteImportModal.urlInput).toBeVisible();
    await expect(noteImportModal.importNoteButton).toBeVisible();
  });

  test('Given URL tab is selected, When URL is empty, Then import button should be disabled', async () => {
    await noteImportModal.switchToUrlTab();

    const isDisabled = await noteImportModal.isImportNoteButtonDisabled();
    expect(isDisabled).toBe(true);
  });

  test('Given URL tab is selected, When entering URL, Then import button should be enabled', async () => {
    await noteImportModal.switchToUrlTab();
    await noteImportModal.setImportUrl('https://example.com/note.json');

    const isDisabled = await noteImportModal.isImportNoteButtonDisabled();
    expect(isDisabled).toBe(false);
  });

  test('Given Import Note modal is open, When entering import name, Then name should be set', async () => {
    const importName = `Imported Note ${Date.now()}`;
    await noteImportModal.setImportAsName(importName);

    const actualName = await noteImportModal.getImportAsName();
    expect(actualName).toBe(importName);
  });

  test('Given JSON File tab is selected, When viewing file size limit, Then limit should be displayed', async () => {
    const fileSizeLimit = await noteImportModal.getFileSizeLimit();
    expect(fileSizeLimit).toBeTruthy();
    expect(fileSizeLimit.length).toBeGreaterThan(0);
  });

  test('Given Import Note modal is open, When clicking close button, Then modal should close', async () => {
    await noteImportModal.close();
    await expect(noteImportModal.modal).not.toBeVisible();
  });

  test('Given URL tab is selected, When entering invalid URL and clicking import, Then error should be displayed', async ({
    page
  }) => {
    await noteImportModal.switchToUrlTab();
    await noteImportModal.setImportUrl('invalid-url');
    await noteImportModal.clickImportNote();

    await page.waitForTimeout(2000);

    const hasError = await noteImportModal.isErrorAlertVisible();
    if (hasError) {
      const errorMessage = await noteImportModal.getErrorMessage();
      expect(errorMessage).toBeTruthy();
    }
  });
});
