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

    test('When accessing login page directly, Then should display full home page with all sections and links', async ({
      page
    }) => {
      await page.goto('/#/login');
      await waitForZeppelinReady(page);
      await page.waitForURL(url => !url.toString().includes('#/login'));

      // Sections
      await expect(homePage.welcomeTitle).toBeVisible();
      await expect(homePage.notebookSection).toBeVisible();
      await expect(homePage.helpSection).toBeVisible();
      await expect(homePage.communitySection).toBeVisible();
      // Notebook actions
      await expect(homePage.nodeList.createNewNoteLink).toBeVisible();
      await expect(homePage.nodeList.importNoteLink).toBeVisible();
      await expect(homePage.nodeList.filterInput).toBeVisible();
      // External links
      await expect(homePage.externalLinks.documentation).toBeVisible();
      await expect(homePage.externalLinks.mailingList).toBeVisible();
      await expect(homePage.externalLinks.issuesTracking).toBeVisible();
      await expect(homePage.externalLinks.github).toBeVisible();
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

    test('When accessing protected route directly, Then should load home content for anonymous user', async ({
      page
    }) => {
      // Notebook-repos is a management route; anonymous users should either access it or be redirected home
      await page.goto('/#/notebook-repos');
      await waitForZeppelinReady(page);

      // Then: Either the notebook-repos page loads (anonymous mode allows it) OR
      // the user is redirected back to home — both are valid; the app must not crash or show an empty shell
      const currentPath = getCurrentPath(page);
      const isAnonymous = await homePage.isAnonymousUser();

      expect(isAnonymous).toBe(true);
      // The app root must still be rendering — not a blank white page
      await expect(basePage.zeppelinWorkspace).toBeVisible();
      // If redirected, must land on home (not an error page)
      if (!currentPath.includes('#/notebook-repos')) {
        // JUSTIFIED: both states are valid — notebook-repos accessible OR redirect to home; only assert welcomeTitle on redirect path
        await expect(basePage.welcomeTitle).toBeVisible();
      }
    });

    test('When accessing configuration route directly, Then should handle navigation for anonymous user', async ({
      page
    }) => {
      // Configuration is a management route; anonymous users should either access it or be redirected home
      await page.goto('/#/configuration');
      await waitForZeppelinReady(page);

      // Then: Either the configuration page loads (anonymous mode allows it) OR
      // the user is redirected back to home — both are valid; the app must not crash
      const currentPath = getCurrentPath(page);
      const isAnonymous = await homePage.isAnonymousUser();

      expect(isAnonymous).toBe(true);
      await expect(basePage.zeppelinWorkspace).toBeVisible();
      if (!currentPath.includes('#/configuration')) {
        // JUSTIFIED: both states are valid — in anonymous mode (no shiro.ini) all routes including
        // /configuration are accessible; shiro.ini url rules control whether this route is restricted
        await expect(basePage.welcomeTitle).toBeVisible({ timeout: 15000 });
      }
    });

    test('When multiple page loads occur on login URL, Then should consistently redirect to home', async ({ page }) => {
      for (let i = 0; i < 3; i++) {
        await page.goto('/#/login');
        await waitForZeppelinReady(page);
        await waitForUrlNotContaining(page, '#/login');

        await expect(basePage.welcomeTitle).toBeVisible();
        await expect(page.getByText('anonymous', { exact: true })).toBeVisible();

        const path = getCurrentPath(page);
        expect(path).toContain('#/');
        expect(path).not.toContain('#/login');
      }
    });
  });
});
