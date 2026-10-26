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

test.describe('Notebook AI Assistant fixture conversations', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_ASSISTANT);

  let assistant: NotebookAssistantPage;
  let noteId: string | undefined;

  test.beforeEach(async ({ page }) => {
    noteId = undefined;
    assistant = new NotebookAssistantPage(page);

    await page.goto('/#/');
    await waitForZeppelinReady(page);
    ({ noteId } = await createTestNotebook(page));

    await page.goto(`/#/notebook/${noteId}?reactAssistant=true`);
    await waitForZeppelinReady(page);
    await assistant.toggleButton.click();
    await expect(assistant.question).toBeEditable();
  });

  test.afterEach(async ({ page }) => {
    if (noteId) {
      const response = await page.request.delete(`/api/notebook/${noteId}`);
      expect(response.ok(), 'Delete the disposable notebook').toBe(true);
    }
  });

  test('streams a new answer without remounting on completion and restores its saved history', async () => {
    const question = 'Explain the revenue results for this new conversation.';

    await assistant.sendQuestion(question);

    const answer = assistant.messages.getByRole('article', { name: 'Assistant answer', exact: true });
    await expect(answer).toHaveAttribute('aria-busy', 'true');
    await expect(answer).toContainText('I checked');
    const original = await answer.elementHandle();
    expect(original).not.toBeNull();

    await expect(answer.getByRole('table')).toBeVisible();
    await expect(answer).not.toHaveAttribute('aria-busy', 'true');
    await expect(assistant.question).toBeEditable();
    await expect(assistant.messages).not.toHaveAttribute('aria-busy', 'true');
    expect(await original!.evaluate(element => element.isConnected)).toBe(true);
    await expect(assistant.messages.getByRole('article', { name: 'Your question', exact: true })).toContainText(
      question
    );
    await expect(answer).toHaveCount(1);

    await assistant.closeButton.click();
    await expect(assistant.panel).toBeHidden();
    await assistant.toggleButton.click();
    await assistant.selectConversation('New conversation');

    await expect(assistant.messages).not.toHaveAttribute('aria-busy', 'true');
    await expect(assistant.messages.getByRole('article', { name: 'Your question', exact: true })).toContainText(
      question
    );
    await expect(answer).toHaveCount(1);
    await expect(answer.getByRole('table')).toBeVisible();
  });

  test('keeps B draft and results separate when switching away from an active A run', async () => {
    const aQuestion = 'Compare revenue for conversation A.';
    const bQuestion = 'Summarize the regions for conversation B.';
    const summary = 'This notebook compares monthly revenue across regions.';

    await assistant.selectConversation('Notebook summary');
    await expect(assistant.messages).toContainText(summary);
    await assistant.question.fill(bQuestion);
    await assistant.selectConversation('Revenue analysis');
    await expect(assistant.question).toBeEditable();

    await assistant.sendQuestion(aQuestion);

    await expect(
      assistant.messages.getByRole('article', { name: 'Assistant answer', exact: true }).last()
    ).toHaveAttribute('aria-busy', 'true');

    await assistant.selectConversation('Notebook summary');
    await expect(assistant.question).toHaveValue(bQuestion);
    await expect(assistant.question).toBeEditable();
    await expect(assistant.messages).toContainText(summary);
    await expect(assistant.messages).not.toContainText(aQuestion);

    await assistant.sendButton.click();
    const bAnswer = assistant.messages.getByRole('article', { name: 'Assistant answer', exact: true }).last();

    await expect(bAnswer.getByRole('table')).toBeVisible();
    await expect(bAnswer).not.toHaveAttribute('aria-busy', 'true');
    await expect(assistant.question).toBeEditable();
    await expect(assistant.messages).not.toHaveAttribute('aria-busy', 'true');
    await expect(assistant.messages.getByText(bQuestion, { exact: true })).toHaveCount(1);
    await expect(assistant.messages).toContainText(summary);
    await expect(assistant.messages).not.toContainText(aQuestion);

    await assistant.question.fill('Keep this next B draft.');

    await assistant.selectConversation('Revenue analysis');
    await expect(assistant.messages.getByText(aQuestion, { exact: true })).toHaveCount(1);
    await expect(assistant.messages.getByRole('table')).toHaveCount(1);
    await expect(assistant.question).toBeEditable();
    await expect(assistant.messages).not.toContainText(bQuestion);

    await assistant.selectConversation('Notebook summary');

    await expect(assistant.question).toHaveValue('Keep this next B draft.');
    await expect(assistant.messages.getByText(bQuestion, { exact: true })).toHaveCount(1);
    await expect(assistant.messages.getByRole('table')).toHaveCount(1);
    await expect(assistant.messages).toContainText(summary);
  });

  test('scrolling to earlier history joins the split answer without replacing the later answer', async () => {
    await assistant.selectConversation('Revenue analysis');
    const laterAnswer = assistant.messages.getByRole('article', { name: 'Assistant answer', exact: true }).filter({
      hasText: 'Compare Europe’s order volume with its average order value.'
    });
    await expect(laterAnswer).toBeVisible();
    const original = await laterAnswer.elementHandle();
    expect(original).not.toBeNull();

    await assistant.messages.evaluate(element => {
      element.scrollTop = 0;
    });

    await expect(assistant.messages.getByText('How did revenue change by region?', { exact: true })).toHaveCount(1);
    await expect(assistant.messages).not.toHaveAttribute('aria-busy', 'true');
    const joinedAnswer = assistant.messages.getByRole('article', { name: 'Assistant answer', exact: true }).filter({
      hasText: 'I checked the regional revenue paragraph.'
    });
    await expect(joinedAnswer).toContainText('Revenue increased in all three regions, led by Europe.');
    await expect(assistant.messages.getByRole('article', { name: 'Assistant answer', exact: true })).toHaveCount(2);
    await expect(laterAnswer).toHaveCount(1);
    expect(await original!.evaluate(element => element.isConnected)).toBe(true);
    await expect(assistant.question).toBeEditable();
  });
});
