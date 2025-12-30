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
import { BasePage } from './base-page';
import { WorkspacePage } from './workspace-page';

export class WorkspaceUtil extends BasePage {
  private workspacePage: WorkspacePage;

  constructor(page: Page) {
    super(page);
    this.workspacePage = new WorkspacePage(page);
  }

  async verifyHeaderVisibility(shouldBeVisible: boolean): Promise<void> {
    if (shouldBeVisible) {
      await expect(this.workspacePage.zeppelinHeader).toBeVisible();
    } else {
      await expect(this.workspacePage.zeppelinHeader).toBeHidden();
    }
  }

  async verifyRouterOutletActivation(): Promise<void> {
    await expect(this.workspacePage.routerOutlet).toBeAttached();
    await this.waitForRouterOutletChild();
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
