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

export class InterpreterRepositoryPage {
  readonly repositoryTrigger: Locator;
  readonly panel: Locator;
  readonly heading: Locator;
  readonly repositoryTags: Locator;
  readonly createRepositoryTag: Locator;
  readonly closeIcons: Locator;
  readonly tooltip: Locator;
  readonly deleteDialog: Locator;
  readonly deleteMessage: Locator;
  readonly confirmDeleteButton: Locator;
  readonly cancelDeleteButton: Locator;

  constructor(private readonly page: Page) {
    this.repositoryTrigger = page.getByRole('button', { name: 'Repository', exact: true });
    this.panel = page.getByTestId('interpreter-repositories');
    this.heading = this.panel.getByRole('heading', { name: 'Repositories', exact: true });
    this.repositoryTags = this.panel.getByTestId('interpreter-repository');
    this.createRepositoryTag = this.panel.getByTestId('create-interpreter-repository');
    this.closeIcons = page.locator('.anticon-close');
    this.tooltip = page.locator('.ant-tooltip-inner:visible');
    this.deleteDialog = page.getByRole('dialog').filter({
      has: page.getByText('Do you want to delete this repository?', { exact: true })
    });
    this.deleteMessage = this.deleteDialog.getByText('Do you want to delete this repository?', { exact: true });
    this.confirmDeleteButton = this.deleteDialog.getByRole('button', { name: 'OK', exact: true });
    this.cancelDeleteButton = this.deleteDialog.getByRole('button', { name: 'Cancel', exact: true });
  }

  repositoryTag(id: string): Locator {
    return this.repositoryTags.and(this.page.getByText(id, { exact: true }));
  }

  repositoryCloseIcon(id: string): Locator {
    return this.repositoryTag(id).locator(this.closeIcons);
  }
}
