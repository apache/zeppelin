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

import { Locator, Page } from '@playwright/test';
import { BasePage } from './base-page';

export class LoginPage extends BasePage {
  readonly userNameInput: Locator;
  readonly passwordInput: Locator;
  readonly loginButton: Locator;
  readonly welcomeTitle: Locator;
  readonly formContainer: Locator;

  constructor(page: Page) {
    super(page);
    this.userNameInput = page.getByRole('textbox', { name: 'User Name' });
    this.passwordInput = page.getByRole('textbox', { name: 'Password' });
    this.loginButton = page.getByRole('button', { name: 'Login' });
    this.welcomeTitle = page.getByRole('heading', { name: 'Welcome to Zeppelin!' });
    this.formContainer = page.locator('form[nz-form]');
  }

  async navigate(): Promise<void> {
    await this.page.goto('/#/login');
    await this.waitForPageLoad();
  }

  async login(username: string, password: string): Promise<void> {
    await this.userNameInput.fill(username);
    await this.passwordInput.fill(password);
    await this.loginButton.click();
  }

  async waitForErrorMessage(): Promise<void> {
    await this.page.waitForSelector("text=The username and password that you entered don't match.", { timeout: 5000 });
  }

  async getErrorMessageText(): Promise<string> {
    return (
      (await this.page.locator("text=The username and password that you entered don't match.").first().textContent()) ||
      ''
    );
  }
}
