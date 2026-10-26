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

export class NotebookAssistantPage extends BasePage {
  readonly toggleButton: Locator;
  readonly panel: Locator;
  readonly closeButton: Locator;
  readonly question: Locator;
  readonly sendButton: Locator;
  readonly messages: Locator;

  constructor(page: Page) {
    super(page);
    this.toggleButton = page.getByRole('button', { name: 'Toggle AI Assistant' });
    this.panel = page.getByRole('complementary', { name: 'AI Assistant workspace' });
    this.closeButton = page.getByRole('button', { name: 'Close AI Assistant' });
    this.question = this.panel.getByRole('textbox', { name: 'Question' });
    this.sendButton = this.panel.getByRole('button', { name: 'Send', exact: true });
    this.messages = this.panel.getByRole('region', { name: 'Conversation messages' });
  }

  async selectConversation(title: string): Promise<void> {
    await this.panel.getByRole('button', { name: 'Open conversations' }).click();
    await this.panel
      .getByRole('region', { name: 'Conversations', exact: true })
      .getByRole('button')
      .filter({ has: this.page.getByText(title, { exact: true }) })
      .click();
  }

  async sendQuestion(content: string): Promise<void> {
    await this.question.fill(content);
    await this.sendButton.click();
  }
}
