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

export class InterpreterPage {
  readonly title: Locator;
  readonly description: Locator;
  readonly search: Locator;
  readonly repositoryButton: Locator;
  readonly createCard: Locator;
  readonly settings: Locator;
  readonly settingTitles: Locator;

  constructor(readonly page: Page) {
    this.title = page.getByRole('heading', { level: 2, name: /^Interpreters(?:\s|$)/ });
    this.description = page.getByText('Manage interpreters settings.', { exact: false });
    this.search = page.getByPlaceholder('Search interpreters...', { exact: true });
    this.repositoryButton = page.getByRole('button', { name: 'Repository', exact: true });
    this.createCard = page.getByText('Create', { exact: true });
    this.settings = page.getByTestId('interpreter-setting');
    this.settingTitles = this.settings
      .getByRole('heading', { level: 3 })
      .filter({ has: page.getByTestId('interpreter-status') });
  }

  setting(name: string): InterpreterItemPage {
    const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    return new InterpreterItemPage(
      this.page,
      this.settings.filter({
        has: this.page.getByRole('heading', { level: 3, name: new RegExp(`^${escaped}\\s+%`) })
      })
    );
  }

  createForm(): InterpreterItemPage {
    return new InterpreterItemPage(this.page, this.page.getByTestId('interpreter-create-form'));
  }
}

export class InterpreterItemPage {
  readonly edit: Locator;
  readonly restart: Locator;
  readonly remove: Locator;
  readonly save: Locator;
  readonly cancel: Locator;
  readonly name: Locator;
  readonly group: Locator;
  readonly status: Locator;
  readonly properties: Locator;
  readonly dependencies: Locator;
  readonly newProperty: Locator;
  readonly propertyKey: Locator;
  readonly propertyValue: Locator;
  readonly propertyType: Locator;
  readonly artifact: Locator;
  readonly exclusions: Locator;
  readonly existingProcess: Locator;
  readonly host: Locator;
  readonly port: Locator;
  readonly setPermission: Locator;
  readonly owners: Locator;
  readonly tooltip: Locator;
  readonly bindingMenu: Locator;
  readonly bindingMode: Locator;

  constructor(
    readonly page: Page,
    readonly card: Locator
  ) {
    this.edit = card.getByRole('button', { name: 'Edit', exact: true });
    this.restart = card.getByRole('button', { name: 'Restart', exact: true });
    this.remove = card.getByRole('button', { name: 'Remove', exact: true });
    this.save = card.getByRole('button', { name: 'Save', exact: true });
    this.cancel = card.getByRole('button', { name: 'Cancel', exact: true });
    this.name = card.getByPlaceholder('interpreter name', { exact: true });
    this.group = card.getByTestId('interpreter-group');
    this.status = card.getByTestId('interpreter-status');
    this.properties = card.getByTestId('interpreter-properties');
    this.dependencies = card.getByTestId('interpreter-dependencies');
    this.newProperty = this.properties
      .getByRole('row')
      .filter({ has: page.getByRole('button', { name: 'Add property', exact: true }) });
    this.propertyKey = this.newProperty.getByRole('cell').nth(0).getByRole('textbox');
    this.propertyValue = this.newProperty.getByRole('cell').nth(1).locator('input:not([readonly]), textarea');
    this.propertyType = this.newProperty.locator('.type-selector');
    this.artifact = card.getByPlaceholder('groupId:artifactId:version or local file path', { exact: true });
    this.exclusions = card.getByPlaceholder('(Optional) comma separated groupId:artifactId list', { exact: true });
    this.existingProcess = card.getByRole('checkbox', { name: 'Connect to existing process', exact: true });
    this.host = card.getByRole('textbox', { name: 'Host', exact: true });
    this.port = card.getByRole('spinbutton', { name: 'Port', exact: true });
    this.setPermission = card.getByRole('checkbox', { name: 'Set permission', exact: true });
    this.owners = card.getByTestId('interpreter-owners');
    this.tooltip = page.locator('.ant-tooltip-inner:visible');
    this.bindingMenu = page.locator('.ant-dropdown:visible');
    this.bindingMode = card.locator('.ant-dropdown-trigger').first();
  }

  propertyRow(key: string): Locator {
    return this.properties.getByRole('row').filter({ has: this.page.getByRole('cell', { name: key, exact: true }) });
  }

  propertyCell(key: string): Locator {
    return this.propertyRow(key).getByRole('cell').nth(1);
  }

  passwordValue(key: string): Locator {
    return this.propertyCell(key).locator('input[type="password"]');
  }

  editableDependencyRow(index: number): Locator {
    return this.dependencies
      .getByRole('row')
      .filter({
        has: this.page.getByRole('button', { name: 'Remove dependency', exact: true })
      })
      .nth(index);
  }

  dependencyRow(artifact: string): Locator {
    return this.dependencies
      .getByRole('row')
      .filter({ has: this.page.getByRole('cell', { name: artifact, exact: true }) });
  }

  async selectGroup(name: string): Promise<void> {
    await this.group.click();
    await this.page.locator('.ant-select-dropdown:visible').getByText(name, { exact: true }).click();
  }

  async selectPropertyType(name: string): Promise<void> {
    await this.propertyType.click();
    await this.page.locator('.ant-select-dropdown:visible').getByText(name, { exact: true }).click();
  }
}
