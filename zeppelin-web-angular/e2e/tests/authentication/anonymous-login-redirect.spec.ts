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
import { BasePage } from '../../models/base-page';
import { HomePage } from '../../models/home-page';
import { LoginTestUtil } from '../../models/login-page.util';
import {
  addPageAnnotationBeforeEach,
  getBasicPageMetadata,
  getCurrentPath,
  waitForUrlNotContaining,
  waitForZeppelinReady,
  PAGES
} from '../../utils';

test.describe('Anonymous User Login Redirect', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.HOME);

  let homePage: HomePage;
  let basePage: BasePage;

  test.beforeAll(async () => {
    const isShiroEnabled = await LoginTestUtil.isShiroEnabled();
    if (isShiroEnabled) {
      test.skip(true, 'Skipping anonymous login redirect tests - authentication is enabled (shiro.ini found)');
    }
  });

  test.beforeEach(async ({ page }) => {
    homePage = new HomePage(page);
    basePage = new BasePage(page);
  });

  test.describe('Given an anonymous user is already logged in', () => {
    test.beforeEach(async ({ page }) => {
      await page.goto('/#/');
      await waitForZeppelinReady(page);
    });

    test('When accessing login page directly, Then should redirect to home with proper URL change', async ({
      page
    }) => {
      await homePage.navigateToLogin();

      const currentPath = getCurrentPath(page);
      const isLoginUrlMaintained = currentPath.includes('#/login');
      const isHomeContentDisplayed = await homePage.isHomeContentDisplayed();
      const isAnonymousUser = await homePage.isAnonymousUser();

      expect(isLoginUrlMaintained).toBe(false);
      expect(isHomeContentDisplayed).toBe(true);
      expect(isAnonymousUser).toBe(true);
      expect(currentPath).toContain('#/');
      expect(currentPath).not.toContain('#/login');
    });

    test('When accessing login page directly, Then should display all home page elements correctly', async ({
      page
    }) => {
      await page.goto('/#/login');
      await waitForZeppelinReady(page);
      await page.waitForURL(url => !url.toString().includes('#/login'));

      await expect(homePage.welcomeTitle).toBeVisible();
      await expect(homePage.notebookSection).toBeVisible();
      await expect(homePage.helpSection).toBeVisible();
      await expect(homePage.communitySection).toBeVisible();
    });

    test('When clicking Zeppelin logo after redirect, Then should maintain home URL and content', async ({ page }) => {
      await page.goto('/#/login');
      await waitForZeppelinReady(page);
      await page.waitForURL(url => !url.toString().includes('#/login'));

      const pathBeforeClick = getCurrentPath(page);
      await homePage.clickZeppelinLogo();
      await basePage.waitForPageLoad();
      const pathAfterClick = getCurrentPath(page);
      const homeContentMaintained = await homePage.isHomeContentDisplayed();

      expect(pathBeforeClick).toContain('#/');
      expect(pathBeforeClick).not.toContain('#/login');
      expect(pathAfterClick).toContain('#/');
      expect(homeContentMaintained).toBe(true);
    });

    test('When accessing login page, Then should redirect and maintain anonymous user state', async ({ page }) => {
      await page.goto('/#/login');
      await waitForZeppelinReady(page);
      await page.waitForURL(url => !url.toString().includes('#/login'));

      const basicMetadata = await getBasicPageMetadata(page);
      const isAnonymous = await homePage.isAnonymousUser();

      expect(basicMetadata.title).toContain('Zeppelin');
      expect(basicMetadata.path).toContain('#/');
      expect(basicMetadata.path).not.toContain('#/login');
      expect(isAnonymous).toBe(true);
    });

    test('When accessing login page, Then should display welcome heading and main sections', async ({ page }) => {
      await page.goto('/#/login');
      await waitForZeppelinReady(page);
      await page.waitForURL(url => !url.toString().includes('#/login'));

      await expect(basePage.welcomeTitle).toBeVisible();
      await expect(page.locator('text=Notebook').first()).toBeVisible();
      await expect(page.locator('text=Help').first()).toBeVisible();
      await expect(page.locator('text=Community').first()).toBeVisible();
    });

    test('When accessing login page, Then should display notebook functionalities', async ({ page }) => {
      await page.goto('/#/login');
      await waitForZeppelinReady(page);
      await page.waitForURL(url => !url.toString().includes('#/login'));

      await expect(page.locator('text=Create new Note')).toBeVisible();
      await expect(page.locator('text=Import Note')).toBeVisible();

      const filterInput = page.locator('input[placeholder*="Filter"]');
      if ((await filterInput.count()) > 0) {
        await expect(filterInput).toBeVisible();
      }
    });

    test('When accessing login page, Then should display external links in help and community sections', async ({
      page
    }) => {
      await page.goto('/#/login');
      await waitForZeppelinReady(page);
      await page.waitForURL(url => !url.toString().includes('#/login'));

      const docLinks = page.locator('a[href*="zeppelin.apache.org/docs"]');
      const communityLinks = page.locator('a[href*="community.html"]');
      const issuesLinks = page.locator('a[href*="issues.apache.org"]');
      const githubLinks = page.locator('a[href*="github.com/apache/zeppelin"]');

      if ((await docLinks.count()) > 0) {
        await expect(docLinks).toBeVisible();
      }
      if ((await communityLinks.count()) > 0) {
        await expect(communityLinks).toBeVisible();
      }
      if ((await issuesLinks.count()) > 0) {
        await expect(issuesLinks).toBeVisible();
      }
      if ((await githubLinks.count()) > 0) {
        await expect(githubLinks).toBeVisible();
      }
    });

    test('When navigating between home and login URLs, Then should maintain consistent user experience', async ({
      page
    }) => {
      await page.goto('/#/');
      await waitForZeppelinReady(page);

      const homeMetadata = await getBasicPageMetadata(page);
      const isHomeAnonymous = await homePage.isAnonymousUser();
      expect(homeMetadata.path).toContain('#/');
      expect(isHomeAnonymous).toBe(true);

      await page.goto('/#/login');
      await waitForZeppelinReady(page);
      await page.waitForURL(url => !url.toString().includes('#/login'));

      const loginMetadata = await getBasicPageMetadata(page);
      const isLoginAnonymous = await homePage.isAnonymousUser();
      expect(loginMetadata.path).toContain('#/');
      expect(loginMetadata.path).not.toContain('#/login');
      expect(isLoginAnonymous).toBe(true);

      await homePage.navigateToLogin();
      const isHomeContentDisplayed = await homePage.isHomeContentDisplayed();
      expect(isHomeContentDisplayed).toBe(true);
    });

    test('When multiple page loads occur on login URL, Then should consistently redirect to home', async ({ page }) => {
      for (let i = 0; i < 3; i++) {
        await page.goto('/#/login');
        await waitForZeppelinReady(page);
        await waitForUrlNotContaining(page, '#/login');

        await expect(basePage.welcomeTitle).toBeVisible();
        await expect(page.locator('text=anonymous')).toBeVisible();

        const path = getCurrentPath(page);
        expect(path).toContain('#/');
        expect(path).not.toContain('#/login');
      }
    });
  });
});
