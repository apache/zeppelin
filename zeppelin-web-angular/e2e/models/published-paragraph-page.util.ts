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

import { randomUUID } from 'node:crypto';
import { APIRequestContext, expect, Page } from '@playwright/test';
import { BasePage, E2E_TEST_FOLDER } from './base-page';
import { PublishedParagraphPage } from './published-paragraph-page';

export class PublishedParagraphTestUtil extends BasePage {
  private publishedParagraphPage: PublishedParagraphPage;

  constructor(page: Page) {
    super(page);
    this.publishedParagraphPage = new PublishedParagraphPage(page);
  }

  async navigateToPublishedParagraph(noteId: string, paragraphId: string): Promise<void> {
    await this.publishedParagraphPage.navigateToPublishedParagraph(noteId, paragraphId);
  }

  async getErrorModalContent(): Promise<string> {
    return this.publishedParagraphPage.getErrorModalContent();
  }

  async clickErrorModalOk(): Promise<void> {
    await this.publishedParagraphPage.clickErrorModalOk();
  }

  generateNonExistentIds(): { noteId: string; paragraphId: string } {
    const timestamp = Date.now();
    return {
      noteId: `NON_EXISTENT_NOTEBOOK_${timestamp}`,
      paragraphId: `NON_EXISTENT_PARAGRAPH_${timestamp}`
    };
  }
}

export const importPublishedTable = async (request: APIRequestContext): Promise<string> => {
  const name = `published-table-${randomUUID()}`;
  const response = await request.post('/api/notebook/import', {
    params: { notePath: `/${E2E_TEST_FOLDER}/${name}` },
    data: {
      name,
      paragraphs: [
        {
          title: 'Saved table',
          text: '%md Saved table',
          status: 'FINISHED',
          config: { results: { '0': { graph: { mode: 'table' } } } },
          results: { code: 'SUCCESS', msg: [{ type: 'TABLE', data: 'city\tsales\nSeoul\t30\nBusan\t20\n' }] }
        }
      ],
      config: {}
    }
  });
  expect(response.ok(), `Notebook import failed (${response.status()}): ${await response.text()}`).toBe(true);
  return (await response.json()).body as string;
};
