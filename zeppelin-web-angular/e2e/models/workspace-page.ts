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

export class WorkspacePage extends BasePage {
  readonly workspaceComponent: Locator;
  readonly header: Locator;
  readonly routerOutlet: Locator;

  constructor(page: Page) {
    super(page);
    this.workspaceComponent = page.locator('zeppelin-workspace');
    this.header = page.locator('zeppelin-header');
    this.routerOutlet = page.locator('zeppelin-workspace router-outlet');
  }

  async navigateToWorkspace(): Promise<void> {
    await this.page.goto('/', { waitUntil: 'load' });
    await this.waitForPageLoad();
  }
}
