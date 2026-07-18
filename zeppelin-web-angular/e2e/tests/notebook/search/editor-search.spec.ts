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
import { EditorSearchPage } from 'e2e/models/editor-search-page';
import {
  addPageAnnotationBeforeEach,
  createTestNotebook,
  PAGES,
  performLoginIfRequired,
  skipWhenAuthenticationIsStillRequired,
  waitForNotebookLinks,
  waitForZeppelinReady
} from '../../../utils';

// Covers the per-paragraph Monaco find widget. The notebook-wide search/replace menu is
// unimplemented and tracked by ZEPPELIN-6442.
test.describe('Notebook editor search', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK);

  let editorSearchPage: EditorSearchPage;

  test.beforeEach(async ({ page }) => {
    editorSearchPage = new EditorSearchPage(page);
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
    await skipWhenAuthenticationIsStillRequired(page);
    await waitForNotebookLinks(page);
  });

  test('shows match count and navigates next and previous matches', async ({ page }) => {
    const { noteId } = await createTestNotebook(page);

    await editorSearchPage.openNotebook(noteId);
    await editorSearchPage.setEditorContent('alpha target beta target gamma target');
    await editorSearchPage.openFindWidget();
    await editorSearchPage.searchFor('target');

    await expect(editorSearchPage.matchesCount).toContainText(/1 of 3/, { timeout: 15000 });

    await editorSearchPage.nextMatchButton.click();
    await expect(editorSearchPage.matchesCount).toContainText(/2 of 3/, { timeout: 15000 });

    await editorSearchPage.previousMatchButton.click();
    await expect(editorSearchPage.matchesCount).toContainText(/1 of 3/, { timeout: 15000 });
  });

  test('opens the find widget with the search shortcut', async ({ page }) => {
    const { noteId } = await createTestNotebook(page);

    await editorSearchPage.openNotebook(noteId);
    await editorSearchPage.setEditorContent('find me in this line');
    await editorSearchPage.openFindWidget();

    await expect(editorSearchPage.findWidget).toBeVisible();
  });

  test('highlights every match in the editor', async ({ page }) => {
    const { noteId } = await createTestNotebook(page);

    await editorSearchPage.openNotebook(noteId);
    await editorSearchPage.setEditorContent('alpha target beta target gamma target');
    await editorSearchPage.openFindWidget();
    await editorSearchPage.searchFor('target');

    await expect(editorSearchPage.matchesCount).toContainText(/1 of 3/, { timeout: 15000 });
    await expect(editorSearchPage.matchHighlights).toHaveCount(3);
  });

  test('replaces all matches in the editor search widget', async ({ page }) => {
    const { noteId } = await createTestNotebook(page);

    await editorSearchPage.openNotebook(noteId);
    await editorSearchPage.setEditorContent('replace_target one replace_target two replace_target');
    await editorSearchPage.openFindWidget();
    await editorSearchPage.searchFor('replace_target');
    await expect(editorSearchPage.matchesCount).toContainText(/1 of 3/, { timeout: 15000 });

    await editorSearchPage.toggleReplaceButton.click();
    await editorSearchPage.replaceInput.fill('replacement');
    await editorSearchPage.replaceAllButton.click();

    await expect(editorSearchPage.editorText).toContainText('replacement one replacement two replacement', {
      timeout: 15000
    });
    await expect(editorSearchPage.editorText).not.toContainText('replace_target');
  });
});
