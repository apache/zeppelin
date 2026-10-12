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
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { expect, test, type Page } from '@playwright/test';
import {
  createLifecycleRecorder,
  validateLifecycleFixture,
  type LifecycleFixture
} from '../../../core-contract/lifecycle/fixture.mjs';
import { NotebookCoreReadPage } from '../../../models/notebook-core-read-page';
import {
  checkpoint,
  deleteNote,
  importSavedResultsNote,
  setNotePermissions
} from '../../../models/notebook-core-read-page.util';
import {
  addPageAnnotationBeforeEach,
  getTwoTestAccounts,
  loginAs,
  performLoginIfRequired,
  waitForZeppelinReady,
  PAGES
} from '../../../utils';

const fixtureDirectory = () => process.env.ZEPPELIN_READ_FIXTURE_DIR ?? path.resolve('e2e/fixtures/notebook-read');

/**
 * The read replies these captures record depend on server changes that are not on Apache master yet,
 * so they are attributed to the commit that was built and served, which the capture must name.
 */
const readCaptureMetadata = async (
  page: Page,
  scenario: string,
  coveredOperations: string[],
  authentication: 'anonymous' | 'auth'
): Promise<LifecycleFixture['metadata']> => {
  const commit = process.env.ZEPPELIN_READ_CAPTURE_COMMIT;
  if (!commit || !/^[a-f0-9]{40}$/.test(commit)) {
    throw new Error('Read capture requires ZEPPELIN_READ_CAPTURE_COMMIT, the commit the server was built from');
  }
  const ticket = (await (await page.request.get('/api/security/ticket')).json()).body as { principal: string };
  expect(ticket.principal === 'anonymous' ? 'anonymous' : 'auth').toBe(authentication);
  const capabilities = (await (await page.request.get('/api/notebook/capabilities')).json()).body;
  const version = (await (await page.request.get('/api/version')).json()).body as {
    'git-commit-id': string;
    version: string;
  };
  expect(version['git-commit-id']).toMatch(/^[a-f0-9]{7,40}$/);
  expect(commit.startsWith(version['git-commit-id'])).toBe(true);
  return {
    owner: 'zeppelin-web-angular',
    scenario,
    coveredOperations,
    knownExclusions: [
      'Recaptured from Apache master once the read msgId change is merged; until then the source is the named branch commit.',
      'Only reads made by the private Core read view are covered; incremental notebook operations are excluded.',
      'Physical reconnect and logout are Shell-owned and not part of this capture.'
    ],
    environment: {
      authentication,
      browser: { name: page.context().browser()!.browserType().name(), version: page.context().browser()!.version() },
      origin: new URL(page.url()).origin,
      revisionSupported: capabilities.isRevisionSupported,
      interpreterExecution: 'none'
    },
    capturedAt: new Date().toISOString(),
    zeppelinVersion: version.version,
    // Captures are taken from Apache master, so `commit` lives in apache/zeppelin.
    source: { repository: 'apache/zeppelin', commit, serverCommit: version['git-commit-id'] }
  } as LifecycleFixture['metadata'];
};

/**
 * The recorder redacts principals by field name, but AUTH_INFO names the allowed and denied users in
 * its free text, so the account names are replaced there too.
 */
const writeWithoutAccountNames = (fixture: LifecycleFixture, names: string[], file: string) => {
  let text = JSON.stringify(fixture, null, 2);
  for (const name of names) {
    text = text.replace(new RegExp(`\\b${name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`, 'g'), '<user>');
  }
  const redacted = JSON.parse(text) as LifecycleFixture;
  expect(validateLifecycleFixture(redacted)).toEqual([]);
  mkdirSync(path.dirname(file), { recursive: true });
  writeFileSync(file, `${text}\n`);
};

