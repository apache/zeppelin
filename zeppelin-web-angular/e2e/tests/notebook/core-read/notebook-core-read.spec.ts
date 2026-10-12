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

import { NotebookCoreReadPage } from '../../../models/notebook-core-read-page';
import {
  checkpoint,
  deleteNote,
  firstParagraphId,
  importSavedResultsNote,
  isNoteFrame,
  renameParagraph,
  setNotePermissions,
  waitForReceived,
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

const uniqueName = (label: string) => `CoreRead_${label}_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`;

test.describe('Notebook Core read view', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_CORE_READ);

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
    const name = uniqueName(label);
    const noteId = await importSavedResultsNote(request, name);
    notes.push(noteId);
    return { noteId, name };
  };

  test('renders saved results and sends only the read, including on mount and unmount', async ({ page }) => {
    const { noteId } = await importNote(page.request, 'render');
    const mounted = probe.sent.length;

    await test.step('When the private view opens the note', async () => {
      await readPage.open(noteId);
      await expect(readPage.view).toHaveAttribute('data-status', 'ready');
    });

    await test.step('Then paragraphs, saved results and chart settings are shown', async () => {
      await expect(readPage.paragraphTitles).toHaveText(['Markdown', 'Table', 'Text', 'Angular']);
      await expect(readPage.results).toHaveCount(3);
      await expect(readPage.results.nth(0)).toContainText('Saved heading');
      await expect(readPage.resultChart(1)).toBeVisible();
      await expect(readPage.resultTable(1)).toHaveCount(0);
      await expect(readPage.results.nth(2)).toContainText('saved text output');
      await expect(readPage.unsupportedResults).toHaveAttribute('data-result-type', 'ANGULAR');
      await expect(readPage.permissions).toContainText('Your access:');
    });

    const unmounted = probe.sent.length;

    await test.step('When the editable route replaces the private view', async () => {
      await readPage.openEditable(noteId);
      await expect(readPage.editableNotebook).toBeVisible();
      await expect(readPage.view).toHaveCount(0);
    });

    await test.step('Then the private view sent only its read, and nothing while unmounting', async () => {
      expect(probe.sentOps(mounted, unmounted).filter(op => op !== 'PING')).toEqual(['GET_NOTE']);
      expect(probe.forbiddenOps(mounted, unmounted)).toEqual([]);
      await expect.poll(() => probe.sentOps(unmounted).find(op => op !== 'PING')).toBe('GET_NOTE');
    });
  });

  test('follows note changes and ignores the late reply of the previous note (A -> B -> A)', async ({ page }) => {
    const a = await importNote(page.request, 'A');
    const b = await importNote(page.request, 'B');
    const from = probe.sent.length;
    const releaseA = probe.hold(isNoteFrame(a.noteId));

    await readPage.open(a.noteId);
    await expect(readPage.view).toHaveAttribute('data-status', 'loading');
    await readPage.open(b.noteId);
    await expect(readPage.view).toHaveAttribute('data-status', 'ready');
    await expect(readPage.title).toHaveText(b.name);

    const delivered = waitForReceived(page, 'NOTE');
    releaseA();
    await delivered;
    await expect(readPage.title).toHaveText(b.name);
    await expect(readPage.view).toHaveAttribute('data-note-id', b.noteId);

    await readPage.open(a.noteId);
    await expect(readPage.title).toHaveText(a.name);
    expect(probe.sentOps(from).filter(op => op !== 'PING')).toEqual(['GET_NOTE', 'GET_NOTE', 'GET_NOTE']);
  });

  test('completes only on its own reply, then ignores a duplicate reply and NOTE broadcasts', async ({ page }) => {
    const { noteId } = await importNote(page.request, 'duplicate');
    const current = (await (await page.request.get(`/api/notebook/${noteId}`)).json()).body;
    const injected = {
      ...current,
      paragraphs: current.paragraphs.map((p: object, i: number) => (i ? p : { ...p, title: 'Injected' }))
    };
    const releaseReply = probe.hold(isNoteFrame(noteId));

    await test.step('Given a read whose reply has not arrived', async () => {
      await readPage.open(noteId);
      await expect(readPage.view).toHaveAttribute('data-status', 'loading');
      await expect.poll(() => probe.sentOps().includes('GET_NOTE')).toBe(true);
    });

    await test.step('Then a NOTE broadcast of the same note does not complete it', async () => {
      const delivered = waitForReceived(page, 'NOTE');
      probe.inject({ op: 'NOTE', data: { note: injected } });
      await delivered;
      await expect(readPage.view).toHaveAttribute('data-status', 'loading');
    });

    await test.step('When its own reply arrives, that reply is shown', async () => {
      releaseReply();
      await expect(readPage.view).toHaveAttribute('data-status', 'ready');
      await expect(readPage.paragraphTitles.first()).toHaveText('Markdown');
    });

    await test.step('Then a duplicate reply and another broadcast change nothing', async () => {
      const delivered = waitForReceived(page, 'NOTE', 2);
      probe.inject({ op: 'NOTE', msgId: probe.lastRead('GET_NOTE').msgId, data: { note: injected } });
      probe.inject({ op: 'NOTE', data: { note: injected } });
      await delivered;
      await expect(readPage.paragraphTitles.first()).toHaveText('Markdown');
    });
  });

  test('enters, leaves and re-enters a revision without live notes changing it', async ({ page }) => {
    const { noteId } = await importNote(page.request, 'revision');
    const revisionId = await checkpoint(page.request, noteId, 'before rename');
    test.skip(revisionId === null, 'The configured notebook storage does not support revisions');
    await renameParagraph(page.request, noteId, await firstParagraphId(page.request, noteId), 'Renamed');
    const from = probe.sent.length;

    await readPage.open(noteId, revisionId!);
    await expect(readPage.revisionBadge).toContainText(revisionId!);
    await expect(readPage.paragraphTitles.first()).toHaveText('Markdown');

    const live = (await (await page.request.get(`/api/notebook/${noteId}`)).json()).body;
    const delivered = waitForReceived(page, 'NOTE');
    probe.inject({ op: 'NOTE', data: { note: live } });
    await delivered;
    await expect(readPage.paragraphTitles.first()).toHaveText('Markdown');

    await readPage.open(noteId);
    await expect(readPage.revisionBadge).toHaveCount(0);
    await expect(readPage.paragraphTitles.first()).toHaveText('Renamed');

    await readPage.open(noteId, revisionId!);
    await expect(readPage.paragraphTitles.first()).toHaveText('Markdown');
    expect(probe.sentOps(from).filter(op => op !== 'PING')).toEqual(['NOTE_REVISION', 'GET_NOTE', 'NOTE_REVISION']);
  });

  test('cancels the permissions read of the previous note and keeps a failed one unknown', async ({ page }) => {
    const a = await importNote(page.request, 'aclA');
    const b = await importNote(page.request, 'aclB');
    let releaseA!: () => void;
    const aReleased = new Promise<void>(resolve => (releaseA = resolve));
    let aborted = false;
    page.on('requestfailed', request => {
      if (request.url().endsWith(`/api/notebook/${a.noteId}/permissions`)) aborted = true;
    });
    await page.route(`**/api/notebook/${a.noteId}/permissions`, async route => {
      await aReleased;
      // JUSTIFIED: the page has aborted this request by now, which is what the test asserts.
      await route.continue().catch(() => undefined);
    });
    await page.route(`**/api/notebook/${b.noteId}/permissions`, route => route.fulfill({ status: 500, body: '{}' }));

    try {
      await readPage.open(a.noteId);
      await readPage.open(b.noteId);
      await expect(readPage.view).toHaveAttribute('data-status', 'ready');
      await expect(readPage.view).toHaveAttribute('data-acl-status', 'failed');
      await expect(readPage.permissions).toHaveText('Permissions unknown');
      await expect.poll(() => aborted).toBe(true);
    } finally {
      releaseA();
    }
  });

  test('reports a note that cannot be loaded as a failure', async () => {
    await readPage.open('NO_SUCH_NOTE');

    await expect(readPage.view).toHaveAttribute('data-status', 'failed');
    await expect(readPage.error).toContainText('could not be loaded');
    await expect(readPage.permissions).toHaveCount(0);
  });

  test('invalidates the note when the connection drops and reads it again after the Shell reconnects', async ({
    page
  }) => {
    const { noteId } = await importNote(page.request, 'reconnect');
    await readPage.open(noteId);
    await expect(readPage.view).toHaveAttribute('data-status', 'ready');
    const connections = probe.connections;
    const from = probe.sent.length;
    const states = await readPage.recordStates();

    await probe.dropConnection();
    await expect.poll(() => probe.connections).toBeGreaterThan(connections);
    await expect.poll(states).toContain('ready/true');

    const recorded = await states();
    expect(recorded[0]).toBe('failed/false');
    expect(recorded.slice(0, recorded.indexOf('ready/true'))).not.toContain('ready/false');
    expect(probe.sentOps(from).filter(op => op !== 'PING')).toEqual(['GET_NOTE']);
  });

  test('shows access denied to a user the note does not admit', async ({ page, browser }) => {
    const accounts = await getTwoTestAccounts();
    test.skip(!accounts, 'Needs two shiro accounts; anonymous mode admits everyone');
    const [owner, other] = accounts!;
    const { noteId } = await importNote(page.request, 'forbidden');
    await setNotePermissions(page.request, noteId, owner.username);

    // A fresh context would otherwise inherit the signed-in state of the default user.
    const otherContext = await browser.newContext({ storageState: { cookies: [], origins: [] } });
    try {
      const otherPage = await otherContext.newPage();
      await loginAs(otherPage, other);
      const otherReadPage = new NotebookCoreReadPage(otherPage);

      await otherReadPage.open(noteId);

      await expect(otherReadPage.view).toHaveAttribute('data-status', 'access-denied');
      await expect(otherReadPage.error).toContainText('do not have permission');
    } finally {
      await otherContext.close();
    }
  });
});
