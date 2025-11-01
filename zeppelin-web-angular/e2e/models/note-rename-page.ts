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

export class NoteRenamePage extends BasePage {
  readonly noteTitle: Locator;
  readonly noteTitleInput: Locator;

  constructor(page: Page) {
    super(page);
    // Note title in elastic input component
    this.noteTitle = page.locator('.elastic p');
    this.noteTitleInput = page.locator('.elastic input');
  }

  async navigate(noteId: string): Promise<void> {
    await this.page.goto(`/#/notebook/${noteId}`);
    await this.waitForPageLoad();
  }

  async clickTitle(): Promise<void> {
    await this.noteTitle.click();
  }

  async enterTitle(title: string): Promise<void> {
    await this.noteTitleInput.fill(title);
  }

  async clearTitle(): Promise<void> {
    await this.noteTitleInput.clear();
  }

  async pressEnter(): Promise<void> {
    await this.noteTitleInput.press('Enter');
  }

  async pressEscape(): Promise<void> {
    await this.noteTitleInput.press('Escape');
  }

  async blur(): Promise<void> {
    await this.noteTitleInput.blur();
  }

  async getTitle(): Promise<string> {
    return (await this.noteTitle.textContent()) || '';
  }

  async getTitleInputValue(): Promise<string> {
    return (await this.noteTitleInput.inputValue()) || '';
  }

  async isTitleInputVisible(): Promise<boolean> {
    return this.noteTitleInput.isVisible();
  }

  async isTitleVisible(): Promise<boolean> {
    return this.noteTitle.isVisible();
  }
}