test.describe('Notebook read transport fixtures', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_CORE_READ);

  test('captures live note, revision and missing-note reads', { tag: '@live' }, async ({ page }) => {
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
    const readPage = new NotebookCoreReadPage(page);
    const noteId = await importSavedResultsNote(page.request, `ReadCapture_${Date.now()}`);
    try {
      const revisionId = await checkpoint(page.request, noteId, 'read capture');
      test.skip(revisionId === null, 'Revision capture requires versioned notebook storage');
      const recorder = createLifecycleRecorder(
        await readCaptureMetadata(
          page,
          'Private Core read view: note, revision and missing note',
          ['GET_NOTE', 'NOTE', 'NOTE_REVISION', 'ERROR_INFO', 'GET /api/notebook/{noteId}/permissions'],
          'anonymous'
        )
      );
      recorder.install(page, 'viewer-a');
      await page.reload();
      await waitForZeppelinReady(page);

      recorder.context('viewer-a', { state: 'active', noteId, revisionId: null });
      await readPage.open(noteId);
      await expect(readPage.view).toHaveAttribute('data-status', 'ready');
      await expect(readPage.view).toHaveAttribute('data-acl-status', 'ready');

      recorder.context('viewer-a', { state: 'inactive', noteId, revisionId: null });
      recorder.context('viewer-a', { state: 'active', noteId, revisionId });
      await readPage.open(noteId, revisionId!);
      await expect(readPage.revisionBadge).toBeVisible();
      await expect(readPage.view).toHaveAttribute('data-status', 'ready');
      // The view cancels a permissions read it leaves, and the recorder rejects a cancelled request.
      await expect(readPage.view).toHaveAttribute('data-acl-status', 'ready');

      recorder.context('viewer-a', { state: 'inactive', noteId, revisionId });
      recorder.context('viewer-a', { state: 'active', noteId: 'READ_CAPTURE_MISSING', revisionId: null });
      await readPage.open('READ_CAPTURE_MISSING');
      await expect(readPage.view).toHaveAttribute('data-status', 'failed');
      await expect(readPage.view).not.toHaveAttribute('data-acl-status', 'loading');

      await recorder.stop();
      await recorder.write(path.join(fixtureDirectory(), 'read-anonymous.json'));
    } finally {
      await deleteNote(page.request, noteId);
    }
  });

  test('captures a read the note permissions deny', { tag: '@live' }, async ({ page, browser }) => {
    const accounts = await getTwoTestAccounts();
    test.skip(!accounts, 'The denied read needs two shiro accounts');
    const [owner, other] = accounts!;
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
    const noteId = await importSavedResultsNote(page.request, `ReadCaptureDenied_${Date.now()}`);
    // A fresh context would otherwise inherit the signed-in state of the default user.
    const otherContext = await browser.newContext({ storageState: { cookies: [], origins: [] } });
    try {
      await setNotePermissions(page.request, noteId, owner.username);
      const otherPage = await otherContext.newPage();
      await loginAs(otherPage, other);
      const recorder = createLifecycleRecorder(
        await readCaptureMetadata(
          otherPage,
          'Private Core read view: a read the note permissions deny',
          ['GET_NOTE', 'AUTH_INFO', 'GET /api/notebook/{noteId}/permissions'],
          'auth'
        )
      );
      recorder.install(otherPage, 'viewer-b');
      await otherPage.reload();
      await waitForZeppelinReady(otherPage);

      recorder.context('viewer-b', { state: 'active', noteId, revisionId: null });
      const otherReadPage = new NotebookCoreReadPage(otherPage);
      await otherReadPage.open(noteId);
      await expect(otherReadPage.view).toHaveAttribute('data-status', 'access-denied');
      await expect(otherReadPage.view).toHaveAttribute('data-acl-status', 'access-denied');

      await recorder.stop();
      writeWithoutAccountNames(
        recorder.snapshot(),
        [owner.username, other.username],
        path.join(fixtureDirectory(), 'read-denied.json')
      );
    } finally {
      await otherContext.close();
      await deleteNote(page.request, noteId);
    }
  });
});
