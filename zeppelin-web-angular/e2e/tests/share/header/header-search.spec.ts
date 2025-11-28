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

import { test, expect } from '@playwright/test';
import { HeaderPage } from '../../../models/header-page';
import { HeaderPageUtil } from '../../../models/header-page.util';
import { addPageAnnotationBeforeEach, PAGES, performLoginIfRequired, waitForZeppelinReady } from '../../../utils';

test.describe('Header Search Functionality', () => {
  let headerPage: HeaderPage;
  let headerUtil: HeaderPageUtil;

  addPageAnnotationBeforeEach(PAGES.SHARE.HEADER);

  test.beforeEach(async ({ page }) => {
    headerPage = new HeaderPage(page);
    headerUtil = new HeaderPageUtil(page, headerPage);

    await page.goto('/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
  });

  test('Given user is on home page, When entering search query and pressing Enter, Then user should navigate to search results page', async () => {
    const searchQuery = 'test';
    await headerUtil.verifySearchNavigation(searchQuery);
  });

  test('Given user is on home page, When viewing search input, Then search input should be visible and accessible', async () => {
    await expect(headerPage.searchInput).toBeVisible();
    await expect(headerPage.searchInput).toBeEditable();
  });
});
