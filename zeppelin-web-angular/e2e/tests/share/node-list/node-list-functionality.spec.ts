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
import { NodeListPage } from '../../../models/node-list-page';
import { addPageAnnotationBeforeEach, PAGES, performLoginIfRequired, waitForZeppelinReady } from '../../../utils';

test.describe('Node List Functionality', () => {
  let homePage: HomePage;
  let nodeListPage: NodeListPage;

  addPageAnnotationBeforeEach(PAGES.SHARE.NODE_LIST);

  test.beforeEach(async ({ page }) => {
    homePage = new HomePage(page);
    nodeListPage = new NodeListPage(page);

    await page.goto('/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
  });

  test('Given user is on home page, When viewing node list, Then node list should display tree structure', async () => {
    await expect(nodeListPage.nodeListContainer).toBeVisible();
    await expect(nodeListPage.treeView).toBeVisible();
  });

  test('Given user is on home page, When viewing node list, Then action buttons should be visible', async () => {
    await expect(nodeListPage.createNewNoteButton).toBeVisible();
    await expect(nodeListPage.importNoteButton).toBeVisible();
  });

  test('Given user is on home page, When viewing node list, Then filter input should be visible', async () => {
    const isFilterVisible = await nodeListPage.isFilterInputVisible();
    expect(isFilterVisible).toBe(true);
  });

  test('Given user is on home page, When viewing node list, Then trash folder should be visible', async () => {
    const isTrashVisible = await nodeListPage.isTrashFolderVisible();
    expect(isTrashVisible).toBe(true);
  });

  test('Given there are notes in node list, When clicking a note, Then user should navigate to that note', async ({
    page
  }) => {
    await expect(nodeListPage.treeView).toBeVisible();
    const notes = await nodeListPage.getAllVisibleNoteNames();

    if (notes.length > 0 && notes[0]) {
      const noteName = notes[0].trim();

      await nodeListPage.clickNote(noteName);
      await page.waitForURL(/notebook\//);

      expect(page.url()).toContain('notebook/');
    }
  });

  test('Given user clicks Create New Note button, When modal opens, Then note create modal should be displayed', async ({
    page
  }) => {
    await nodeListPage.clickCreateNewNote();
    await page.waitForSelector('input[name="noteName"]');

    const noteNameInput = page.locator('input[name="noteName"]');
    await expect(noteNameInput).toBeVisible();
  });

  test('Given user clicks Import Note button, When modal opens, Then note import modal should be displayed', async ({
    page
  }) => {
    await nodeListPage.clickImportNote();
    await page.waitForSelector('input[name="noteImportName"]');

    const importNameInput = page.locator('input[name="noteImportName"]');
    await expect(importNameInput).toBeVisible();
  });
});
