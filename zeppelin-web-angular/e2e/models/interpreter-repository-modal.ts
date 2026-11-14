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

export interface RepositoryProxyCredentials {
  id: string;
  url: string;
  proxyLogin: string;
  proxyPassword: string;
}

export class InterpreterRepositoryModal extends BasePage {
  readonly repositoryTrigger: Locator;
  readonly createRepositoryTag: Locator;
  readonly dialog: Locator;
  readonly idInput: Locator;
  readonly urlInput: Locator;
  readonly urlProtocol: Locator;
  readonly snapshotSwitch: Locator;
  readonly usernameInput: Locator;
  readonly passwordInput: Locator;
  readonly proxyHttpRadio: Locator;
  readonly proxyHttpsRadio: Locator;
  readonly proxyHostInput: Locator;
  readonly proxyPortInput: Locator;
  readonly proxyLoginInput: Locator;
  readonly proxyPasswordInput: Locator;
  readonly addButton: Locator;
  readonly dismissButton: Locator;

  constructor(page: Page) {
    super(page);
    this.repositoryTrigger = page.getByRole('button', { name: 'Repository', exact: true });
    this.createRepositoryTag = page.getByTestId('create-interpreter-repository');
    this.dialog = page.getByRole('dialog').filter({
      has: page.getByText('Add New Repository', { exact: true })
    });
    this.idInput = this.dialog.getByPlaceholder('Repository id', { exact: true });
    this.urlInput = this.dialog.getByPlaceholder('Repository url', { exact: true });
    this.urlProtocol = this.dialog.getByTestId('repository-url-protocol');
    this.snapshotSwitch = this.dialog.locator('.ant-switch');
    this.usernameInput = this.dialog.getByPlaceholder('username', { exact: true });
    this.passwordInput = this.dialog.getByPlaceholder('password', { exact: true });
    this.proxyHttpRadio = this.dialog.getByRole('radio', { name: 'HTTP', exact: true });
    this.proxyHttpsRadio = this.dialog.getByRole('radio', { name: 'HTTPS', exact: true });
    this.proxyHostInput = this.dialog.getByPlaceholder('proxy host', { exact: true });
    this.proxyPortInput = this.dialog.getByPlaceholder('proxy port', { exact: true });
    // The proxy binding is under test, so locate it by its user-facing placeholder.
    this.proxyLoginInput = this.dialog.getByPlaceholder('proxy login', { exact: true });
    this.proxyPasswordInput = this.dialog.getByPlaceholder('proxy password', { exact: true });
    this.addButton = this.dialog.getByRole('button', { name: 'Add', exact: true });
    this.dismissButton = this.dialog.getByRole('button', { name: 'Cancel', exact: true });
  }

  async navigate(): Promise<void> {
    await this.navigateToRoute('/interpreter');
  }

  async openCreateModal(): Promise<void> {
    await this.repositoryTrigger.click();
    await this.createRepositoryTag.click();
    await this.idInput.waitFor({ state: 'visible' });
  }

  async selectUrlProtocol(protocol: 'http://' | 'https://' | 'file://'): Promise<void> {
    await this.urlProtocol.click();
    await this.page.locator('.ant-select-dropdown:visible').getByText(protocol, { exact: true }).click();
  }

  async selectProxyProtocol(protocol: 'HTTP' | 'HTTPS'): Promise<void> {
    await this.dialog.getByText(protocol, { exact: true }).click();
  }

  async fillProxyRepository(form: RepositoryProxyCredentials): Promise<void> {
    await this.fillAndVerifyInput(this.idInput, form.id);
    await this.fillAndVerifyInput(this.urlInput, form.url);
    await this.fillAndVerifyInput(this.proxyLoginInput, form.proxyLogin);
    await this.fillAndVerifyInput(this.proxyPasswordInput, form.proxyPassword);
  }

  async submit(): Promise<void> {
    await this.addButton.click();
  }
}
