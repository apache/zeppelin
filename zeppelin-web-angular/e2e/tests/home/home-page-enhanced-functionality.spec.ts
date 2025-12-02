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
import { HomePageUtil } from '../../models/home-page.util';
import { addPageAnnotationBeforeEach, performLoginIfRequired, waitForZeppelinReady, PAGES } from '../../utils';

addPageAnnotationBeforeEach(PAGES.WORKSPACE.HOME);

test.describe('Home Page Enhanced Functionality', () => {
  let homeUtil: HomePageUtil;

  test.beforeEach(async ({ page }) => {
    homeUtil = new HomePageUtil(page);
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
  });

  test.describe('Given documentation links are displayed', () => {
    test('When documentation link is checked Then should have correct version in URL', async () => {
      await homeUtil.verifyDocumentationVersionLink();
    });

    test('When external links are checked Then should all open in new tab', async () => {
      await homeUtil.verifyAllExternalLinksTargetBlank();
    });
  });

  test.describe('Given welcome section display', () => {
    test('When page loads Then should show welcome content with proper text', async () => {
      await homeUtil.verifyWelcomeSection();
    });

    test('When welcome section is displayed Then should contain interactive elements', async () => {
      await homeUtil.verifyNotebookSection();
    });
  });

  test.describe('Given community section content', () => {
    test('When community section loads Then should display help and community headings', async () => {
      await homeUtil.verifyHelpSection();
      await homeUtil.verifyCommunitySection();
    });

    test('When external links are displayed Then should show correct targets', async () => {
      const linkTargets = await homeUtil.testExternalLinkTargets();

      expect(linkTargets.documentationHref).toContain('zeppelin.apache.org/docs');
      expect(linkTargets.mailingListHref).toContain('community.html');
      expect(linkTargets.issuesTrackingHref).toContain('issues.apache.org');
      expect(linkTargets.githubHref).toContain('github.com/apache/zeppelin');
    });
  });
});
