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

export class AboutZeppelinModal extends BasePage {
  readonly modal: Locator;
  readonly modalTitle: Locator;
  readonly closeButton: Locator;
  readonly logo: Locator;
  readonly heading: Locator;
  readonly versionText: Locator;
  readonly getInvolvedLink: Locator;
  readonly licenseLink: Locator;

  constructor(page: Page) {
    super(page);
    this.modal = page.locator('[role="dialog"]').filter({ has: page.getByText('About Zeppelin') });
    this.modalTitle = page.locator('.ant-modal-title', { hasText: 'About Zeppelin' });
    this.closeButton = page.getByRole('button', { name: 'Close' });
    this.logo = page.locator('img[alt="Apache Zeppelin"]');
    this.heading = page.locator('h3', { hasText: 'Apache Zeppelin' });
    this.versionText = page.locator('.about-version');
    this.getInvolvedLink = page.getByRole('link', { name: 'Get involved!' });
    this.licenseLink = page.getByRole('link', { name: 'Licensed under the Apache License, Version 2.0' });
  }

  async isModalVisible(): Promise<boolean> {
    return this.modal.isVisible();
  }

  async close(): Promise<void> {
    await this.closeButton.click();
  }

  async getVersionText(): Promise<string> {
    return (await this.versionText.textContent()) || '';
  }

  async isLogoVisible(): Promise<boolean> {
    return this.logo.isVisible();
  }

  async clickGetInvolvedLink(): Promise<void> {
    await this.getInvolvedLink.click();
  }

  async clickLicenseLink(): Promise<void> {
    await this.licenseLink.click();
  }

  async getGetInvolvedHref(): Promise<string | null> {
    return this.getInvolvedLink.getAttribute('href');
  }

  async getLicenseHref(): Promise<string | null> {
    return this.licenseLink.getAttribute('href');
  }
}
