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

import { test } from '@playwright/test';
import { WorkspaceTestUtil } from '../../models/workspace-page.util';
import { addPageAnnotationBeforeEach, PAGES } from '../../utils';

addPageAnnotationBeforeEach(PAGES.WORKSPACE.MAIN);

test.describe('Workspace Main Component', () => {
  let workspaceUtil: WorkspaceTestUtil;

  test.beforeEach(async ({ page }) => {
    workspaceUtil = new WorkspaceTestUtil(page);
  });

  test.describe('Given user accesses workspace container', () => {
    test('When workspace loads Then should display main container structure', async () => {
      await workspaceUtil.navigateAndWaitForLoad();

      await workspaceUtil.verifyWorkspaceLayout();
      await workspaceUtil.verifyWorkspaceContainer();
    });

    test('When workspace loads Then should display header component', async () => {
      await workspaceUtil.navigateAndWaitForLoad();

      await workspaceUtil.verifyHeaderVisibility(true);
    });

    test('When workspace loads Then should activate router outlet', async () => {
      await workspaceUtil.navigateAndWaitForLoad();

      await workspaceUtil.verifyRouterOutletActivation();
    });

    test('When component activates Then should trigger onActivate event', async () => {
      await workspaceUtil.navigateAndWaitForLoad();

      await workspaceUtil.waitForComponentActivation();
    });
  });

  test.describe('Given workspace header visibility', () => {
    test('When not in publish mode Then should show header', async () => {
      await workspaceUtil.navigateAndWaitForLoad();

      await workspaceUtil.verifyHeaderVisibility(true);
    });
  });

  test.describe('Given router outlet functionality', () => {
    test('When navigating to workspace Then should load child components', async () => {
      await workspaceUtil.navigateAndWaitForLoad();

      await workspaceUtil.verifyRouterOutletActivation();
      await workspaceUtil.waitForComponentActivation();
    });
  });
});
