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
import { HomePageUtil } from '../../models/home-page.util';
import { addPageAnnotationBeforeEach, performLoginIfRequired, waitForZeppelinReady, PAGES } from '../../utils';

test.describe('Home Page - External Links', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.HOME);

  test.beforeEach(async ({ page }) => {
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
  });

  test.describe('Documentation Link', () => {
    test('should have correct documentation link with dynamic version', async ({ page }) => {
      const homePage = new HomePage(page);
      const homePageUtil = new HomePageUtil(page);

      await test.step('Given I am on the home page', async () => {
        await homePage.navigateToHome();
      });

      await test.step('When I examine the documentation link', async () => {
        await expect(homePage.externalLinks.documentation).toBeVisible();
      });

      await test.step('Then it should have the correct href pattern', async () => {
        const linkTargets = await homePageUtil.testExternalLinkTargets();
        expect(linkTargets.documentationHref).toContain('zeppelin.apache.org/docs');
        expect(linkTargets.documentationHref).toContain('index.html');
      });

      await test.step('And it should open in a new tab', async () => {
        const target = await homePage.externalLinks.documentation.getAttribute('target');
        expect(target).toBe('_blank');
      });
    });
  });

  test.describe('Community Links', () => {
    test('should have correct mailing list link', async ({ page }) => {
      const homePage = new HomePage(page);
      const homePageUtil = new HomePageUtil(page);

      await test.step('Given I am on the home page', async () => {
        await homePage.navigateToHome();
      });

      await test.step('When I examine the mailing list link', async () => {
        await expect(homePage.externalLinks.mailingList).toBeVisible();
      });

      await test.step('Then it should have the correct href', async () => {
        const linkTargets = await homePageUtil.testExternalLinkTargets();
        expect(linkTargets.mailingListHref).toBe('http://zeppelin.apache.org/community.html');
      });

      await test.step('And it should open in a new tab', async () => {
        const target = await homePage.externalLinks.mailingList.getAttribute('target');
        expect(target).toBe('_blank');
      });

      await test.step('And it should have the mail icon', async () => {
        const mailIcon = homePage.externalLinks.mailingList.locator('i[nz-icon][nzType="mail"]');
        await expect(mailIcon).toBeVisible();
      });
    });

    test('should have correct issues tracking link', async ({ page }) => {
      const homePage = new HomePage(page);
      const homePageUtil = new HomePageUtil(page);

      await test.step('Given I am on the home page', async () => {
        await homePage.navigateToHome();
      });

      await test.step('When I examine the issues tracking link', async () => {
        await expect(homePage.externalLinks.issuesTracking).toBeVisible();
      });

      await test.step('Then it should have the correct href', async () => {
        const linkTargets = await homePageUtil.testExternalLinkTargets();
        expect(linkTargets.issuesTrackingHref).toBe(
          'https://issues.apache.org/jira/projects/ZEPPELIN/issues/filter=allopenissues'
        );
      });

      await test.step('And it should open in a new tab', async () => {
        const target = await homePage.externalLinks.issuesTracking.getAttribute('target');
        expect(target).toBe('_blank');
      });

      await test.step('And it should have the exception icon', async () => {
        const exceptionIcon = homePage.externalLinks.issuesTracking.locator('i[nz-icon][nzType="exception"]');
        await expect(exceptionIcon).toBeVisible();
      });
    });

    test('should have correct GitHub link', async ({ page }) => {
      const homePage = new HomePage(page);
      const homePageUtil = new HomePageUtil(page);

      await test.step('Given I am on the home page', async () => {
        await homePage.navigateToHome();
      });

      await test.step('When I examine the GitHub link', async () => {
        await expect(homePage.externalLinks.github).toBeVisible();
      });

      await test.step('Then it should have the correct href', async () => {
        const linkTargets = await homePageUtil.testExternalLinkTargets();
        expect(linkTargets.githubHref).toBe('https://github.com/apache/zeppelin');
      });

      await test.step('And it should open in a new tab', async () => {
        const target = await homePage.externalLinks.github.getAttribute('target');
        expect(target).toBe('_blank');
      });

      await test.step('And it should have the GitHub icon', async () => {
        const githubIcon = homePage.externalLinks.github.locator('i[nz-icon][nzType="github"]');
        await expect(githubIcon).toBeVisible();
      });
    });
  });

  test.describe('Link Verification', () => {
    test('should have all external links with proper attributes', async ({ page }) => {
      const homePage = new HomePage(page);

      await test.step('Given I am on the home page', async () => {
        await homePage.navigateToHome();
      });

      await test.step('When I examine all external links', async () => {
        await expect(homePage.externalLinks.documentation).toBeVisible();
        await expect(homePage.externalLinks.mailingList).toBeVisible();
        await expect(homePage.externalLinks.issuesTracking).toBeVisible();
        await expect(homePage.externalLinks.github).toBeVisible();
      });

      await test.step('Then all links should open in new tabs', async () => {
        const docTarget = await homePage.externalLinks.documentation.getAttribute('target');
        const mailTarget = await homePage.externalLinks.mailingList.getAttribute('target');
        const issuesTarget = await homePage.externalLinks.issuesTracking.getAttribute('target');
        const githubTarget = await homePage.externalLinks.github.getAttribute('target');

        expect(docTarget).toBe('_blank');
        expect(mailTarget).toBe('_blank');
        expect(issuesTarget).toBe('_blank');
        expect(githubTarget).toBe('_blank');
      });
    });
  });
});
