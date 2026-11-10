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

import { expect, interpreterTitlePattern, openInterpreterPage, test } from '../../../models/interpreter-test.util';
import { InterpreterPage } from '../../../models/interpreter-page';
import { addPageAnnotationBeforeEach, PAGES } from '../../../utils';

test.describe('Interpreter Page - Real Route Baseline', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.INTERPRETER);

  let interpreterPage: InterpreterPage;

  test.beforeEach(async ({ page }) => {
    interpreterPage = new InterpreterPage(page);
  });

  test('loads server settings and primary controls on direct navigation and reload', async ({ page }) => {
    for (const navigation of ['direct', 'reload'] as const) {
      await test.step(`When opening the interpreter route via ${navigation}`, async () => {
        const data = await openInterpreterPage(page, navigation);
        await expect(page).toHaveURL(/\/#\/interpreter$/);
        await expect(interpreterPage.title).toBeVisible();
        await expect(interpreterPage.description).toBeVisible();
        await expect(interpreterPage.search).toBeVisible();
        await expect(interpreterPage.repositoryButton).toBeEnabled();
        await expect(interpreterPage.createCard).toBeVisible();
        await expect(interpreterPage.settings).toHaveCount(data.settings.length);
        await expect(interpreterPage.settingTitles).toHaveText(data.settings.map(interpreterTitlePattern));
        for (const setting of data.settings) {
          const item = interpreterPage.setting(setting.name);
          await expect(item.edit).toBeEnabled();
          await expect(item.restart).toBeEnabled();
          await expect(item.remove).toBeEnabled();
          await expect(item.existingProcess).toBeDisabled();
          await expect(item.setPermission).toBeDisabled();
          await expect(item.bindingMode).toHaveAttribute('disabled', '');
          await expect(item.save).toHaveCount(0);
        }
      });
    }
  });

  test('filters actual server settings case-insensitively and restores them on clear', async ({ page }) => {
    const { settings } = await openInterpreterPage(page);
    const searchable = settings.find(setting => /[a-z]/i.test(setting.name));
    if (!searchable) {
      throw new Error('The E2E server must provide a setting name with letters to verify case-insensitive search');
    }
    const character = searchable.name.match(/[a-z]/i)![0];
    const query = character === character.toUpperCase() ? character.toLowerCase() : character.toUpperCase();
    const matches = settings.filter(setting => setting.name.toLowerCase().includes(query.toLowerCase()));
    let missingName = 'e2e-no-matching-interpreter';
    while (settings.some(setting => setting.name.toLowerCase().includes(missingName))) {
      missingName += '-';
    }

    await test.step('When part of a returned name is searched with different casing', async () => {
      await interpreterPage.search.fill(query);
      await expect(interpreterPage.settingTitles).toHaveText(matches.map(interpreterTitlePattern));
    });

    await test.step('Then an absent name hides settings without hiding Create', async () => {
      await interpreterPage.search.fill(missingName);
      await expect(interpreterPage.settings).toHaveCount(0);
      await expect(interpreterPage.createCard).toBeVisible();
    });

    await test.step('Then clearing the search restores all server settings', async () => {
      await interpreterPage.search.clear();
      await expect(interpreterPage.settingTitles).toHaveText(settings.map(interpreterTitlePattern));
    });
  });
});
