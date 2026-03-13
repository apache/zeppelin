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

import { expect, test } from '@playwright/test';
import { WorkspacePage } from 'e2e/models/workspace-page';
import { WorkspaceUtil } from '../../models/workspace-page.util';
import { addPageAnnotationBeforeEach, PAGES, performLoginIfRequired, waitForZeppelinReady } from '../../utils';

addPageAnnotationBeforeEach(PAGES.WORKSPACE.MAIN);

test.describe('Workspace Main Component', () => {
  let workspaceUtil: WorkspaceUtil;
  let workspacePage: WorkspacePage;

  test.beforeEach(async ({ page }) => {
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);

    workspacePage = new WorkspacePage(page);
    workspaceUtil = new WorkspaceUtil(page);
  });

  test.describe('Given user accesses workspace container', () => {
    test('When workspace loads Then should display main container structure', async () => {
      await expect(workspacePage.zeppelinWorkspace).toBeVisible();
      // Verify workspace contains the header — not just that the elements exist in isolation
      await expect(workspacePage.zeppelinWorkspace.locator('zeppelin-header')).toBeVisible();
    });

    test('When workspace loads Then should display header component', async () => {
      await expect(workspacePage.zeppelinHeader).toBeVisible();
      // Header must contain navigable content, not just be an empty shell
      await expect(workspacePage.zeppelinHeader).toContainText('Zeppelin');
    });

    test('When workspace loads Then should have router outlet attached', async () => {
      await workspaceUtil.verifyRouterOutletActivation();
    });

    test('When workspace activates Then should render content inside router outlet', async () => {
      await workspaceUtil.waitForComponentActivation();
    });
  });
});
