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
import { BasePage } from 'e2e/models/base-page';
import { addPageAnnotationBeforeEach, PAGES, performLoginIfRequired, waitForZeppelinReady } from '../../utils';

addPageAnnotationBeforeEach(PAGES.WORKSPACE.MAIN);

test.describe('Workspace Main Component', () => {
  let basePage: BasePage;

  test.beforeEach(async ({ page }) => {
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);

    basePage = new BasePage(page);
  });

  test.describe('Given user accesses workspace container', () => {
    test('When workspace loads Then should display main container structure', async () => {
      await expect(basePage.zeppelinWorkspace).toBeVisible();
      // Verify workspace contains the header — not just that the elements exist in isolation
      await expect(basePage.zeppelinWorkspace.locator('zeppelin-header')).toBeVisible();
    });

    test('When workspace loads Then should display header component', async () => {
      await expect(basePage.zeppelinHeader).toBeVisible();
      // Header must contain navigable content, not just be an empty shell
      await expect(basePage.zeppelinHeader).toContainText('Zeppelin');
    });

    test('When workspace loads Then should have router outlet attached with home component', async ({ page }) => {
      // Router outlet must have an activated child, not just exist as an empty outlet
      await expect(page.locator('zeppelin-workspace router-outlet + *')).toHaveCount(1);
      // Activated route must have rendered the home component
      await expect(basePage.zeppelinWorkspace.locator('zeppelin-home')).toBeVisible();
    });
  });
});
