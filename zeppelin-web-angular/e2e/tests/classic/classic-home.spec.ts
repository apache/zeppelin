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

import { expect, Page, test } from '@playwright/test';

const CLASSIC_HOME = '/classic';

const waitForClassicHomeReady = async (page: Page) => {
  await page.goto(CLASSIC_HOME, { waitUntil: 'domcontentloaded' });
  await expect(page.locator('#welcome')).toHaveText('Welcome to Zeppelin!', { timeout: 30000 });
  await expect(page.getByRole('link', { name: /Import note/ })).toBeVisible();
  await expect(page.getByRole('link', { name: /Create new note/ })).toBeVisible();
};

test.describe('Classic home', () => {
  test.beforeEach(async ({ page }) => {
    await waitForClassicHomeReady(page);
  });

  test('should have a welcome message', async ({ page }) => {
    await expect(page.locator('#welcome')).toHaveText('Welcome to Zeppelin!');
  });

  test('should have the button for importing notebook', async ({ page }) => {
    await expect(page.getByRole('link', { name: /Import note/ })).toBeVisible();
  });

  test('should have the button for creating notebook', async ({ page }) => {
    await expect(page.getByRole('link', { name: /Create new note/ })).toBeVisible();
  });

  test('correct save permission in interpreter', async ({ page }) => {
    const ownerName = 'admin';
    const interpreterName = `interpreter_e2e_test_${Date.now()}`;

    await page.locator('.username').click();
    await page.locator('a[href="#/interpreter"]').click();
    await expect(page.locator('.interpreterHead')).toBeVisible({ timeout: 30000 });

    await page.locator('button[ng-click="showAddNewSetting = !showAddNewSetting"]').click();
    const createForm = page.locator('.interpreterSettingAdd');
    await expect(createForm).toBeVisible();
    await createForm.locator('#newInterpreterSettingName').fill(interpreterName);
    await createForm.locator('select[ng-model="newInterpreterSetting.group"]').selectOption({ label: 'angular' });
    await createForm.locator('#idShowPermission').check();

    const ownerInput = createForm.locator('input.select2-search__field');
    await ownerInput.fill(ownerName);
    // JUSTIFIED: Select2 can render grouped AJAX/tag candidates; the final visible option is the concrete typed owner.
    const ownerOption = page.locator('.select2-results__option').filter({ hasText: ownerName }).last();
    await expect(ownerOption).toBeVisible({ timeout: 30000 });
    await ownerInput.press('Enter');
    await expect(createForm.locator('.select2-selection__choice', { hasText: ownerName })).toBeVisible();

    await createForm.locator('span[ng-click="addNewInterpreterSetting()"]').click();

    let setting = page.locator(`#${interpreterName}`);
    await expect(setting).toBeVisible({ timeout: 30000 });

    await setting.locator('span.fa-pencil').click();
    await setting.locator('button[type="submit"]').click();
    await page.locator('.bootstrap-dialog-footer-buttons button', { hasText: 'OK' }).click();

    await page.goto('/classic/#/interpreter', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('.interpreterHead')).toBeVisible({ timeout: 30000 });
    setting = page.locator(`#${interpreterName}`);
    await expect(setting.locator(`select[id="${interpreterName}Owners"] option`)).toHaveText(ownerName, {
      timeout: 30000
    });

    await setting.locator('span.fa-trash').click();
    await page.locator('.bootstrap-dialog-footer-buttons button', { hasText: 'OK' }).click();
    await expect(setting).toBeHidden({ timeout: 30000 });
  });
});
