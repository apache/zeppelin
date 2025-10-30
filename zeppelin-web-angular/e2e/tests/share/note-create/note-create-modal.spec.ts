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
import { NoteCreateModal } from '../../../models/note-create-modal';
import { NoteCreateModalUtil } from '../../../models/note-create-modal.util';
import { addPageAnnotationBeforeEach, PAGES, performLoginIfRequired, waitForZeppelinReady } from '../../../utils';

test.describe('Note Create Modal', () => {
  let homePage: HomePage;
  let noteCreateModal: NoteCreateModal;
  let noteCreateUtil: NoteCreateModalUtil;

  addPageAnnotationBeforeEach(PAGES.SHARE.NOTE_CREATE);

  test.beforeEach(async ({ page }) => {
    homePage = new HomePage(page);
    noteCreateModal = new NoteCreateModal(page);
    noteCreateUtil = new NoteCreateModalUtil(noteCreateModal);

    await page.goto('/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);

    await homePage.clickCreateNewNote();
    await page.waitForSelector('input[name="noteName"]');
  });

  test('Given user clicks Create New Note, When modal opens, Then modal should display all required elements', async () => {
    await noteCreateUtil.verifyModalIsOpen();
    await expect(noteCreateModal.interpreterDropdown).toBeVisible();
    await noteCreateUtil.verifyFolderCreationInfo();
  });

  test('Given Create Note modal is open, When checking default note name, Then auto-generated name should follow pattern', async () => {
    await noteCreateUtil.verifyDefaultNoteName(/Untitled Note \d+/);
  });

  test('Given Create Note modal is open, When entering custom note name and creating, Then new note should be created successfully', async ({
    page
  }) => {
    const uniqueName = `Test Note ${Date.now()}`;
    await noteCreateModal.setNoteName(uniqueName);
    await noteCreateModal.clickCreate();

    await page.waitForURL(/notebook\//);
    expect(page.url()).toContain('notebook/');
  });

  test('Given Create Note modal is open, When entering note name with folder path, Then note should be created in folder', async ({
    page
  }) => {
    const folderPath = `/TestFolder/SubFolder`;
    const noteName = `Note ${Date.now()}`;
    const fullPath = `${folderPath}/${noteName}`;

    await noteCreateModal.setNoteName(fullPath);
    await noteCreateModal.clickCreate();

    await page.waitForURL(/notebook\//);
    expect(page.url()).toContain('notebook/');
  });

  test('Given Create Note modal is open, When clicking close button, Then modal should close', async () => {
    await noteCreateUtil.verifyModalClose();
  });

  test('Given Create Note modal is open, When viewing folder info alert, Then alert should contain folder creation instructions', async () => {
    const isInfoVisible = await noteCreateModal.isFolderInfoVisible();
    expect(isInfoVisible).toBe(true);
  });
});
