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

  async ensureEditMode(): Promise<void> {
    if (!(await this.noteTitleInput.isVisible())) {
      await this.clickTitle();
    }
    await this.noteTitleInput.waitFor({ state: 'visible' });
  }

  async clickTitle(): Promise<void> {
    await this.noteTitle.click();
    await this.noteTitleInput.waitFor({ state: 'visible', timeout: 5000 });
  }

  async enterTitle(title: string): Promise<void> {
    await this.ensureEditMode();
    await this.noteTitleInput.fill(title);
  }

  async clearTitle(): Promise<void> {
    await this.ensureEditMode();
    await this.noteTitleInput.clear();
  }

  async pressEnter(): Promise<void> {
    await this.noteTitleInput.waitFor({ state: 'visible', timeout: 5000 });
    await this.noteTitleInput.press('Enter');
    await this.noteTitleInput.waitFor({ state: 'hidden', timeout: 5000 });
  }

  async pressEscape(): Promise<void> {
    await this.noteTitleInput.waitFor({ state: 'visible', timeout: 5000 });
    await this.noteTitleInput.press('Escape');
    await this.noteTitleInput.waitFor({ state: 'hidden', timeout: 5000 });
  }

  async blur(): Promise<void> {
    await this.noteTitleInput.blur();
  }

  async getTitle(): Promise<string> {
    return (await this.noteTitle.textContent()) || '';
  }

  async isTitleInputVisible(): Promise<boolean> {
    return this.noteTitleInput.isVisible();
  }
}
