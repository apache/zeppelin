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

import { expect, Page } from '@playwright/test';
import { performLoginIfRequired, waitForZeppelinReady } from '../utils';
import { WorkspacePage } from './workspace-page';

export class WorkspaceTestUtil {
  private readonly page: Page;
  private readonly workspacePage: WorkspacePage;

  constructor(page: Page) {
    this.page = page;
    this.workspacePage = new WorkspacePage(page);
  }

  async navigateAndWaitForLoad(): Promise<void> {
    await this.workspacePage.navigateToWorkspace();
    await waitForZeppelinReady(this.page);
    await performLoginIfRequired(this.page);
  }

  async verifyWorkspaceLayout(): Promise<void> {
    await expect(this.workspacePage.workspaceComponent).toBeVisible();
    await expect(this.workspacePage.routerOutlet).toBeAttached();
  }

  async verifyHeaderVisibility(shouldBeVisible: boolean): Promise<void> {
    if (shouldBeVisible) {
      await expect(this.workspacePage.header).toBeVisible();
    } else {
      await expect(this.workspacePage.header).toBeHidden();
    }
  }

  async verifyWorkspaceContainer(): Promise<void> {
    await expect(this.workspacePage.workspaceComponent).toBeVisible();
    const contentElements = await this.page.locator('.content').count();
    expect(contentElements).toBeGreaterThan(0);
  }

  async verifyRouterOutletActivation(): Promise<void> {
    await expect(this.workspacePage.routerOutlet).toBeAttached();

    await this.page.waitForFunction(
      () => {
        const workspace = document.querySelector('zeppelin-workspace');
        const outlet = workspace?.querySelector('router-outlet');
        return outlet && outlet.nextElementSibling !== null;
      },
      { timeout: 10000 }
    );
  }

  async waitForComponentActivation(): Promise<void> {
    await this.page.waitForFunction(
      () => {
        const workspace = document.querySelector('zeppelin-workspace');
        const content = workspace?.querySelector('.content');
        return content && content.children.length > 1;
      },
      { timeout: 15000 }
    );
  }
}
