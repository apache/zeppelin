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

import { expect, Locator, Page } from '@playwright/test';
import { BasePage } from './base-page';

export class NotebookSidebarPage extends BasePage {
  readonly sidebarContainer: Locator;
  readonly tocButton: Locator;
  readonly fileTreeButton: Locator;
  readonly closeButton: Locator;
  readonly nodeList: Locator;
  readonly noteToc: Locator;

  // Selector constants for state detection
  private static readonly TOC_ALTERNATIVE_SELECTORS = [
    'zeppelin-notebook-sidebar .toc-content',
    'zeppelin-notebook-sidebar .note-toc',
    'zeppelin-notebook-sidebar [class*="toc"]',
    'zeppelin-notebook-sidebar zeppelin-note-toc',
    'zeppelin-notebook-sidebar .sidebar-content zeppelin-note-toc'
  ];

  private static readonly FILE_TREE_ALTERNATIVE_SELECTORS = [
    'zeppelin-notebook-sidebar .file-tree',
    'zeppelin-notebook-sidebar .node-list',
    'zeppelin-notebook-sidebar [class*="file"]',
    'zeppelin-notebook-sidebar [class*="tree"]',
    'zeppelin-notebook-sidebar zeppelin-node-list',
    'zeppelin-notebook-sidebar .sidebar-content zeppelin-node-list'
  ];

  constructor(page: Page) {
    super(page);
    this.sidebarContainer = page.locator('zeppelin-notebook-sidebar');
    this.tocButton = page.getByRole('button', { name: 'Toggle Table of Contents' });
    this.fileTreeButton = page.getByRole('button', { name: 'Toggle File Tree' });
    this.closeButton = page.getByRole('button', { name: 'Close Sidebar' });
    this.nodeList = page.locator('zeppelin-node-list');
    this.noteToc = page.locator('zeppelin-note-toc');
  }

  async openToc(): Promise<void> {
    await this.tocButton.click();
    await expect(this.noteToc).toBeVisible();
  }

  async openFileTree(): Promise<void> {
    await this.fileTreeButton.click();
    await expect(this.nodeList).toBeVisible();
  }

  async closeSidebar(): Promise<void> {
    await this.closeButton.click();
  }

  // Direct visibility checks - failures exposed immediately
  async isSidebarVisible(): Promise<boolean> {
    return await this.sidebarContainer.isVisible();
  }

  async isTocContentVisible(): Promise<boolean> {
    return await this.noteToc.isVisible();
  }

  async isFileTreeContentVisible(): Promise<boolean> {
    return await this.nodeList.isVisible();
  }

  async getSidebarState(): Promise<'CLOSED' | 'TOC' | 'FILE_TREE' | 'UNKNOWN'> {
    if (!(await this.isSidebarVisible())) {
      return 'CLOSED';
    }

    // Method 1: Check primary content elements
    const primaryState = await this.checkByPrimaryContent();
    if (primaryState) {
      return primaryState;
    }

    // Method 2: Check alternative TOC selectors
    if (await this.checkTocByAlternativeSelectors()) {
      return 'TOC';
    }

    // Method 3: Check alternative FileTree selectors
    if (await this.checkFileTreeByAlternativeSelectors()) {
      return 'FILE_TREE';
    }

    // Method 4: Check active button states
    const buttonState = await this.checkByButtonState();
    if (buttonState) {
      return buttonState;
    }

    // Method 5: Check content text patterns
    const contentState = await this.checkByContentText();
    if (contentState) {
      return contentState;
    }

    console.log('Could not determine sidebar state');
    return 'UNKNOWN';
  }

  async getTocItems(): Promise<string[]> {
    const tocItems = this.noteToc.locator('li');
    const count = await tocItems.count();
    const items: string[] = [];

    for (let i = 0; i < count; i++) {
      const text = await tocItems.nth(i).textContent();
      if (text) {
        items.push(text.trim());
      }
    }

    return items;
  }

  async getFileTreeItems(): Promise<string[]> {
    const fileItems = this.nodeList.locator('li');
    const count = await fileItems.count();
    const items: string[] = [];

    for (let i = 0; i < count; i++) {
      const text = await fileItems.nth(i).textContent();
      if (text) {
        items.push(text.trim());
      }
    }

    return items;
  }

  async clickTocItem(itemText: string): Promise<void> {
    await this.noteToc.locator(`li:has-text("${itemText}")`).click();
  }

  async clickFileTreeItem(itemText: string): Promise<void> {
    await this.nodeList.locator(`li:has-text("${itemText}")`).click();
  }

  // ===== PRIVATE HELPER METHODS FOR STATE DETECTION =====

  private async checkByPrimaryContent(): Promise<'TOC' | 'FILE_TREE' | null> {
    const isTocVisible = await this.isTocContentVisible();
    const isFileTreeVisible = await this.isFileTreeContentVisible();

    console.log(`State detection - TOC visible: ${isTocVisible}, FileTree visible: ${isFileTreeVisible}`);

    if (isTocVisible) {
      return 'TOC';
    }
    if (isFileTreeVisible) {
      return 'FILE_TREE';
    }
    return null;
  }

  private async checkTocByAlternativeSelectors(): Promise<boolean> {
    for (const selector of NotebookSidebarPage.TOC_ALTERNATIVE_SELECTORS) {
      if (await this.page.locator(selector).isVisible()) {
        console.log(`Found TOC using selector: ${selector}`);
        return true;
      }
    }
    return false;
  }

  private async checkFileTreeByAlternativeSelectors(): Promise<boolean> {
    for (const selector of NotebookSidebarPage.FILE_TREE_ALTERNATIVE_SELECTORS) {
      if (await this.page.locator(selector).isVisible()) {
        console.log(`Found FileTree using selector: ${selector}`);
        return true;
      }
    }
    return false;
  }

  private async checkByButtonState(): Promise<'TOC' | 'FILE_TREE' | null> {
    const tocButtonActive = await this.page
      .locator(
        'zeppelin-notebook-sidebar button.active:has(i[nzType="unordered-list"]), zeppelin-notebook-sidebar .active:has(i[nzType="unordered-list"])'
      )
      .isVisible();

    if (tocButtonActive) {
      console.log('Found active TOC button');
      return 'TOC';
    }

    const fileTreeButtonActive = await this.page
      .locator(
        'zeppelin-notebook-sidebar button.active:has(i[nzType="folder"]), zeppelin-notebook-sidebar .active:has(i[nzType="folder"])'
      )
      .isVisible();

    if (fileTreeButtonActive) {
      console.log('Found active FileTree button');
      return 'FILE_TREE';
    }

    return null;
  }

  private async checkByContentText(): Promise<'TOC' | 'FILE_TREE' | null> {
    const hasAnyContent = (await this.page.locator('zeppelin-notebook-sidebar *').count()) > 1;
    if (!hasAnyContent) {
      return null;
    }

    const sidebarText = (await this.page.locator('zeppelin-notebook-sidebar').textContent()) || '';
    if (sidebarText.toLowerCase().includes('heading') || sidebarText.toLowerCase().includes('title')) {
      console.log('Guessing TOC based on content text');
      return 'TOC';
    }

    console.log('Defaulting to FILE_TREE as fallback');
    return 'FILE_TREE';
  }
}
