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

import { randomUUID } from 'crypto';
import { expect, test } from '@playwright/test';
import { HeaderPage } from '../../models/header-page';
import { NotebookSearchPage } from '../../models/notebook-search-page';
import { waitForSearchIndex } from '../../models/notebook-search-page.util';
import {
  addPageAnnotationBeforeEach,
  createNotebookViaRest,
  PAGES,
  setParagraphText,
  waitForZeppelinReady
} from '../../utils';

test.describe('Notebook search route', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_SEARCH);
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_SEARCH_RESULT);

  let search: NotebookSearchPage;
  let header: HeaderPage;
  let noteId: string;
  let paragraphId: string;
  let noteName: string;
  let paragraphTerm: string;
  let source: string;
  let ownedNoteIds: string[];

  test.beforeEach(async ({ page }) => {
    search = new NotebookSearchPage(page);
    header = new HeaderPage(page);
    ownedNoteIds = [];
    const token = randomUUID().replace(/-/g, '');
    noteName = `Search${token} Route${token}`;
    paragraphTerm = `paragraph${token}`;
    source = `%sql\nSELECT '${paragraphTerm}' AS marker WHERE 1 < 2`;

    await test.step('Given an indexed notebook with a distinct name and non-first matching paragraph', async () => {
      const note = await createNotebookViaRest(page, noteName);
      noteId = note.noteId;
      ownedNoteIds.push(noteId);
      const response = await page.request.post(`/api/notebook/${noteId}/paragraph`, {
        data: { title: `Title ${paragraphTerm}` },
        failOnStatusCode: true
      });
      const payload: unknown = await response.json();
      if (!payload || typeof payload !== 'object' || !('body' in payload) || typeof payload.body !== 'string') {
        throw new Error('Paragraph creation response did not contain a paragraph id');
      }
      paragraphId = payload.body;
      await setParagraphText(page, noteId, paragraphId, source);
      await waitForSearchIndex(page, noteName, noteId);
      await waitForSearchIndex(page, paragraphTerm, `${noteId}/paragraph/${paragraphId}`);
      await page.goto('/');
      await waitForZeppelinReady(page);
    });
  });

  test.afterEach(async ({ page }) => {
    for (const id of ownedNoteIds) {
      const response = await page.request.delete(`/api/notebook/${id}`, { failOnStatusCode: true });
      expect(response.ok()).toBe(true);
    }
  });

  test('preserves the trimmed search phrase and opens a notebook-name match', async ({ page }) => {
    const destination = `#/notebook/${noteId}`;

    await test.step('When the header submits a phrase with outer and internal spaces', async () => {
      await header.searchNote(`  ${noteName}  `);
    });

    await test.step('Then the route and header preserve the trimmed phrase and render its result', async () => {
      await expect(page).toHaveURL(url => decodeURIComponent(url.hash) === `#/search/${noteName}`);
      await expect(header.searchInput).toHaveValue(noteName);
      await expect(search.resultLink(noteName, destination)).toBeVisible();
      await expect(search.snippet(noteName, destination)).toContainText(noteName);
      await expect(search.emptyState).not.toBeVisible();
    });

    await test.step('When the notebook-name link is clicked', async () => {
      await search.resultLink(noteName, destination).click();
    });

    await test.step('Then the matching notebook loads without paragraph query parameters', async () => {
      await expect(page).toHaveURL(url => url.hash === `#/notebook/${noteId}`);
      await expect(search.notebookTitle).toHaveText(noteName);
    });
  });

  test('restores the search input and results from a direct URL and after reload', async ({ page }) => {
    const destination = `#/notebook/${noteId}`;
    const searchUrl = `/#/search/${encodeURIComponent(noteName)}`;
    const expectRestoredSearch = async () => {
      await expect(page).toHaveURL(url => decodeURIComponent(url.hash) === `#/search/${noteName}`);
      await expect(header.searchInput).toHaveValue(noteName);
      await expect(search.resultLink(noteName, destination)).toBeVisible();
      await expect(search.snippet(noteName, destination)).toContainText(noteName);
      await expect(search.emptyState).not.toBeVisible();
    };

    await test.step('When an encoded search URL is opened without the existing application state', async () => {
      await page.goto('about:blank');
      await page.goto(searchUrl);
      await waitForZeppelinReady(page);
    });

    await test.step('Then the search phrase and matching result are restored from the URL', expectRestoredSearch);

    await test.step('When the search page is reloaded', async () => {
      await page.reload();
      await waitForZeppelinReady(page);
    });

    await test.step('Then the search phrase and matching result are restored again', expectRestoredSearch);
  });

  for (const action of ['title link', 'card body'] as const) {
    test(`opens the matching paragraph through its ${action} with paragraph query parameters`, async ({ page }) => {
      const destination = `#/notebook/${noteId}?paragraph=${paragraphId}&term=${paragraphTerm}`;

      await test.step('When the header searches for text present only in the second paragraph', async () => {
        await header.searchNote(paragraphTerm);
      });

      await test.step('Then the result shows the code, highlighted keyword and interpreter', async () => {
        await expect(page).toHaveURL(url => decodeURIComponent(url.hash) === `#/search/${paragraphTerm}`);
        await expect(header.searchInput).toHaveValue(paragraphTerm);
        await expect(search.resultLink(noteName, destination)).toBeVisible();
        await expect(search.snippet(noteName, destination)).toHaveText(source);
        await expect(search.highlightedKeyword(noteName, destination)).toHaveText(paragraphTerm);
        await expect(search.highlightedTitle(noteName, destination)).toHaveText(paragraphTerm);
        await expect(search.resultCard(noteName, destination).getByText('sql', { exact: true })).toBeVisible();
      });

      await test.step(`When the result ${action} is clicked`, async () => {
        if (action === 'title link') {
          await search.resultLink(noteName, destination).click();
        } else {
          await search.snippet(noteName, destination).click();
        }
      });

      await test.step('Then the matching notebook and paragraph load with the exact search query parameters', async () => {
        await expect(page).toHaveURL(url => {
          const route = new URL(url.hash.slice(1), url.origin);
          return (
            route.pathname === `/notebook/${noteId}` &&
            route.searchParams.get('paragraph') === paragraphId &&
            route.searchParams.get('term') === paragraphTerm
          );
        });
        await expect(search.notebookTitle).toHaveText(noteName);
        await expect(search.paragraphEditor(paragraphId)).toHaveValue(source);
      });
    });
  }

  test('replaces results on the same search route and renders a completed empty state', async ({ page }) => {
    const noteDestination = `#/notebook/${noteId}`;
    const paragraphDestination = `#/notebook/${noteId}?paragraph=${paragraphId}&term=${paragraphTerm}`;
    const missingTerm = `missing${randomUUID().replace(/-/g, '')}`;

    await test.step('Given a successful notebook-name search', async () => {
      await header.searchNote(noteName);
      await expect(search.resultLink(noteName, noteDestination)).toBeVisible();
    });

    await test.step('When another query is submitted from the search page', async () => {
      await header.searchNote(paragraphTerm);
    });

    await test.step('Then the new result replaces the previous document', async () => {
      await expect(page).toHaveURL(url => decodeURIComponent(url.hash) === `#/search/${paragraphTerm}`);
      await expect(header.searchInput).toHaveValue(paragraphTerm);
      await expect(search.resultLink(noteName, paragraphDestination)).toBeVisible();
      await expect(search.resultLink(noteName, noteDestination)).toHaveCount(0);
      await expect(search.emptyState).not.toBeVisible();
    });

    await test.step('When a valid query with no matching document is submitted', async () => {
      await header.searchNote(missingTerm);
    });

    await test.step('Then the completed empty state includes the current query and no stale cards', async () => {
      await expect(page).toHaveURL(url => decodeURIComponent(url.hash) === `#/search/${missingTerm}`);
      await expect(header.searchInput).toHaveValue(missingTerm);
      await expect(search.emptyState).toBeVisible();
      await expect(search.emptyState).toContainText(`'${missingTerm}'`);
      await expect(search.cards).toHaveCount(0);
    });
  });
});
