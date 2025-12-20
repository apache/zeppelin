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

import { expect } from '@playwright/test';
import { NoteRenamePage } from './note-rename-page';

export class NoteRenamePageUtil {
  private noteRenamePage: NoteRenamePage;

  constructor(noteRenamePage: NoteRenamePage) {
    this.noteRenamePage = noteRenamePage;
  }

  async verifyTitleIsDisplayed(): Promise<void> {
    await expect(this.noteRenamePage.noteTitle).toBeVisible();
  }

  async verifyTitleText(expectedTitle: string): Promise<void> {
    const actualTitle = await this.noteRenamePage.getTitle();
    expect(actualTitle).toContain(expectedTitle);
  }

  async verifyTitleInputAppearsOnClick(): Promise<void> {
    await this.noteRenamePage.clickTitle();
    await expect(this.noteRenamePage.noteTitleInput).toBeVisible();
  }

  async verifyTitleCanBeChanged(newTitle: string): Promise<void> {
    await this.noteRenamePage.clickTitle();
    await this.noteRenamePage.clearTitle();
    await this.noteRenamePage.enterTitle(newTitle);
    await this.noteRenamePage.pressEnter();
    await this.noteRenamePage.page.waitForTimeout(500);
    await this.verifyTitleText(newTitle);
  }

  async verifyTitleChangeWithBlur(newTitle: string): Promise<void> {
    await this.noteRenamePage.clickTitle();
    await this.noteRenamePage.clearTitle();
    await this.noteRenamePage.enterTitle(newTitle);
    await this.noteRenamePage.blur();
    await this.noteRenamePage.page.waitForTimeout(500);
    await this.verifyTitleText(newTitle);
  }

  async verifyTitleChangeCancelsOnEscape(originalTitle: string): Promise<void> {
    await this.noteRenamePage.clickTitle();
    await this.noteRenamePage.clearTitle();
    await this.noteRenamePage.enterTitle('Temporary Title');
    await this.noteRenamePage.pressEscape();
    await this.noteRenamePage.page.waitForTimeout(500);
    await this.verifyTitleText(originalTitle);
  }

  async verifyEmptyTitleIsNotAllowed(): Promise<void> {
    const originalTitle = await this.noteRenamePage.getTitle();
    await this.noteRenamePage.clickTitle();
    await this.noteRenamePage.clearTitle();
    await this.noteRenamePage.pressEnter();
    await this.noteRenamePage.page.waitForTimeout(500);
    await this.verifyTitleText(originalTitle);
  }
}
