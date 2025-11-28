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
import { NoteCreateModal } from './note-create-modal';

export class NoteCreateModalUtil {
  constructor(private readonly modal: NoteCreateModal) {}

  async verifyModalIsOpen(): Promise<void> {
    await expect(this.modal.modal).toBeVisible();
    await expect(this.modal.noteNameInput).toBeVisible();
    await expect(this.modal.createButton).toBeVisible();
  }

  async verifyDefaultNoteName(expectedPattern: RegExp): Promise<void> {
    const noteName = await this.modal.getNoteName();
    expect(noteName).toMatch(expectedPattern);
  }

  async verifyFolderCreationInfo(): Promise<void> {
    await expect(this.modal.folderInfoAlert).toBeVisible();
    const text = await this.modal.folderInfoAlert.textContent();
    expect(text).toContain('/');
  }

  async verifyModalClose(): Promise<void> {
    await this.modal.close();
    await expect(this.modal.modal).not.toBeVisible();
  }
}
