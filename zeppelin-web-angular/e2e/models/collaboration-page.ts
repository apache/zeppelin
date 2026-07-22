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
import { waitForZeppelinReady } from '../utils';
import { BasePage } from './base-page';

export class CollaborationPage extends BasePage {
  readonly paragraph: Locator;
  readonly editor: Locator;
  readonly editorText: Locator;
  readonly switchToPersonalModeButton: Locator;
  readonly switchToCollaborationModeButton: Locator;

  constructor(page: Page) {
    super(page);
    // JUSTIFIED: CSS chains into Monaco's third-party DOM — it exposes no roles/test ids.
    this.paragraph = page.locator('zeppelin-notebook-paragraph').first();
    this.editor = this.paragraph.locator('.monaco-editor').first();
    this.editorText = this.paragraph.locator('.view-lines').first();
    this.switchToPersonalModeButton = page.getByRole('button', { name: 'Switch to personal mode' });
    this.switchToCollaborationModeButton = page.getByRole('button', { name: 'Switch to collaboration mode' });
  }

  async openNotebook(noteId: string): Promise<void> {
    await this.page.goto(`/#/notebook/${noteId}`);
    await waitForZeppelinReady(this.page);
    await expect(this.paragraph).toBeVisible({ timeout: 15000 });
  }

  async getPrincipal(): Promise<string> {
    const response = await this.page.request.get('/api/security/ticket', { failOnStatusCode: false });
    if (!response.ok()) {
      return '';
    }
    const json = (await response.json()) as { body?: { principal?: string } };
    return json.body?.principal ?? '';
  }

  async confirmPersonalizedModeChange(): Promise<void> {
    // Scope to this dialog and wait for it to close, so a back-to-back toggle can't race the animation or hit another modal.
    const dialog = this.page.locator('.ant-modal-confirm', { hasText: 'Setting the result display' }).first();
    await expect(dialog).toBeVisible({ timeout: 15000 });
    await dialog.locator('button:has-text("OK")').click();
    await expect(dialog).toBeHidden({ timeout: 15000 });
  }

  async typeInEditor(text: string): Promise<void> {
    await this.editor.click();
    // insertText avoids per-key events that can trigger Monaco autocomplete (see ZEPPELIN-6536).
    await this.page.keyboard.insertText(text);
  }
}
