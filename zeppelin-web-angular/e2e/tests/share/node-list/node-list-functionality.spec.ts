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
  let nodeListPage: NodeListPage;

  addPageAnnotationBeforeEach(PAGES.SHARE.NODE_LIST);

  test.beforeEach(async ({ page }) => {
    nodeListPage = new NodeListPage(page);

    await page.goto('/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
  });

  test('Given user is on home page, When viewing node list, Then node list should display tree structure', async () => {
    await expect(nodeListPage.nodeListContainer).toBeVisible();
    await expect(nodeListPage.treeView).toBeVisible();
    await expect(nodeListPage.treeView.locator('nz-tree-node').first()).toBeVisible(); // tree has at least one node
  });

  test('Given user is on home page, When viewing node list, Then action buttons should be visible', async () => {
    await expect(nodeListPage.createNewNoteButton).toBeVisible();
    await expect(nodeListPage.createNewNoteButton).toContainText('Create new Note');
    await expect(nodeListPage.importNoteButton).toBeVisible();
    await expect(nodeListPage.importNoteButton).toContainText('Import Note');
  });

  test('Given user is on home page, When viewing node list, Then filter input should be visible with placeholder', async () => {
    await expect(nodeListPage.filterInput).toBeVisible();
    await expect(nodeListPage.filterInput).toHaveAttribute('placeholder', /[Ff]ilter/);
  });

  test('Given a note has been moved to trash, When viewing node list, Then trash folder should be visible', async ({
    page
  }) => {
    const homePage = new HomePage(page);

    // Create a test note to ensure there is something to trash
    await homePage.createNote('_e2e_trash_test');

    // Navigate back to home
    await page.goto('/');
    await waitForZeppelinReady(page);

    // Wait for the created note to appear in the node list, then hover
    const testNote = page.locator('.node .file').filter({ hasText: '_e2e_trash_test' });
    await expect(testNote).toBeVisible({ timeout: 15000 });
    await testNote.hover();

    // Click the delete icon (nz-popconfirm is on the <i> element)
    const deleteIcon = testNote.locator('.operation i[nztype="delete"]');
    await deleteIcon.click();

    // Confirm the popconfirm dialog (ng-zorro en_US default is "OK", not "Yes")
    await expect(page.locator('text=This note will be moved to trash.')).toBeVisible();
    const confirmButton = page.locator('.ant-popover button:has-text("OK")');
    await confirmButton.click();

    // Wait for the trash folder to appear and verify
    await expect(nodeListPage.trashFolder).toBeVisible({ timeout: 10000 });
  });

  test('Given there are notes in node list, When clicking a note, Then user should navigate to that note', async ({
    page
  }) => {
    const homePage = new HomePage(page);

    await expect(nodeListPage.treeView).toBeVisible();
    let notes = await nodeListPage.getAllVisibleNoteNames();

    if (notes.length === 0) {
      // Seed a note so the test always runs — critical navigation path must not be skipped
      await homePage.createNote(`_e2e_nav_${Date.now()}`);
      await page.goto('/');
      await waitForZeppelinReady(page);
      notes = await nodeListPage.getAllVisibleNoteNames();
    }

    const noteName = notes[0].trim();
    await nodeListPage.clickNote(noteName);
    await page.waitForURL(/notebook\//);
    expect(page.url()).toContain('notebook/');
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
