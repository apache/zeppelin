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
import { LoginPage } from '../../models/login-page';
import { LoginTestUtil, TestCredentials } from '../../models/login-page.util';
import { addPageAnnotationBeforeEach, PAGES } from '../../utils';

test.describe('Login Page', () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  addPageAnnotationBeforeEach(PAGES.PAGES.LOGIN);
  let loginPage: LoginPage;
  let testCredentials: Record<string, TestCredentials>;

  test.beforeAll(async ({ request }) => {
    const isShiroEnabled = await LoginTestUtil.isShiroEnabled();
    if (!isShiroEnabled) {
      test.skip(true, 'Skipping all login tests - shiro.ini not found');
    }

    const ticketResponse = await request.get('/api/security/ticket', { failOnStatusCode: false });
    if (ticketResponse.ok()) {
      const ticket = await ticketResponse.json();
      if (ticket?.body?.principal === 'anonymous') {
        test.skip(true, 'Skipping all login tests - Zeppelin server is running in anonymous mode');
      }
    }

    testCredentials = await LoginTestUtil.getTestCredentials();
  });

  test.beforeEach(async ({ page }) => {
    loginPage = new LoginPage(page);
    await loginPage.navigate();
  });

  test('should display login form with required elements', async () => {
    await expect(loginPage.formContainer).toBeVisible();
    await expect(loginPage.userNameInput).toBeVisible();
    await expect(loginPage.passwordInput).toBeVisible();
    await expect(loginPage.loginButton).toBeVisible();
    await expect(loginPage.welcomeTitle).toBeVisible();
  });

  test('should have proper input field attributes', async () => {
    await expect(loginPage.userNameInput).toHaveAttribute('placeholder', 'User Name');
    await expect(loginPage.passwordInput).toHaveAttribute('placeholder', 'Password');
    await expect(loginPage.passwordInput).toHaveAttribute('type', 'password');
    await expect(loginPage.userNameInput).toHaveAttribute('name', 'userName');
    await expect(loginPage.passwordInput).toHaveAttribute('name', 'password');
  });

  test('should allow text input in form fields', async () => {
    const testUsername = 'testuser';
    const testPassword = 'testpass';

    await loginPage.userNameInput.fill(testUsername);
    await loginPage.passwordInput.fill(testPassword);

    await expect(loginPage.userNameInput).toHaveValue(testUsername);
    await expect(loginPage.passwordInput).toHaveValue(testPassword);
  });

  test('should display error message for invalid credentials', async () => {
    const invalidCreds = testCredentials.INVALID_USER;

    await loginPage.login(invalidCreds.username, invalidCreds.password);
    await loginPage.waitForErrorMessage();

    const errorText = await loginPage.getErrorMessageText();
    expect(errorText).toContain("The username and password that you entered don't match");
  });

  test('should login successfully with valid credentials', async ({ page }) => {
    const validUserKey = Object.keys(testCredentials).find(
      key => key !== 'INVALID_USER' && key !== 'EMPTY_CREDENTIALS'
    );
    const validCreds = testCredentials[validUserKey || 'user1'];

    // Attempt to login with valid credentials
    await loginPage.login(validCreds.username, validCreds.password);

    // Wait for navigation and verify the new URL
    await page.waitForURL('/#/');
    await expect(page).toHaveURL('/#/');

    // Verify the login form is no longer visible
    await expect(loginPage.formContainer).not.toBeVisible();
  });

  for (const entry of ['login', 'protected route']) {
    test(`should preserve returnUrl through a cold ${entry} entry and successful authentication`, async ({ page }) => {
      const destination = '/credential?probe=returnurl';
      const credentials = Object.values(testCredentials).find(account => account.roles?.includes('admin'));
      if (!credentials) {
        throw new Error('The returnUrl regression requires an admin account for the credential destination');
      }
      const entryUrl = entry === 'login' ? `/#/login?returnUrl=${encodeURIComponent(destination)}` : `/#${destination}`;

      // Force a new document, rather than updating query params on an already mounted login component.
      await page.goto('about:blank');
      await page.goto(entryUrl);
      await expect(loginPage.userNameInput).toBeVisible();
      await expect(page).toHaveURL(url => {
        const [path, query] = url.hash.split('?');
        return path === '#/login' && new URLSearchParams(query).get('returnUrl') === destination;
      });

      const responsePromise = page.waitForResponse(
        response => response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/login'
      );
      await loginPage.login(credentials.username, credentials.password);
      const response = await responsePromise;
      expect(response.status()).toBe(200);
      expect((await response.json()).body.principal).toBe(credentials.username);
      await expect(page).toHaveURL(url => url.hash === `#${destination}`);
      await expect(
        page.getByText('Manage your credentials. You can add new credential information.', { exact: true })
      ).toBeVisible();
      await expect(loginPage.userNameInput).toBeHidden();

      const cookies = await page.context().cookies(page.url());
      expect(cookies.some(cookie => cookie.name === 'JSESSIONID' && cookie.value !== '' && cookie.httpOnly)).toBe(true);
      const ticket = await page.context().request.get('/api/security/ticket', { maxRedirects: 0 });
      expect(ticket.status()).toBe(200);
      expect((await ticket.json()).body.principal).toBe(credentials.username);
    });
  }

  test('should maintain form state after failed login', async () => {
    const invalidCreds = testCredentials.INVALID_USER;
    await loginPage.login(invalidCreds.username, invalidCreds.password);
    await loginPage.waitForErrorMessage();

    await expect(loginPage.userNameInput).toHaveValue(invalidCreds.username);
    await expect(loginPage.passwordInput).toHaveValue(invalidCreds.password);
  });

  test('should support keyboard navigation', async ({ page }) => {
    await loginPage.userNameInput.focus();
    await expect(loginPage.userNameInput).toBeFocused();

    await page.keyboard.press('Tab');
    await expect(loginPage.passwordInput).toBeFocused();

    await page.keyboard.press('Tab');
    await expect(loginPage.loginButton).toBeFocused();
  });

  test('should handle form submission with Enter key', async ({ page }) => {
    const testCreds = testCredentials.INVALID_USER;
    await loginPage.login(testCreds.username, testCreds.password);

    await loginPage.passwordInput.focus();
    await page.keyboard.press('Enter');

    await loginPage.waitForErrorMessage();
  });
});
