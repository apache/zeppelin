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

import { expect, Page, Request, test } from '@playwright/test';
import { LoginPage } from '../../models/login-page';
import { LoginTestUtil, TestCredentials } from '../../models/login-page.util';
import { addPageAnnotationBeforeEach, PAGES } from '../../utils';

// Login requests contain credentials and tickets, including in retry traces.
test.use({
  storageState: { cookies: [], origins: [] },
  trace: 'off',
  video: 'off',
  screenshot: 'off',
  serviceWorkers: 'block'
});

const destination = '/credential?probe=returnurl';
const loginUrl = `/#/login?returnUrl=${encodeURIComponent(destination)}`;
const isLogin = (request: Request) => request.method() === 'POST' && new URL(request.url()).pathname === '/api/login';

const expectUnauthenticated = async (page: Page) => {
  const response = await page.context().request.get('/api/security/ticket', { maxRedirects: 0 });
  // Do not follow Shiro's redirect into the login endpoint.
  expect(response.status()).toBe(302);
  expect(new URL(response.headers().location, response.url()).pathname).toBe('/api/login');
};

const expectAuthenticated = async (page: Page, username: string) => {
  const cookies = await page.context().cookies(page.url());
  expect(cookies.some(cookie => cookie.name === 'JSESSIONID' && cookie.value !== '' && cookie.httpOnly)).toBe(true);
  // A session cookie alone can also belong to an unauthenticated session.
  const response = await page.context().request.get('/api/security/ticket', { maxRedirects: 0 });
  expect(response.status()).toBe(200);
  expect((await response.json()).body.principal).toBe(username);
};

const submitSuccessfully = async (page: Page, loginPage: LoginPage, credentials: TestCredentials) => {
  const responsePromise = page.waitForResponse(response => isLogin(response.request()));
  await loginPage.login(credentials.username, credentials.password);
  const response = await responsePromise;
  expect(response.status()).toBe(200);
  expect((await response.json()).body.principal).toBe(credentials.username);
};

const expectHome = async (page: Page, loginPage: LoginPage, username: string) => {
  await expect(page).toHaveURL(url => url.hash === '#/');
  // The login page also has a welcome heading; scope this to the destination.
  await expect(page.locator('zeppelin-home').getByRole('heading', { name: 'Welcome to Zeppelin!' })).toBeVisible();
  await expect(loginPage.formContainer).toBeHidden();
  await expectAuthenticated(page, username);
};

const expectUsableFailure = async (page: Page, loginPage: LoginPage) => {
  await expect(loginPage.errorMessage).toBeVisible();
  await expect(page).toHaveURL(url => url.hash === '#/login');
  await expect(loginPage.formContainer).toBeVisible();
  // nzLoading suppresses clicks without setting the native disabled attribute.
  await expect(loginPage.loginButton).not.toHaveClass(/ant-btn-loading/);
  await expect(loginPage.loginButton).toBeEnabled();
  await expect(loginPage.userNameInput).toBeEditable();
  await expect(loginPage.passwordInput).toBeEditable();
  await expectUnauthenticated(page);
};

