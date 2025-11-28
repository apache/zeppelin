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

import { test } from '@playwright/test';
import { HomePageUtil } from '../../models/home-page.util';
import { addPageAnnotationBeforeEach, performLoginIfRequired, waitForZeppelinReady, PAGES } from '../../utils';

addPageAnnotationBeforeEach(PAGES.WORKSPACE.HOME);

test.describe('Home Page Notebook Actions', () => {
  let homeUtil: HomePageUtil;

  test.beforeEach(async ({ page }) => {
    homeUtil = new HomePageUtil(page);
    await page.goto('/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
  });

  test.describe('Given notebook list is displayed', () => {
    test('When page loads Then should show notebook actions', async ({ page }) => {
      await homeUtil.verifyNotebookActions();
    });

    test('When refresh button is clicked Then should trigger reload with loading state', async ({ page }) => {
      await homeUtil.testNotebookRefreshLoadingState();
    });

    test('When filter is used Then should filter notebook list', async ({ page }) => {
      await homeUtil.testFilterFunctionality('test');
    });
  });

  test.describe('Given create new note action', () => {
    test('When create new note is clicked Then should open note creation modal', async ({ page }) => {
      await homeUtil.verifyCreateNewNoteWorkflow();
    });
  });

  test.describe('Given import note action', () => {
    test('When import note is clicked Then should open import modal', async ({ page }) => {
      await homeUtil.verifyImportNoteWorkflow();
    });
  });

  test.describe('Given notebook refresh functionality', () => {
    test('When refresh is triggered Then should maintain notebook list visibility', async ({ page }) => {
      await homeUtil.verifyNotebookRefreshFunctionality();
    });
  });
});
