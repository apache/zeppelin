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
import { HomePage } from '../../models/home-page';
import { addPageAnnotationBeforeEach, performLoginIfRequired, waitForZeppelinReady, PAGES } from '../../utils';

addPageAnnotationBeforeEach(PAGES.WORKSPACE.HOME);

test.describe('Home Page Enhanced Functionality', () => {
  let homePage: HomePage;

  test.beforeEach(async ({ page }) => {
    homePage = new HomePage(page);
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
  });

  test.describe('Given documentation links are displayed', () => {
    test('When documentation link is checked Then should have correct version in URL', async () => {
      const href = await homePage.getDocumentationLinkHref();
      expect(href).toContain('zeppelin.apache.org/docs');
      expect(href).toMatch(/\/docs\/\d+\.\d+\.\d+(-SNAPSHOT)?\//);
    });
  });
});
