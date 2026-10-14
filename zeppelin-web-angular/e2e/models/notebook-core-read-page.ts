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

const NOTEBOOK_CORE_READ_QUERY = 'notebookCoreRead';

export class NotebookCoreReadPage extends BasePage {
  readonly view: Locator;
  readonly title: Locator;
  readonly paragraphs: Locator;
  readonly paragraphTitles: Locator;
  readonly results: Locator;
  readonly unsupportedResults: Locator;
  readonly permissions: Locator;
  readonly error: Locator;
  readonly revisionBadge: Locator;
  readonly editableNotebook: Locator;

  constructor(page: Page) {
    super(page);
    this.view = page.getByTestId('notebook-core-read');
    this.title = this.view.getByRole('heading', { level: 2 });
    this.paragraphs = page.getByTestId('notebook-core-read-paragraph');
    this.paragraphTitles = this.paragraphs.getByRole('heading', { level: 3 });
    this.results = page.getByTestId('notebook-core-read-result');
    this.unsupportedResults = page.getByTestId('notebook-core-read-unsupported-result');
    this.permissions = page.getByTestId('notebook-core-read-permissions');
    this.error = page.getByTestId('notebook-core-read-error');
    this.revisionBadge = page.getByTestId('notebook-core-read-revision');
    this.editableNotebook = page.locator('zeppelin-notebook');
  }

  /** The rendered chart of a saved TABLE result; G2 draws into a canvas. */
  resultChart(index: number): Locator {
    return this.results.nth(index).locator('canvas').first();
  }

  resultTable(index: number): Locator {
    return this.results.nth(index).getByRole('table');
  }

  /**
   * Records every `status/connected` pair the view shows from now on. A state that lasts only until the
   * Shell reconnects can be gone before a polling assertion sees it.
   */
  async recordStates(): Promise<() => Promise<string[]>> {
    await this.view.evaluate(view => {
      const states: string[] = [];
      const record = () => states.push(`${view.dataset.status}/${view.dataset.connected}`);
      new MutationObserver(record).observe(view, {
        attributes: true,
        attributeFilter: ['data-status', 'data-connected']
      });
      Object.assign(window, { coreReadStates: states });
    });
    return () => this.page.evaluate(() => (window as unknown as { coreReadStates: string[] }).coreReadStates);
  }

  /** Changes only the hash, so the Shell and its socket stay up. */
  async open(noteId: string, revisionId?: string): Promise<void> {
    const path = revisionId ? `/notebook/${noteId}/revision/${revisionId}` : `/notebook/${noteId}`;
    await this.page.goto(`/#${path}?${NOTEBOOK_CORE_READ_QUERY}`);
  }

  async openEditable(noteId: string): Promise<void> {
    await this.page.goto(`/#/notebook/${noteId}`);
  }
}
