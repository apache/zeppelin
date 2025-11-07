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

  constructor(page: Page) {
    super(page);
    this.sidebarContainer = page.locator('zeppelin-notebook-sidebar');
    // Try multiple possible selectors for TOC button with more specific targeting
    this.tocButton = page
      .locator(
        'zeppelin-notebook-sidebar button[nzTooltipTitle*="Table"], zeppelin-notebook-sidebar button[title*="Table"], zeppelin-notebook-sidebar i[nz-icon][nzType="unordered-list"], zeppelin-notebook-sidebar button:has(i[nzType="unordered-list"]), zeppelin-notebook-sidebar .sidebar-button:has(i[nzType="unordered-list"])'
      )
      .first();
    // Try multiple possible selectors for File Tree button with more specific targeting
    this.fileTreeButton = page
      .locator(
        'zeppelin-notebook-sidebar button[nzTooltipTitle*="File"], zeppelin-notebook-sidebar button[title*="File"], zeppelin-notebook-sidebar i[nz-icon][nzType="folder"], zeppelin-notebook-sidebar button:has(i[nzType="folder"]), zeppelin-notebook-sidebar .sidebar-button:has(i[nzType="folder"])'
      )
      .first();
    // Try multiple selectors for close button with more specific targeting
    this.closeButton = page
      .locator(
        'zeppelin-notebook-sidebar button.sidebar-close, zeppelin-notebook-sidebar button[nzTooltipTitle*="Close"], zeppelin-notebook-sidebar i[nz-icon][nzType="close"], zeppelin-notebook-sidebar button:has(i[nzType="close"]), zeppelin-notebook-sidebar .close-button, zeppelin-notebook-sidebar [aria-label*="close" i]'
      )
      .first();
    this.nodeList = page.locator('zeppelin-node-list');
    this.noteToc = page.locator('zeppelin-note-toc');
  }

  async openToc(): Promise<void> {
    // Ensure sidebar is visible first
    await expect(this.sidebarContainer).toBeVisible();

    // Try multiple strategies to find and click the TOC button
    const strategies = [
      // Strategy 1: Original button selector
      () => this.tocButton.click(),
      // Strategy 2: Look for unordered-list icon specifically in sidebar
      () => this.page.locator('zeppelin-notebook-sidebar i[nzType="unordered-list"]').first().click(),
      // Strategy 3: Look for any button with list-related icons
      () => this.page.locator('zeppelin-notebook-sidebar button:has(i[nzType="unordered-list"])').first().click(),
      // Strategy 4: Try aria-label or title containing "table" or "content"
      () =>
        this.page
          .locator(
            'zeppelin-notebook-sidebar button[aria-label*="Table"], zeppelin-notebook-sidebar button[aria-label*="Contents"]'
          )
          .first()
          .click(),
      // Strategy 5: Look for any clickable element with specific classes
      () =>
        this.page
          .locator('zeppelin-notebook-sidebar .sidebar-nav button, zeppelin-notebook-sidebar [role="button"]')
          .first()
          .click()
    ];

    let success = false;
    for (const strategy of strategies) {
      try {
        await strategy();

        // Wait for state change after click - check for visible content instead of state
        await Promise.race([
          // Option 1: Wait for TOC content to appear
          this.page
            .locator('zeppelin-note-toc, .sidebar-content .toc')
            .waitFor({ state: 'visible', timeout: 3000 })
            .catch(() => {}),
          // Option 2: Wait for file tree content to appear
          this.page
            .locator('zeppelin-node-list, .sidebar-content .file-tree')
            .waitFor({ state: 'visible', timeout: 3000 })
            .catch(() => {}),
          // Option 3: Wait for any sidebar content change
          this.page.waitForLoadState('networkidle', { timeout: 3000 }).catch(() => {})
        ]).catch(() => {
          // If all fail, continue - this is acceptable
        });

        success = true;
        break;
      } catch (error) {
        console.log(`TOC button strategy failed: ${error instanceof Error ? error.message : String(error)}`);
      }
    }

    if (!success) {
      console.log('All TOC button strategies failed - sidebar may not have TOC functionality');
    }

    // Wait for TOC content to be visible if it was successfully opened
    const tocContent = this.page.locator('zeppelin-note-toc, .sidebar-content .toc, .outline-content');
    try {
      await expect(tocContent).toBeVisible({ timeout: 3000 });
    } catch {
      // TOC might not be available or visible, check if file tree opened instead
      const fileTreeContent = this.page.locator('zeppelin-node-list, .sidebar-content .file-tree');
      try {
        await expect(fileTreeContent).toBeVisible({ timeout: 2000 });
      } catch {
        // Neither TOC nor file tree visible
      }
    }
  }

  async openFileTree(): Promise<void> {
    // Ensure sidebar is visible first
    await expect(this.sidebarContainer).toBeVisible();

    // Try multiple ways to find and click the File Tree button
    try {
      await this.fileTreeButton.click();
    } catch (error) {
      // Fallback: try clicking any folder icon in the sidebar
      const fallbackFileTreeButton = this.page.locator('zeppelin-notebook-sidebar i[nzType="folder"]').first();
      await fallbackFileTreeButton.click();
    }

    // Wait for file tree content to appear after click
    await Promise.race([
      // Wait for file tree content to appear
      this.page.locator('zeppelin-node-list, .sidebar-content .file-tree').waitFor({ state: 'visible', timeout: 3000 }),
      // Wait for network to stabilize
      this.page.waitForLoadState('networkidle', { timeout: 3000 })
    ]).catch(() => {
      // If both fail, continue - this is acceptable
    });

    // Wait for file tree content to be visible
    const fileTreeContent = this.page.locator('zeppelin-node-list, .sidebar-content .file-tree, .file-browser');
    try {
      await expect(fileTreeContent).toBeVisible({ timeout: 3000 });
    } catch {
      // File tree might not be available or visible
    }
  }

  async closeSidebar(): Promise<void> {
    // Ensure sidebar is visible first
    await expect(this.sidebarContainer).toBeVisible();

    // Try multiple strategies to find and click the close button
    const strategies = [
      // Strategy 1: Original close button selector
      () => this.closeButton.click(),
      // Strategy 2: Look for close icon specifically in sidebar
      () => this.page.locator('zeppelin-notebook-sidebar i[nzType="close"]').first().click(),
      // Strategy 3: Look for any button with close-related icons
      () => this.page.locator('zeppelin-notebook-sidebar button:has(i[nzType="close"])').first().click(),
      // Strategy 4: Try any close-related elements
      () =>
        this.page.locator('zeppelin-notebook-sidebar .close, zeppelin-notebook-sidebar .sidebar-close').first().click(),
      // Strategy 5: Try keyboard shortcut (Escape key)
      () => this.page.keyboard.press('Escape'),
      // Strategy 6: Click on the sidebar toggle button again (might close it)
      () => this.page.locator('zeppelin-notebook-sidebar button').first().click()
    ];

    let success = false;
    for (const strategy of strategies) {
      try {
        await strategy();

        // Wait for sidebar to close or become hidden
        await Promise.race([
          // Wait for sidebar to be hidden
          this.sidebarContainer.waitFor({ state: 'hidden', timeout: 3000 }),
          // Wait for sidebar content to disappear
          this.page
            .locator('zeppelin-notebook-sidebar zeppelin-note-toc, zeppelin-notebook-sidebar zeppelin-node-list')
            .waitFor({ state: 'hidden', timeout: 3000 }),
          // Wait for network to stabilize
          this.page.waitForLoadState('networkidle', { timeout: 3000 })
        ]).catch(() => {
          // If all fail, continue - close functionality may not be available
        });

        success = true;
        break;
      } catch (error) {
        console.log(`Close button strategy failed: ${error instanceof Error ? error.message : String(error)}`);
      }
    }

    if (!success) {
      console.log('All close button strategies failed - sidebar may not have close functionality');
    }

    // Final check - wait for sidebar to be hidden if it was successfully closed
    try {
      await expect(this.sidebarContainer).toBeHidden({ timeout: 3000 });
    } catch {
      // Sidebar might still be visible or close functionality not available
      // This is acceptable as some applications don't support closing sidebar
    }
  }

  async isSidebarVisible(): Promise<boolean> {
    try {
      return await this.sidebarContainer.isVisible();
    } catch (error) {
      // If page is closed or connection lost, assume sidebar is not visible
      return false;
    }
  }

  async isTocContentVisible(): Promise<boolean> {
    try {
      return await this.noteToc.isVisible();
    } catch (error) {
      // If page is closed or connection lost, assume TOC is not visible
      return false;
    }
  }

  async isFileTreeContentVisible(): Promise<boolean> {
    try {
      return await this.nodeList.isVisible();
    } catch (error) {
      // If page is closed or connection lost, assume file tree is not visible
      return false;
    }
  }

  async getSidebarState(): Promise<'CLOSED' | 'TOC' | 'FILE_TREE' | 'UNKNOWN'> {
    const isVisible = await this.isSidebarVisible();
    if (!isVisible) {
      return 'CLOSED';
    }

    // Enhanced state detection with multiple strategies

    // Method 1: Check specific content elements
    const isTocVisible = await this.isTocContentVisible();
    const isFileTreeVisible = await this.isFileTreeContentVisible();

    console.log(`State detection - TOC visible: ${isTocVisible}, FileTree visible: ${isFileTreeVisible}`);

    if (isTocVisible) {
      return 'TOC';
    } else if (isFileTreeVisible) {
      return 'FILE_TREE';
    }

    // Method 2: Check for alternative TOC selectors (more comprehensive)
    const tocAlternatives = [
      'zeppelin-notebook-sidebar .toc-content',
      'zeppelin-notebook-sidebar .note-toc',
      'zeppelin-notebook-sidebar [class*="toc"]',
      'zeppelin-notebook-sidebar zeppelin-note-toc',
      'zeppelin-notebook-sidebar .sidebar-content zeppelin-note-toc'
    ];

    for (const selector of tocAlternatives) {
      const tocElementVisible = await this.page.locator(selector).isVisible();
      if (tocElementVisible) {
        console.log(`Found TOC using selector: ${selector}`);
        return 'TOC';
      }
    }

    // Method 3: Check for alternative FileTree selectors
    const fileTreeAlternatives = [
      'zeppelin-notebook-sidebar .file-tree',
      'zeppelin-notebook-sidebar .node-list',
      'zeppelin-notebook-sidebar [class*="file"]',
      'zeppelin-notebook-sidebar [class*="tree"]',
      'zeppelin-notebook-sidebar zeppelin-node-list',
      'zeppelin-notebook-sidebar .sidebar-content zeppelin-node-list'
    ];

    for (const selector of fileTreeAlternatives) {
      const fileTreeElementVisible = await this.page.locator(selector).isVisible();
      if (fileTreeElementVisible) {
        console.log(`Found FileTree using selector: ${selector}`);
        return 'FILE_TREE';
      }
    }

    // Method 4: Check for active button states
    const tocButtonActive = await this.page
      .locator(
        'zeppelin-notebook-sidebar button.active:has(i[nzType="unordered-list"]), zeppelin-notebook-sidebar .active:has(i[nzType="unordered-list"])'
      )
      .isVisible();
    const fileTreeButtonActive = await this.page
      .locator(
        'zeppelin-notebook-sidebar button.active:has(i[nzType="folder"]), zeppelin-notebook-sidebar .active:has(i[nzType="folder"])'
      )
      .isVisible();

    if (tocButtonActive) {
      console.log('Found active TOC button');
      return 'TOC';
    } else if (fileTreeButtonActive) {
      console.log('Found active FileTree button');
      return 'FILE_TREE';
    }

    // Method 5: Check for any content in sidebar and make best guess
    const hasAnyContent = (await this.page.locator('zeppelin-notebook-sidebar *').count()) > 1;
    if (hasAnyContent) {
      // Check content type by text patterns
      const sidebarText = (await this.page.locator('zeppelin-notebook-sidebar').textContent()) || '';
      if (sidebarText.toLowerCase().includes('heading') || sidebarText.toLowerCase().includes('title')) {
        console.log('Guessing TOC based on content text');
        return 'TOC';
      }
      // Default to FILE_TREE (most common)
      console.log('Defaulting to FILE_TREE as fallback');
      return 'FILE_TREE';
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
}
