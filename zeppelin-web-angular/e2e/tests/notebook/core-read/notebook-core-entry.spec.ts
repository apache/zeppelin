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

import { expect, test, Route } from '@playwright/test';

import { NotebookCoreReadPage, NOTEBOOK_CORE_READ_BRANCHES } from '../../../models/notebook-core-read-page';
import {
  checkpoint,
  deleteNote,
  firstParagraphId,
  importSavedResultsNote,
  setNotePermissions,
  CoreReadSocketProbe
} from '../../../models/notebook-core-read-page.util';
import {
  addPageAnnotationBeforeEach,
  getTwoTestAccounts,
  loginAs,
  performLoginIfRequired,
  waitForNotebookLinks,
  waitForZeppelinReady,
  PAGES
} from '../../../utils';

const REACT = 'notebookCoreReact';
const REMOTE_ENTRY = '**/remoteEntry.js';

const uniqueName = (label: string) => `CoreEntry_${label}_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`;

test.describe('Notebook Core read entry', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_CORE_ENTRY);

  let probe: CoreReadSocketProbe;
  let readPage: NotebookCoreReadPage;
  const notes: string[] = [];

  test.beforeEach(async ({ page }) => {
    probe = await CoreReadSocketProbe.install(page);
    readPage = new NotebookCoreReadPage(page);
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
    await waitForNotebookLinks(page);
  });

  test.afterEach(async ({ page }) => {
    for (const noteId of notes.splice(0)) {
      await deleteNote(page.request, noteId);
    }
  });

  const importNote = async (request: Parameters<typeof importSavedResultsNote>[0], label: string) => {
    const noteId = await importSavedResultsNote(request, uniqueName(label));
    notes.push(noteId);
    return noteId;
  };

  const getNoteReads = (from: number) => probe.sentOps(from).filter(op => op === 'GET_NOTE');

  for (const { label, query, renderer } of NOTEBOOK_CORE_READ_BRANCHES) {
    test.describe(`with the ${label} renderer`, () => {
      test('follows note changes on one entry, sending reads only', async ({ page }) => {
        const a = await importNote(page.request, 'A');
        const b = await importNote(page.request, 'B');
        const from = probe.sent.length;

        await test.step('When the private route opens a note', async () => {
          await readPage.open(a, undefined, query);
        });

        await test.step('Then the entry shows it with the chosen renderer', async () => {
          await expect(readPage.entry).toHaveAttribute('data-renderer', renderer);
          await expect(readPage.readStatus('ready')).toBeVisible();
          await expect(readPage.shows(a)).toBeVisible();
        });

        await test.step('When the route moves to another note', async () => {
          await readPage.open(b, undefined, query);
        });

        await test.step('Then the same entry reads and shows that note', async () => {
          await expect(readPage.shows(b)).toBeVisible();
          await expect(readPage.readStatus('ready')).toBeVisible();
          await expect(readPage.entry).toHaveAttribute('data-renderer', renderer);
          expect(probe.sentOps(from).filter(op => op !== 'PING')).toEqual(['GET_NOTE', 'GET_NOTE']);
          expect(probe.forbiddenOps(from)).toEqual([]);
        });
      });

      test('enters and leaves a revision on one entry', async ({ page }) => {
        const noteId = await importNote(page.request, 'revision');
        const revisionId = await checkpoint(page.request, noteId, 'entry revision');
        test.skip(revisionId === null, 'The configured notebook storage does not support revisions');
        const from = probe.sent.length;

        await test.step('When the private route opens a revision', async () => {
          await readPage.open(noteId, revisionId!, query);
        });

        await test.step('Then the revision is shown', async () => {
          await expect(readPage.shows(revisionId!)).toBeVisible();
          await expect(readPage.readStatus('ready')).toBeVisible();
        });

        await test.step('When the route moves to the live note', async () => {
          await readPage.open(noteId, undefined, query);
        });

        await test.step('Then the live note is read on the same entry', async () => {
          await expect(readPage.shows(revisionId!)).toHaveCount(0);
          await expect(readPage.readStatus('ready')).toBeVisible();
          await expect(readPage.entry).toHaveAttribute('data-renderer', renderer);
          expect(probe.sentOps(from).filter(op => op !== 'PING')).toEqual(['NOTE_REVISION', 'GET_NOTE']);
        });
      });

      test('shows access denied to a user the note does not admit', async ({ page, browser }) => {
        const accounts = await getTwoTestAccounts();
        test.skip(!accounts, 'Needs two shiro accounts; anonymous mode admits everyone');
        const [owner, other] = accounts!;
        const noteId = await importNote(page.request, 'forbidden');
        await setNotePermissions(page.request, noteId, owner.username);

        // A fresh context would otherwise inherit the signed-in state of the default user.
        const otherContext = await browser.newContext({ storageState: { cookies: [], origins: [] } });
        try {
          const otherPage = await otherContext.newPage();
          await loginAs(otherPage, other);
          const otherReadPage = new NotebookCoreReadPage(otherPage);

          await otherReadPage.open(noteId, undefined, query);

          await expect(otherReadPage.entry).toHaveAttribute('data-renderer', renderer);
          await expect(otherReadPage.readStatus('access-denied')).toBeVisible();
        } finally {
          await otherContext.close();
        }
      });
    });
  }

  const deadRemotes: ReadonlyArray<{ label: string; handle: (route: Route) => Promise<void> }> = [
    { label: 'the remote request fails', handle: route => route.abort() },
    { label: 'the remote answers 404', handle: route => route.fulfill({ status: 404, body: '' }) }
  ];

  for (const { label, handle } of deadRemotes) {
    test(`falls back to the Angular read view on the same Core when ${label}`, async ({ page }) => {
      const noteId = await importNote(page.request, 'deadRemote');
      const from = probe.sent.length;

      await test.step('Given a remote whose entry does not load', async () => {
        await page.route(REMOTE_ENTRY, handle);
      });

      await test.step('When the private route opens with the React renderer', async () => {
        // Angular also renders without the flag, so awaiting the request proves this is a fallback.
        const remoteRequested = page.waitForRequest(REMOTE_ENTRY);
        await readPage.open(noteId, undefined, REACT);
        await remoteRequested;
      });

      await test.step('Then the Angular read view shows the note read once', async () => {
        await expect(readPage.entry).toHaveAttribute('data-renderer', 'angular-fallback');
        await expect(readPage.view).toHaveAttribute('data-status', 'ready');
        await expect(readPage.skeleton).toHaveCount(0);
        expect(getNoteReads(from)).toEqual(['GET_NOTE']);
      });
    });
  }

  test('falls back to the Angular read view when the remote never answers', async ({ page }) => {
    const noteId = await importNote(page.request, 'slowRemote');

    await test.step('Given a remote that accepts the request and never answers', async () => {
      // The handler settles nothing on purpose: the request is left open.
      await page.route(REMOTE_ENTRY, () => {});
    });

    await test.step('When the private route opens with the React renderer', async () => {
      await readPage.open(noteId, undefined, REACT);
    });

    await test.step('Then the Angular read view takes over once the load budget expires', async () => {
      await expect(readPage.entry).toHaveAttribute('data-renderer', 'angular-fallback', { timeout: 30000 });
      await expect(readPage.view).toHaveAttribute('data-status', 'ready');
    });
  });

  test('falls back when the React view fails while mounting, keeping the paragraph anchor in the URL', async ({
    page
  }) => {
    const noteId = await importNote(page.request, 'mountFailure');
    const paragraphId = await firstParagraphId(page.request, noteId);
    const from = probe.sent.length;

    await test.step('When the React view is made to fail before its first commit', async () => {
      await readPage.open(noteId, undefined, `${REACT}&notebookCoreReactFailure=mount&paragraph=${paragraphId}`);
    });

    await test.step('Then the Angular read view shows the note and the URL keeps the anchor', async () => {
      await expect(readPage.entry).toHaveAttribute('data-renderer', 'angular-fallback');
      await expect(readPage.view).toHaveAttribute('data-status', 'ready');
      await expect(readPage.skeleton).toHaveCount(0);
      await expect(page).toHaveURL(new RegExp(`paragraph=${paragraphId}`));
      expect(getNoteReads(from)).toEqual(['GET_NOTE']);
    });
  });

  test('only reports a failure after the React view mounted, leaving the view empty', async ({ page }) => {
    const noteId = await importNote(page.request, 'afterMountFailure');

    await test.step('When the React view fails after its first commit', async () => {
      await readPage.open(noteId, undefined, `${REACT}&notebookCoreReactFailure=after-mount`);
    });

    await test.step('Then the entry records it without switching to Angular', async () => {
      await expect(readPage.entry).toHaveAttribute('data-renderer', 'runtime-error');
      // Intended: the error boundary renders nothing, and a fallback is only taken before mounting.
      await expect(readPage.skeleton).toHaveCount(0);
      await expect(readPage.view).toHaveCount(0);
    });
  });

  test('passes the paragraph anchor to the React view', async ({ page }) => {
    const noteId = await importNote(page.request, 'anchor');
    const paragraphId = await firstParagraphId(page.request, noteId);

    await readPage.open(noteId, undefined, `${REACT}&paragraph=${paragraphId}`);

    await expect(readPage.entry).toHaveAttribute('data-renderer', 'react-mounted');
    await expect(readPage.skeleton.getByText(paragraphId, { exact: true })).toBeVisible();
  });

  test('leaving private mode loads the editable notebook afresh at the paragraph anchor', async ({ page }) => {
    const noteId = await importNote(page.request, 'leave');
    const lastParagraph = (
      (await (await page.request.get(`/api/notebook/${noteId}`)).json()).body.paragraphs as {
        id: string;
      }[]
    ).at(-1)!.id;
    // A short viewport keeps the last paragraph out of view unless the notebook scrolls to it.
    await page.setViewportSize({ width: 1280, height: 400 });

    await test.step('Given the React read view of the note', async () => {
      await readPage.open(noteId, undefined, `${REACT}&paragraph=${lastParagraph}`);
      await expect(readPage.entry).toHaveAttribute('data-renderer', 'react-mounted');
    });

    const from = probe.sent.length;

    await test.step('When the URL drops the private flags', async () => {
      await readPage.openEditable(noteId, `paragraph=${lastParagraph}`);
    });

    await test.step('Then the editable notebook loads the note itself and scrolls to the anchor', async () => {
      await expect(readPage.editableNotebook).toBeVisible();
      await expect(readPage.entry).toHaveCount(0);
      await expect(readPage.editableParagraphs.last()).toBeInViewport();
      await expect.poll(() => getNoteReads(from)).toContain('GET_NOTE');
    });
  });
});
