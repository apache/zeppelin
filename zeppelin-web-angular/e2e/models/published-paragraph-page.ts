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
import { navigateToNotebookWithFallback } from '../utils';
import { BasePage } from './base-page';

export class PublishedParagraphPage extends BasePage {
  readonly confirmationModal: Locator;
  readonly textOutput: Locator;
  readonly angularRenderer: Locator;
  readonly reactWidget: Locator;
  readonly reactWidgetOrEmptyState: Locator;
  readonly dataTable: Locator;
  readonly tableHeaders: Locator;
  readonly tableCells: Locator;
  readonly visualizationModeControls: Locator;
  readonly exportControls: Locator;
  private readonly errorModalContent: Locator;
  private readonly errorModalOkButton: Locator;

  constructor(page: Page) {
    super(page);
    this.errorModalContent = this.page.locator('.ant-modal-body', { hasText: 'Paragraph Not Found' }).last();
    this.errorModalOkButton = page.getByRole('button', { name: 'OK' }).last();
    this.confirmationModal = page.locator('div.ant-modal-confirm').last();
    // The result count is 0 in both modes, so dynamic-forms is the discriminator: it renders only in Angular mode.
    this.angularRenderer = page.locator('zeppelin-notebook-paragraph-dynamic-forms');
    this.reactWidget = page.locator('[data-testid="react-published-paragraph"]');
    this.textOutput = page.locator('zeppelin-publish-paragraph pre');
    // Without paragraph data the remote mounts an <Empty>, so tests that only assert "React took over" accept either.
    this.reactWidgetOrEmptyState = this.reactWidget.or(page.getByTestId('react-empty-paragraph'));
    this.dataTable = page.getByRole('table');
    // NG-ZORRO exposes its sortable header cells as role=cell, unlike antd's columnheader.
    this.tableHeaders = this.dataTable.locator('thead th');
    this.tableCells = this.dataTable.locator('tbody tr:not(.ant-table-measure-now)').getByRole('cell');
    // Angular's icon-only controls have no accessible names; React's controls have visible labels.
    this.visualizationModeControls = page.locator('.viz-icon:visible').or(
      page.getByRole('button', {
        name: /\b(Table|Bar Chart|Pie Chart|Line Chart|Area Chart|Scatter Chart)$/,
        exact: true
      })
    );
    this.exportControls = page.getByRole('button', { name: /^Export\b/, exact: true });
  }

  async navigateToNotebook(noteId: string): Promise<void> {
    await navigateToNotebookWithFallback(this.page, noteId);
  }

  async navigateToPublishedParagraph(noteId: string, paragraphId: string): Promise<void> {
    await this.navigateToRoute(`/notebook/${noteId}/paragraph/${paragraphId}`);
  }

  async getErrorModalContent(): Promise<string> {
    return await this.getElementText(this.errorModalContent);
  }

  async clickErrorModalOk(): Promise<void> {
    await this.errorModalOkButton.click({ timeout: 15000 });
  }
}
