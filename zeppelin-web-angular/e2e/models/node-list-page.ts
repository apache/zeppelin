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

import { Locator, Page } from '@playwright/test';
import { BasePage } from './base-page';

export class NodeListPage extends BasePage {
  readonly nodeListContainer: Locator;
  readonly importNoteButton: Locator;
  readonly createNewNoteButton: Locator;
  readonly filterInput: Locator;
  readonly treeView: Locator;
  readonly notes: Locator;
  readonly trashFolder: Locator;

  constructor(page: Page) {
    super(page);
    this.nodeListContainer = page.locator('zeppelin-node-list');
    this.importNoteButton = page.getByText('Import Note', { exact: true }).first();
    this.createNewNoteButton = page.getByText('Create new Note', { exact: true }).first();
    this.filterInput = page.locator('zeppelin-node-list input[placeholder*="Filter"]');
    this.treeView = page.locator('zeppelin-node-list nz-tree');
    this.notes = page.locator('nz-tree-node').filter({ has: page.locator('.ant-tree-node-content-wrapper .file') });
    this.trashFolder = page.locator('nz-tree-node').filter({ hasText: '~Trash' });
  }

  async clickImportNote(): Promise<void> {
    await this.importNoteButton.click();
  }

  async clickCreateNewNote(): Promise<void> {
    await this.createNewNoteButton.click();
  }

  async filterNotes(searchTerm: string): Promise<void> {
    await this.filterInput.fill(searchTerm);
  }

  getFolderByName(folderName: string): Locator {
    return this.page.locator('nz-tree-node').filter({ hasText: folderName }).first();
  }

  getNoteByName(noteName: string): Locator {
    return this.page.locator('nz-tree-node').filter({ hasText: noteName }).first();
  }

  async clickNote(noteName: string): Promise<void> {
    const note = await this.getNoteByName(noteName);
    // Target the specific link that navigates to the notebook (has href with "#/notebook/")
    const noteLink = note.locator('a[href*="#/notebook/"]');
    await noteLink.click();
  }

  async isFilterInputVisible(): Promise<boolean> {
    return this.filterInput.isVisible();
  }

  async isTrashFolderVisible(): Promise<boolean> {
    return this.trashFolder.isVisible();
  }

  async getAllVisibleNoteNames(): Promise<string[]> {
    const noteElements = await this.notes.all();
    const names: string[] = [];
    for (const note of noteElements) {
      const text = await note.textContent();
      if (text) {
        names.push(text.trim());
      }
    }
    return names;
  }
}