test.describe('Login route handoff', () => {
  // Keep submissions for this account ordered even when the runner is fully parallel.
  test.describe.configure({ mode: 'default' });
  addPageAnnotationBeforeEach(PAGES.PAGES.LOGIN);
  let credentials: TestCredentials;
  let loginPage: LoginPage;

  test.beforeAll(async ({ request }) => {
    test.skip(!(await LoginTestUtil.isShiroEnabled()), 'Login scenarios require Shiro authentication');
    const response = await request.get('/api/security/ticket', { maxRedirects: 0 });
    if (response.ok()) {
      const ticket = await response.json();
      test.skip(ticket.body.principal === 'anonymous', 'Login scenarios do not run against an anonymous server');
    }
    const account = Object.values(await LoginTestUtil.getTestCredentials()).find(value =>
      value.roles?.includes('admin')
    );
    if (!account) {
      throw new Error('Login handoff requires an admin account for the credential destination');
    }
    credentials = account;
  });

  test.beforeEach(async ({ page }) => {
    await expectUnauthenticated(page);
    loginPage = new LoginPage(page);
  });

  test('submits valid credentials and establishes a browser session at home', async ({ page }) => {
    await loginPage.navigate();
    await expect(loginPage.formContainer).toBeVisible();
    await submitSuccessfully(page, loginPage, credentials);
    await expectHome(page, loginPage, credentials.username);
  });

  test('supports keyboard navigation through the login form', async ({ page }) => {
    await loginPage.navigate();
    await loginPage.userNameInput.focus();
    await expect(loginPage.userNameInput).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(loginPage.passwordInput).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(loginPage.loginButton).toBeFocused();
  });

  for (const entry of ['login', 'protected route']) {
    test(`preserves returnUrl through a cold ${entry} entry and successful authentication`, async ({ page }) => {
      const entryUrl = entry === 'login' ? loginUrl : `/#${destination}`;
      // The fresh page fixture has not mounted the login component before this entry.
      await page.goto(entryUrl);
      await expect(loginPage.formContainer).toBeVisible();
      await expect(page).toHaveURL(url => {
        const [path, query] = url.hash.split('?');
        return path === '#/login' && new URLSearchParams(query).get('returnUrl') === destination;
      });

      await submitSuccessfully(page, loginPage, credentials);
      await expect(page).toHaveURL(url => url.hash === `#${destination}`);
      await expect(
        page.getByText('Manage your credentials. You can add new credential information.', { exact: true })
      ).toBeVisible();
      await expect(loginPage.formContainer).toBeHidden();
      await expectAuthenticated(page, credentials.username);
    });
  }

  test('redirects an authenticated login visit to home without submitting credentials again', async ({ page }) => {
    await loginPage.navigate();
    await submitSuccessfully(page, loginPage, credentials);
    await expectHome(page, loginPage, credentials.username);

    const submissions: Request[] = [];
    const observe = (request: Request) => {
      if (isLogin(request)) {
        submissions.push(request);
      }
    };
    page.on('request', observe);
    try {
      // LoginGuard sends authenticated visitors home even when returnUrl is present.
      await page.goto(loginUrl);
      await expectHome(page, loginPage, credentials.username);
      expect(submissions).toHaveLength(0);
    } finally {
      page.off('request', observe);
    }
  });

  test('rejects invalid credentials without leaving the form loading and accepts a corrected retry', async ({
    page
  }) => {
    await loginPage.navigate();
    const responsePromise = page.waitForResponse(response => isLogin(response.request()));
    await loginPage.login(credentials.username, `${credentials.password}-incorrect`);
    expect((await responsePromise).status()).toBe(403);
    await expectUsableFailure(page, loginPage);
    await submitSuccessfully(page, loginPage, credentials);
    await expectHome(page, loginPage, credentials.username);
  });

  for (const failure of ['network failure', 'HTTP 503']) {
    test(`recovers from ticket submission ${failure} and authenticates on retry`, async ({ page }) => {
      await loginPage.navigate();
      await expect(loginPage.formContainer).toBeVisible();
      let intercepted = 0;
      await page.route('**/api/login', async route => {
        if (!isLogin(route.request())) {
          await route.fallback();
          return;
        }
        intercepted++;
        if (failure === 'network failure') {
          await route.abort('failed');
        } else {
          await route.fulfill({
            status: 503,
            contentType: 'application/json',
            body: '{"status":"SERVICE_UNAVAILABLE"}'
          });
        }
      });
      try {
        const resultPromise =
          failure === 'network failure'
            ? page.waitForEvent('requestfailed', { predicate: isLogin }).then(() => 'network failure')
            : page
                .waitForResponse(response => isLogin(response.request()))
                .then(response => `HTTP ${response.status()}`);
        await loginPage.login(credentials.username, credentials.password);
        expect(await resultPromise).toBe(failure);
        expect(intercepted).toBe(1);
        await expectUsableFailure(page, loginPage);
      } finally {
        await page.unroute('**/api/login');
      }
      // Only the failed attempt is intercepted; the retry must authenticate with Shiro.
      await submitSuccessfully(page, loginPage, credentials);
      await expectHome(page, loginPage, credentials.username);
    });
  }
});
