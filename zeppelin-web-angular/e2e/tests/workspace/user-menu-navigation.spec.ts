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
import { HeaderPage } from '../../models/header-page';
import { performLoginIfRequired, waitForZeppelinReady } from '../../utils';

/**
 * Regression guard for the header user-menu navigation.
 *
 * ng-zorro renders dropdown nz-menu-item content inside <span class="ant-menu-title-content">,
 * but the upstream rule that stretches the inner <a> across the whole item targets a
 * different class (.ant-dropdown-menu-title-content). The class mismatch left the <a>
 * covering only its text, so clicking the row padding closed the dropdown without
 * navigating. This was exposed by the Angular/ng-zorro 13 -> 17 upgrade and fixed by a
 * scoped full-row click rule (nzOverlayClassName="zeppelin-user-menu" + ::ng-deep ::after).
 *
 * Each test deliberately clicks the EMPTY PADDING of the row (not the link text) so a
 * future ng-zorro/Angular upgrade that reintroduces the dead zone fails here.
 */
const MENU_ITEMS = [
  { label: 'Interpreter', route: '/interpreter' },
  { label: 'Notebook Repos', route: '/notebook-repos' },
  { label: 'Credential', route: '/credential' },
  { label: 'Configuration', route: '/configuration' }
];

test.describe('Header user menu - full-row navigation', () => {
  let header: HeaderPage;

  test.beforeEach(async ({ page }) => {
    header = new HeaderPage(page);
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
  });

  for (const item of MENU_ITEMS) {
    test(`navigates to ${item.label} when the row padding (not the text) is clicked`, async ({ page }) => {
      await test.step('Given the user dropdown is open', async () => {
        await header.clickUserDropdown();
        await header.getUserMenuItemRow(item.label).waitFor({ state: 'visible', timeout: 10000 });
      });

      await test.step(`When I click the empty padding of the "${item.label}" row`, async () => {
        await header.clickUserMenuItemEdge(item.label);
      });

      await test.step(`Then the app navigates to ${item.route}`, async () => {
        await page.waitForURL(url => url.hash.includes(item.route), { timeout: 10000 });
        expect(page.url()).toContain(item.route);
      });
    });
  }
});
