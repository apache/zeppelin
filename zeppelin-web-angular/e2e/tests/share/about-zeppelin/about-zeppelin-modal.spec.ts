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

import { test, expect } from '@playwright/test';
import { HeaderPage } from '../../../models/header-page';
import { AboutZeppelinModal } from '../../../models/about-zeppelin-modal';
import { addPageAnnotationBeforeEach, PAGES, performLoginIfRequired, waitForZeppelinReady } from '../../../utils';

test.describe('About Zeppelin Modal', () => {
  let headerPage: HeaderPage;
  let aboutModal: AboutZeppelinModal;

  addPageAnnotationBeforeEach(PAGES.SHARE.ABOUT_ZEPPELIN);

  test.beforeEach(async ({ page }) => {
    headerPage = new HeaderPage(page);
    aboutModal = new AboutZeppelinModal(page);

    await page.goto('/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);

    await headerPage.clickUserDropdown();
    await headerPage.clickAboutZeppelin();
  });

  test('Given user clicks About Zeppelin menu item, When modal opens, Then modal should display all required elements', async () => {
    await expect(aboutModal.modal).toBeVisible();
    await expect(aboutModal.modalTitle).toBeVisible();
    await expect(aboutModal.heading).toBeVisible();
    await expect(aboutModal.logo).toBeVisible();
    await expect(aboutModal.versionText).toBeVisible();
    await expect(aboutModal.getInvolvedLink).toBeVisible();
    await expect(aboutModal.licenseLink).toBeVisible();
  });

  test('Given About Zeppelin modal is open, When viewing version information, Then version should be displayed', async () => {
    const version = await aboutModal.getVersionText();
    expect(version).toBeTruthy();
    expect(version.length).toBeGreaterThan(0);
  });

  test('Given About Zeppelin modal is open, When checking external links, Then links should have correct URLs', async () => {
    const getInvolvedHref = await aboutModal.getGetInvolvedHref();
    const licenseHref = await aboutModal.getLicenseHref();

    expect(getInvolvedHref).toContain('zeppelin.apache.org');
    expect(licenseHref).toContain('apache.org/licenses');
  });

  test('Given About Zeppelin modal is open, When clicking close button, Then modal should close', async () => {
    await aboutModal.close();
    await expect(aboutModal.modal).not.toBeVisible();
  });

  test('Given About Zeppelin modal is open, When checking logo, Then logo should be visible and properly loaded', async () => {
    const isLogoVisible = await aboutModal.isLogoVisible();
    expect(isLogoVisible).toBe(true);
  });
});
