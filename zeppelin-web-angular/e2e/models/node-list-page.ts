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
  readonly folders: Locator;
  readonly notes: Locator;
  readonly trashFolder: Locator;

  constructor(page: Page) {
    super(page);
    this.nodeListContainer = page.locator('zeppelin-node-list');
    this.importNoteButton = page.getByText('Import Note', { exact: true }).first();
    this.createNewNoteButton = page.getByText('Create new Note', { exact: true }).first();
    this.filterInput = page.locator('zeppelin-node-list input[placeholder*="Filter"]');
    this.treeView = page.locator('zeppelin-node-list nz-tree');
    this.folders = page.locator('nz-tree-node').filter({ has: page.locator('.ant-tree-node-content-wrapper .folder') });
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

  async clearFilter(): Promise<void> {
    await this.filterInput.clear();
  }

  getFolderByName(folderName: string): Locator {
    return this.page.locator('nz-tree-node').filter({ hasText: folderName }).first();
  }

  getNoteByName(noteName: string): Locator {
    return this.page.locator('nz-tree-node').filter({ hasText: noteName }).first();
  }

  async expandFolder(folderName: string): Promise<void> {
    const folder = await this.getFolderByName(folderName);
    const switcherIcon = folder.locator('.ant-tree-switcher');
    const isExpanded = await folder.getAttribute('aria-expanded');
    if (isExpanded !== 'true') {
      await switcherIcon.click();
    }
  }

  async collapseFolder(folderName: string): Promise<void> {
    const folder = await this.getFolderByName(folderName);
    const switcherIcon = folder.locator('.ant-tree-switcher');
    const isExpanded = await folder.getAttribute('aria-expanded');
    if (isExpanded === 'true') {
      await switcherIcon.click();
    }
  }

  async clickNote(noteName: string): Promise<void> {
    const note = await this.getNoteByName(noteName);
    // Target the specific link that navigates to the notebook (has href with "#/notebook/")
    const noteLink = note.locator('a[href*="#/notebook/"]');
    await noteLink.click();
  }

  async isFolderExpanded(folderName: string): Promise<boolean> {
    // For "Flink Tutorial" folder, check if its child items are visible
    if (folderName.includes('Flink Tutorial')) {
      const flinkBasics = this.page.locator('text=1. Flink Basics').first();
      return await flinkBasics.isVisible();
    }

    // For other folders, use a more generic approach
    const folder = await this.getFolderByName(folderName);

    // Check various expansion indicators
    const isExpanded = await folder.evaluate(node => {
      // Check aria-expanded attribute
      const ariaExpanded = node.getAttribute('aria-expanded');
      if (ariaExpanded === 'true') {
        return true;
      }

      // Check switcher icon classes
      const switcher = node.querySelector('.ant-tree-switcher');
      if (switcher) {
        // Check for various expansion classes
        if (switcher.classList.contains('ant-tree-switcher_open')) {
          return true;
        }
        if (switcher.classList.contains('ant-tree-switcher-icon_open')) {
          return true;
        }

        // Check if switcher points down (expanded) vs right (collapsed)
        const icon = switcher.querySelector('svg, i, span');
        if (icon) {
          const transform = window.getComputedStyle(icon).transform;
          // Typically, expanded folders have rotated icons
          if (transform && transform.includes('matrix')) {
            return true;
          }
        }
      }

      return false;
    });

    return isExpanded;
  }

  async getVisibleFolderCount(): Promise<number> {
    return this.folders.count();
  }

  async getVisibleNoteCount(): Promise<number> {
    return this.notes.count();
  }

  async isFilterInputVisible(): Promise<boolean> {
    return this.filterInput.isVisible();
  }

  async isTreeViewVisible(): Promise<boolean> {
    return this.treeView.isVisible();
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

  async getAllVisibleFolderNames(): Promise<string[]> {
    const folderElements = await this.folders.all();
    const names: string[] = [];
    for (const folder of folderElements) {
      const text = await folder.textContent();
      if (text) {
        names.push(text.trim());
      }
    }
    return names;
  }
}
