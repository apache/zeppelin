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

    test('When external links are checked Then should all open in new tab', async () => {
      const links = [
        homePage.externalLinks.documentation,
        homePage.externalLinks.mailingList,
        homePage.externalLinks.issuesTracking,
        homePage.externalLinks.github
      ];

      for (const link of links) {
        const target = await link.getAttribute('target');
        expect(target).toBe('_blank');
      }
    });
  });

  test.describe('Given welcome section display', () => {
    test('When page loads Then should show welcome content with proper text', async () => {
      await expect(homePage.welcomeSection).toBeVisible();
      await expect(homePage.welcomeTitle).toBeVisible();
      const headingText = await homePage.getWelcomeHeadingText();
      expect(headingText.trim()).toBe('Welcome to Zeppelin!');
      await expect(homePage.welcomeDescription).toBeVisible();
      const welcomeText = await homePage.welcomeDescription.textContent();
      expect(welcomeText).toContain('web-based notebook');
      expect(welcomeText).toContain('interactive data analytics');
    });

    test('When welcome section is displayed Then should contain interactive elements', async ({ page }) => {
      await expect(homePage.notebookSection).toBeVisible();
      await expect(homePage.notebookHeading).toBeVisible();
      await expect(homePage.refreshNoteButton).toBeVisible();
      await page.waitForSelector('zeppelin-node-list', { timeout: 10000 });
      await expect(homePage.zeppelinNodeList).toBeVisible();
    });
  });

  test.describe('Given community section content', () => {
    test('When community section loads Then should display help and community headings', async () => {
      await expect(homePage.helpSection).toBeVisible();
      await expect(homePage.helpHeading).toBeVisible();
      await expect(homePage.communitySection).toBeVisible();
      await expect(homePage.communityHeading).toBeVisible();
    });

    test('When external links are displayed Then should show correct targets', async () => {
      const docHref = await homePage.externalLinks.documentation.getAttribute('href');
      const mailHref = await homePage.externalLinks.mailingList.getAttribute('href');
      const issuesHref = await homePage.externalLinks.issuesTracking.getAttribute('href');
      const githubHref = await homePage.externalLinks.github.getAttribute('href');

      expect(docHref).toContain('zeppelin.apache.org/docs');
      expect(mailHref).toContain('community.html');
      expect(issuesHref).toContain('issues.apache.org');
      expect(githubHref).toContain('github.com/apache/zeppelin');
    });
  });
});
