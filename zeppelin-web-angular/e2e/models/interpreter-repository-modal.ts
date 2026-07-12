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
  readonly idInput: Locator;
  readonly urlInput: Locator;
  readonly proxyLoginInput: Locator;
  readonly proxyPasswordInput: Locator;
  readonly addButton: Locator;

  constructor(page: Page) {
    super(page);
    this.repositoryTrigger = page.locator('button.repository-trigger');
    this.createRepositoryTag = page.locator('nz-tag.editable-tag');
    this.idInput = page.locator('input[placeholder="Repository id"]');
    this.urlInput = page.locator('input[placeholder="Repository url"]');
    // Locate the proxy inputs by placeholder, not formControlName: the binding is
    // what the regression test exercises, so the locator must not depend on it.
    this.proxyLoginInput = page.locator('input[placeholder="proxy login"]');
    this.proxyPasswordInput = page.locator('input[placeholder="proxy password"]');
    this.addButton = page.getByRole('button', { name: 'Add', exact: true });
  }

  async navigate(): Promise<void> {
    await this.navigateToRoute('/interpreter');
  }

  async openCreateModal(): Promise<void> {
    await this.repositoryTrigger.click();
    await this.createRepositoryTag.click();
    await this.idInput.waitFor({ state: 'visible' });
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
