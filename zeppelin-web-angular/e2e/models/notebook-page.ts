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
import { navigateToNotebookWithFallback } from '../utils';
import { BasePage } from './base-page';

export class NotebookPage extends BasePage {
  readonly notebookContainer: Locator;
  readonly actionBar: Locator;
  readonly sidebar: Locator;
  readonly sidebarArea: Locator;
  readonly paragraphContainer: Locator;
  readonly extensionArea: Locator;
  readonly noteFormBlock: Locator;
  readonly paragraphInner: Locator;

  constructor(page: Page) {
    super(page);
    this.notebookContainer = page.locator('.notebook-container');
    this.actionBar = page.locator('zeppelin-notebook-action-bar');
    this.sidebar = page.locator('zeppelin-notebook-sidebar');
    this.sidebarArea = page.locator('.sidebar-area[nz-resizable]');
    this.paragraphContainer = page.locator('zeppelin-notebook-paragraph');
    this.extensionArea = page.locator('.extension-area');
    this.noteFormBlock = page.locator('zeppelin-note-form-block');
    this.paragraphInner = page.locator('.paragraph-inner[nz-row]');
  }

  async navigateToNotebook(noteId: string): Promise<void> {
    await navigateToNotebookWithFallback(this.page, noteId);
  }

  async navigateToNotebookRevision(noteId: string, revisionId: string): Promise<void> {
    await this.page.goto(`/#/notebook/${noteId}/revision/${revisionId}`);
    await this.waitForPageLoad();
  }

  async navigateToNotebookParagraph(noteId: string, paragraphId: string): Promise<void> {
    await this.page.goto(`/#/notebook/${noteId}/paragraph/${paragraphId}`);
    await this.waitForPageLoad();
  }

  async getParagraphCount(): Promise<number> {
    return await this.paragraphContainer.count();
  }

  getParagraphByIndex(index: number): Locator {
    return this.paragraphContainer.nth(index);
  }

  async isSidebarVisible(): Promise<boolean> {
    return await this.sidebarArea.isVisible();
  }

  async getSidebarWidth(): Promise<number> {
    const sidebarElement = await this.sidebarArea.boundingBox();
    return sidebarElement?.width || 0;
  }

  async isExtensionAreaVisible(): Promise<boolean> {
    return await this.extensionArea.isVisible();
  }

  async isNoteFormBlockVisible(): Promise<boolean> {
    return await this.noteFormBlock.isVisible();
  }

  async getNotebookContainerClass(): Promise<string | null> {
    return await this.notebookContainer.getAttribute('class');
  }
}
