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

import { expect, Page } from '@playwright/test';
import { NoteCreateModal } from './note-create-modal';

export class NoteCreateModalUtil {
  constructor(
    private readonly page: Page,
    private readonly modal: NoteCreateModal
  ) {}

  async verifyModalIsOpen(): Promise<void> {
    await expect(this.modal.modal).toBeVisible();
    await expect(this.modal.noteNameInput).toBeVisible();
    await expect(this.modal.createButton).toBeVisible();
  }

  async verifyCloneModalIsOpen(): Promise<void> {
    await expect(this.modal.modal).toBeVisible();
    await expect(this.modal.noteNameInput).toBeVisible();
    await expect(this.modal.cloneButton).toBeVisible();
    await expect(this.modal.interpreterDropdown).not.toBeVisible();
  }

  async verifyDefaultNoteName(expectedPattern: RegExp): Promise<void> {
    const noteName = await this.modal.getNoteName();
    expect(noteName).toMatch(expectedPattern);
  }

  async verifyInterpreterSelectionWorks(): Promise<void> {
    await this.modal.openInterpreterDropdown();
    await expect(this.modal.interpreterOptions.first()).toBeVisible();

    const interpreters = await this.modal.getAvailableInterpreters();
    expect(interpreters.length).toBeGreaterThan(0);
  }

  async verifyFolderCreationInfo(): Promise<void> {
    await expect(this.modal.folderInfoAlert).toBeVisible();
    const text = await this.modal.folderInfoAlert.textContent();
    expect(text).toContain('/');
  }

  async createNoteWithCustomName(name: string, interpreter?: string): Promise<void> {
    await this.modal.setNoteName(name);

    if (interpreter) {
      await this.modal.selectInterpreter(interpreter);
    }

    await this.modal.clickCreate();
    await this.page.waitForURL(/notebook\//);
  }

  async verifyNoteCreationSuccess(noteName: string): Promise<void> {
    expect(this.page.url()).toContain('notebook/');
    const title = await this.page.title();
    expect(title).toContain(noteName);
  }

  async verifyInterpreterSearch(searchTerm: string): Promise<void> {
    await this.modal.searchInterpreter(searchTerm);
    const visibleOptions = await this.modal.interpreterOptions.count();
    expect(visibleOptions).toBeGreaterThan(0);
  }

  async createNoteWithFolderPath(folderPath: string, noteName: string): Promise<void> {
    const fullPath = `${folderPath}/${noteName}`;
    await this.modal.setNoteName(fullPath);
    await this.modal.clickCreate();
    await this.page.waitForURL(/notebook\//);
  }

  async verifyModalClose(): Promise<void> {
    await this.modal.close();
    await expect(this.modal.modal).not.toBeVisible();
  }
}
