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

import { Page } from '@playwright/test';
import { BasePage } from './base-page';
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
