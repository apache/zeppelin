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

export class NotebookSearchPage {
  readonly cards: Locator;
  readonly emptyState: Locator;
  readonly notebookTitle: Locator;

  constructor(private readonly page: Page) {
    this.cards = page.locator('.result-card');
    this.emptyState = page.getByText(/We couldn't find any notebook matching/);
    this.notebookTitle = page.getByTestId('notebook-title');
  }

  resultLink(name: string, destination: string): Locator {
    return this.page.getByRole('link', { name, exact: true }).and(this.page.locator(`a[href="${destination}"]`));
  }

  resultCard(name: string, destination: string): Locator {
    return this.cards.filter({ has: this.resultLink(name, destination) });
  }

  snippet(name: string, destination: string): Locator {
    return this.resultCard(name, destination).locator('pre');
  }

  highlightedKeyword(name: string, destination: string): Locator {
    return this.snippet(name, destination).locator('mark');
  }

  highlightedTitle(name: string, destination: string): Locator {
    return this.resultCard(name, destination).locator('.title-block mark');
  }

  paragraphEditor(paragraphId: string): Locator {
    return this.page.getByTestId(paragraphId).getByRole('textbox', { name: 'Editor content', exact: true });
  }
}
