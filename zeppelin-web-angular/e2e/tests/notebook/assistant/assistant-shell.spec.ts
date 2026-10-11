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

import { expect, test } from '@playwright/test';
import { NotebookAssistantPage } from '../../../models/notebook-assistant-page';
import { addPageAnnotationBeforeEach, createTestNotebook, PAGES, waitForZeppelinReady } from '../../../utils';

test.describe('Notebook AI Assistant shell', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_ASSISTANT);

  let assistant: NotebookAssistantPage;

  test.beforeEach(async ({ page }) => {
    assistant = new NotebookAssistantPage(page);
  });

  test('opens and closes from the notebook sidebar', async ({ page }) => {
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    const { noteId } = await createTestNotebook(page);
    try {
      await test.step('Given a notebook with the Assistant enabled', async () => {
        await page.goto(`/#/notebook/${noteId}?reactAssistant=true`);
        await waitForZeppelinReady(page);
        await expect(assistant.toggleButton).toBeVisible();
        await expect(assistant.panel).toBeHidden();
      });

      await test.step('When I open the Assistant', async () => {
        await assistant.toggleButton.click();
      });

      await test.step('Then the panel appears', async () => {
        await expect(assistant.toggleButton).toHaveAttribute('aria-pressed', 'true');
        await expect(assistant.panel).toBeVisible();
      });

      await test.step('When I close the Assistant', async () => {
        await assistant.closeButton.click();
      });

      await test.step('Then the notebook returns to its closed state', async () => {
        await expect(assistant.panel).toBeHidden();
        await expect(assistant.toggleButton).toHaveAttribute('aria-pressed', 'false');
      });
    } finally {
      const response = await page.request.delete(`/api/notebook/${noteId}`);
      expect(response.ok(), 'Delete the disposable notebook').toBe(true);
    }
  });
});
