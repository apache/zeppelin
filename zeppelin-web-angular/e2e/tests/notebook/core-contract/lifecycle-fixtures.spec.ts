/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { expect, test } from '@playwright/test';
import {
  captureMetadata,
  lifecycleFixtureDirectory,
  recordCaptureResult,
  cleanUpLifecycleCapture
} from '../../../core-contract/lifecycle-capture';
import {
  createLifecycleRecorder,
  validateLifecycleFixture,
  type LifecycleFixture
} from '../../../core-contract/notebook-lifecycle-fixture.mjs';
import { replayLifecycleTrace } from '../../../core-contract/replay-lifecycle-trace';
import { NotebookTransportPage } from '../../../models/notebook-transport-page';
import { LoginTestUtil } from '../../../models/login-page.util';
import { LoginPage } from '../../../models/login-page';
import { addPageAnnotationBeforeEach, PAGES, waitForZeppelinReady } from '../../../utils';

const operationSets = {
  structural: [
    'MOVE_PARAGRAPH',
    'INSERT_PARAGRAPH',
    'COPY_PARAGRAPH',
    'PARAGRAPH_REMOVE',
    'COMMIT_PARAGRAPH',
    'PARAGRAPH_ADDED',
    'PARAGRAPH_REMOVED',
    'PARAGRAPH_MOVED'
  ],
  revision: [
    'CHECKPOINT_NOTE',
    'LIST_REVISION_HISTORY',
    'NOTE_REVISION',
    'SET_NOTE_REVISION',
    'NOTE_REVISION_FOR_COMPARE'
  ],
  collaboration: ['PATCH_PARAGRAPH', 'NOTE_UPDATED', 'COLLABORATIVE_MODE_STATUS'],
  association: ['GET_NOTE', 'RELOAD_NOTE', 'GET_HOME_NOTE', 'NEW_NOTE', 'CLONE_NOTE', 'LIST_NOTE_JOBS'],
  reconnect: ['GET_NOTE', 'NOTE', 'NOTE_REVISION', 'LIST_REVISION_HISTORY']
};

test.describe('Notebook lifecycle transport fixtures', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK);
  let view: NotebookTransportPage;

  test.beforeEach(({ page }) => {
    view = new NotebookTransportPage(page);
  });

  test.afterEach(async ({}, info) => {
    if (!info.tags.includes('@live')) return;
    const files = [
      { title: 'captures paragraph', file: 'structural.json', operations: operationSets.structural },
      {
        title: 'captures revisions',
        file: 'revision-reconnect.json',
        operations: [...operationSets.revision, ...operationSets.reconnect]
      },
      {
        title: 'captures collaboration',
        file: (await LoginTestUtil.isShiroEnabled()) ? 'collaboration-auth.json' : 'collaboration-anonymous.json',
        operations: operationSets.collaboration
      },
      { title: 'captures association', file: 'association.json', operations: operationSets.association }
    ];
    const entry = files.find(value => info.title.startsWith(value.title));
    if (!entry) return;
    recordCaptureResult(info, { file: entry.file, operations: entry.operations });
  });

  test('replays committed captures in independent browser sessions', async ({ browser }) => {
    const manifest = JSON.parse(readFileSync(path.join(lifecycleFixtureDirectory(), 'manifest.json'), 'utf8')) as {
      fixtures: { file: string; status: string }[];
    };
    const supported = manifest.fixtures.filter(entry => entry.status === 'supported');
    expect(supported.length).toBeGreaterThan(0);
    for (const entry of supported) {
      await test.step(`Then ${entry.file} replays its captured transport order`, async () => {
        const fixture = JSON.parse(
          readFileSync(path.join(lifecycleFixtureDirectory(), entry.file), 'utf8')
        ) as LifecycleFixture;
        expect(validateLifecycleFixture(fixture)).toEqual([]);
        const faults = fixture.metadata.commitLoss?.faults ?? [];
        const frames = await replayLifecycleTrace(browser, fixture, faults);
        expect(frames.size).toBe(fixture.sessions.length);
        for (const session of fixture.sessions) {
          expect(frames.get(session.id)).toEqual(
            fixture.records
              .filter(
                record =>
                  record.sessionId === session.id &&
                  record.kind === 'websocket' &&
                  record.websocket.direction === 'receive' &&
                  !faults.some(fault => fault.sequence === record.sequence && fault.copies === 0)
              )
              .flatMap(record => (record.kind === 'websocket' ? [JSON.parse(record.websocket.payloadText)] : []))
          );
        }
      });
    }
  });

  test(
    'captures paragraph edits through separate REST and WebSocket ingresses',
    { tag: '@live' },
    async ({ page, browser }) => {
      await view.install();
      await page.goto('/#/');
      await waitForZeppelinReady(page);
      const created = await page.request.post('/api/notebook', {
        data: {
          notePath: `LifecycleCapture/structural-${Date.now()}`,
          paragraphs: [{ text: '%md first' }, { text: '%md second' }]
        }
      });
      expect(created.ok()).toBe(true);
      const noteId = (await created.json()).body as string;
      const recorder = createLifecycleRecorder({
        ...(await captureMetadata(page, 'Structural paragraph ingresses', operationSets.structural)),
        authoritativeInputs: [
          { ingress: 'rest', commands: ['insert', 'move', 'remove'], frames: ['NOTE'] },
          {
            ingress: 'websocket',
            commands: ['INSERT_PARAGRAPH', 'COPY_PARAGRAPH', 'MOVE_PARAGRAPH', 'PARAGRAPH_REMOVE'],
            frames: ['PARAGRAPH_ADDED', 'PARAGRAPH_MOVED', 'PARAGRAPH_REMOVED']
          },
          { ingress: 'websocket', commands: ['COMMIT_PARAGRAPH'], frames: ['PARAGRAPH with the matching msgId'] }
        ]
      });
      recorder.install(page, 'viewer-a');
      try {
        await test.step('Given a live note associated with the current socket', async () => {
          // Capture starts before the socket exists; opening a fresh document creates that socket.
          await page.reload();
          recorder.context('viewer-a', { state: 'active', noteId, revisionId: null });
          await view.open(noteId);
        });

        await test.step('When insert, copy, move, commit and remove use WebSocket commands', async () => {
          const added = await view.reply(
            'PARAGRAPH_ADDED',
            await view.send('INSERT_PARAGRAPH', { index: 1, config: {} })
          );
          const paragraph = added.data.paragraph as { id: string };
          await view.reply(
            'PARAGRAPH',
            await view.send('COMMIT_PARAGRAPH', {
              id: paragraph.id,
              noteId,
              title: 'captured',
              paragraph: '%md websocket',
              params: {},
              config: {}
            })
          );
          await view.reply('PARAGRAPH_MOVED', await view.send('MOVE_PARAGRAPH', { id: paragraph.id, index: 0 }));
          await view.reply(
            'PARAGRAPH_ADDED',
            await view.send('COPY_PARAGRAPH', {
              index: 1,
              title: 'copy',
              paragraph: '%md copy',
              params: {},
              config: {}
            })
          );
          await view.reply('PARAGRAPH_REMOVED', await view.send('PARAGRAPH_REMOVE', { id: paragraph.id }));
          expect((await view.canonical(noteId)).paragraphs.map(value => value.text)).toContain('%md copy');
        });

        await test.step('When REST edits broadcast full NOTE snapshots', async () => {
          const after = await view.count();
          const inserted = await page.evaluate(async id => {
            const response = await fetch(`/api/notebook/${id}/paragraph`, {
              method: 'POST',
              headers: { 'content-type': 'application/json' },
              body: JSON.stringify({ index: 0, text: '%md rest' })
            });
            if (!response.ok) throw new Error('REST insert failed');
            return (await response.json()).body as string;
          }, noteId);
          await view.reply('NOTE', after);
          const movedAt = await view.count();
          expect(
            await page.evaluate(
              async ({ id, paragraphId }) =>
                (await fetch(`/api/notebook/${id}/paragraph/${paragraphId}/move/1`, { method: 'POST' })).ok,
              { id: noteId, paragraphId: inserted }
            )
          ).toBe(true);
          await view.reply('NOTE', movedAt);
          const removedAt = await view.count();
          expect(
            await page.evaluate(
              async ({ id, paragraphId }) =>
                (await fetch(`/api/notebook/${id}/paragraph/${paragraphId}`, { method: 'DELETE' })).ok,
              { id: noteId, paragraphId: inserted }
            )
          ).toBe(true);
          await view.reply('NOTE', removedAt);
          expect((await view.canonical(noteId)).paragraphs.map(value => value.id)).not.toContain(inserted);
        });

        await test.step('Then the captured operations replay in the same order', async () => {
          const fixture = await recorder.write(path.join(lifecycleFixtureDirectory(), 'structural.json'));
          const observed = new Set(
            fixture.records
              .filter(record => record.kind === 'websocket')
              .map(record => (JSON.parse(record.websocket.payloadText!) as { op: string }).op)
          );
          expect(operationSets.structural.every(op => observed.has(op))).toBe(true);
          const replay = await replayLifecycleTrace(browser, fixture);
          expect(replay.get('viewer-a')!.length).toBeGreaterThan(0);
        });
      } finally {
        await cleanUpLifecycleCapture(page, recorder, [noteId]);
      }
    }
  );

  test(
    'captures revisions and Angular refetch after physical reconnect',
    { tag: '@live' },
    async ({ page, browser }) => {
      const capability = await page.request.get('/api/notebook/capabilities');
      const capabilities = (await capability.json()).body as { isRevisionSupported: boolean };
      test.skip(!capabilities.isRevisionSupported, 'Revision capture requires capture-server.sh --storage git');
      await view.install();
      await page.goto('/#/');
      await waitForZeppelinReady(page);
      const created = await page.request.post('/api/notebook', {
        data: { notePath: `LifecycleCapture/revision-${Date.now()}`, paragraphs: [{ text: '%md snapshot' }] }
      });
      expect(created.ok()).toBe(true);
      const noteId = (await created.json()).body as string;
      const recorder = createLifecycleRecorder(
        await captureMetadata(page, 'Revision routes and physical reconnect', [
          ...operationSets.revision,
          ...operationSets.reconnect
        ])
      );
      recorder.install(page, 'viewer-a');

      const liveContext = await browser.newContext({ storageState: await page.context().storageState() });
      const livePage = await liveContext.newPage();
      const liveView = new NotebookTransportPage(livePage);
      await liveView.install();
      recorder.install(livePage, 'viewer-b');
      try {
        await page.reload();
        recorder.context('viewer-a', { state: 'active', noteId, revisionId: null });
        await view.open(noteId);
        let revisionId = '';

        await test.step('When a paragraph is committed and checkpointed', async () => {
          const note = await view.canonical(noteId);
          await view.reply(
            'PARAGRAPH',
            await view.send('COMMIT_PARAGRAPH', {
              id: note.paragraphs[0].id,
              noteId,
              title: 'snapshot',
              paragraph: '%md snapshot committed',
              params: {},
              config: {}
            })
          );
          const history = await view.reply(
            'LIST_REVISION_HISTORY',
            await view.send('CHECKPOINT_NOTE', { noteId, commitMessage: 'capture snapshot' })
          );
          revisionId = (history.data.revisionList as { id: string }[])[0].id;
          expect(revisionId).not.toBe('');
        });

        await test.step('Then the live route refetches NOTE and revision history after reconnect', async () => {
          const after = await view.count();
          await view.reconnect();
          await view.reply('NOTE', after);
          await view.reply('LIST_REVISION_HISTORY', after);
          await expect(view.editorText.first()).toContainText('snapshot committed');
        });

        await test.step('When the route switches to a revision and the socket reconnects', async () => {
          recorder.context('viewer-a', { state: 'inactive', noteId, revisionId: null });
          recorder.context('viewer-a', { state: 'active', noteId, revisionId });
          await view.open(noteId, revisionId);
          await livePage.goto('/#/');
          await waitForZeppelinReady(livePage);
          recorder.context('viewer-b', { state: 'active', noteId, revisionId: null });
          await liveView.open(noteId);
          const liveEventAt = await view.count();
          const added = await liveView.reply(
            'PARAGRAPH_ADDED',
            await liveView.send('INSERT_PARAGRAPH', { index: 0, config: {} })
          );
          const liveParagraph = added.data.paragraph as { id: string };
          await liveView.reply(
            'PARAGRAPH_MOVED',
            await liveView.send('MOVE_PARAGRAPH', { id: liveParagraph.id, index: 1 })
          );
          await liveView.reply('PARAGRAPH_REMOVED', await liveView.send('PARAGRAPH_REMOVE', { id: liveParagraph.id }));
          await liveView.reply(
            'NOTE_UPDATED',
            await liveView.send('NOTE_UPDATE', { id: noteId, name: 'live-change', config: { looknfeel: 'simple' } })
          );
          await view.reply('NOTE_UPDATED', liveEventAt);
          await view.reply('PARAGRAPH_ADDED', liveEventAt);
          await view.reply('PARAGRAPH_MOVED', liveEventAt);
          await view.reply('PARAGRAPH_REMOVED', liveEventAt);
          await expect(view.editorText.first()).toContainText('snapshot committed');
          await expect(view.paragraphs).toHaveCount(1);
          const after = await view.count();
          await view.reconnect();
          await view.reply('NOTE_REVISION', after);
          await view.reply('LIST_REVISION_HISTORY', after);
          await expect(page).toHaveURL(new RegExp(`/revision/${revisionId}`));
          await expect(view.editorText.first()).toContainText('snapshot committed');
        });

        await test.step('Then compare and restore preserve revision payloads', async () => {
          const compared = await view.reply(
            'NOTE_REVISION_FOR_COMPARE',
            await view.send('NOTE_REVISION_FOR_COMPARE', { noteId, revisionId, position: 'left' })
          );
          expect(compared.data.revisionId).toBe(revisionId);
          await view.reply('SET_NOTE_REVISION', await view.send('SET_NOTE_REVISION', { noteId, revisionId }));
          await expect(page).toHaveURL(new RegExp(`/notebook/${noteId}$`));
          const fixture = await recorder.write(path.join(lifecycleFixtureDirectory(), 'revision-reconnect.json'));
          const replay = await replayLifecycleTrace(browser, fixture);
          expect(replay.get('viewer-a')!.some(frame => frame.op === 'NOTE_REVISION')).toBe(true);
        });
      } finally {
        await cleanUpLifecycleCapture(page, recorder, [noteId], [liveContext]);
      }
    }
  );

  test(
    'captures collaboration using independent contexts and distinct authenticated users',
    { tag: '@live' },
    async ({ page, browser }) => {
      const authenticated = await LoginTestUtil.isShiroEnabled();
      const credentials = Object.values(await LoginTestUtil.getTestCredentials());
      test.skip(
        authenticated && new Set(credentials.map(value => value.username)).size < 2,
        'Authenticated collaboration requires two distinct capture users'
      );
      const context = await browser.newContext({
        storageState: authenticated ? undefined : await page.context().storageState()
      });
      const follower = await context.newPage();
      const a = view;
      const b = new NotebookTransportPage(follower);
      await a.install();
      await b.install();
      await page.goto('/#/');
      await waitForZeppelinReady(page);
      await follower.goto('/#/');
      await waitForZeppelinReady(follower);
      if (authenticated) {
        const current = (await (await page.request.get('/api/security/ticket')).json()).body.principal as string;
        const other = credentials.find(value => value.username !== current)!;
        await new LoginPage(follower).login(other.username, other.password);
      }
      const principals = await Promise.all(
        [page, follower].map(
          async viewer => (await (await viewer.request.get('/api/security/ticket')).json()).body.principal as string
        )
      );
      expect(!authenticated || principals[0] !== principals[1]).toBe(true);
      const created = await page.request.post('/api/notebook', {
        data: { notePath: `LifecycleCapture/collaboration-${Date.now()}`, paragraphs: [{ text: '%md original' }] }
      });
      expect(created.ok()).toBe(true);
      const noteId = (await created.json()).body as string;
      // ACL is setup for the two actors, rather than a permission fixture.
      expect(
        (
          await page.request.put(`/api/notebook/${noteId}/permissions`, {
            data: { owners: [], readers: [], writers: [], runners: [] }
          })
        ).ok()
      ).toBe(true);
      const recorder = createLifecycleRecorder(
        await captureMetadata(page, 'Independent collaboration viewers', operationSets.collaboration)
      );
      recorder.install(page, 'viewer-a');
      recorder.install(follower, 'viewer-b');
      try {
        await test.step('Given two separately associated browser sessions', async () => {
          await page.reload();
          recorder.context('viewer-a', { state: 'active', noteId, revisionId: null });
          await a.open(noteId);
          await follower.reload();
          recorder.context('viewer-b', { state: 'active', noteId, revisionId: null });
          await b.open(noteId);
        });

        await test.step('When a collaborative patch and note update are broadcast', async () => {
          const note = await a.canonical(noteId);
          const after = await b.count();
          await a.send('PATCH_PARAGRAPH', {
            id: note.paragraphs[0].id,
            noteId,
            patch: '@@ -1,12 +1,12 @@\n %25md \n-original\n+modified\n'
          });
          await b.reply('PATCH_PARAGRAPH', after);
          await expect(b.editorText.first()).toContainText('modified');
          const updatedAt = await b.count();
          await a.send('NOTE_UPDATE', {
            id: noteId,
            name: `collaboration-${Date.now()}`,
            config: { looknfeel: 'simple' }
          });
          await b.reply('NOTE_UPDATED', updatedAt);
          await a.reply('NOTE', await a.send('GET_NOTE', { id: noteId }));
          await b.reply('NOTE', await b.send('GET_NOTE', { id: noteId }));
          await expect(a.editorText.first()).toContainText('modified');
          await expect(b.editorText.first()).toContainText('modified');
          await expect(a.paragraphs).toHaveCount(1);
          await expect(b.paragraphs).toHaveCount(1);
          expect((await a.canonical(noteId)).paragraphs).toEqual((await b.canonical(noteId)).paragraphs);
        });

        await test.step('Then fault delivery preserves the captured canonical refetch payloads', async () => {
          const fixture = await recorder.write(
            path.join(
              lifecycleFixtureDirectory(),
              authenticated ? 'collaboration-auth.json' : 'collaboration-anonymous.json'
            )
          );
          const selected = fixture.records.filter(
            record =>
              record.kind === 'websocket' &&
              record.websocket.direction === 'receive' &&
              ['PATCH_PARAGRAPH', 'NOTE_UPDATED'].includes(
                (JSON.parse(record.websocket.payloadText!) as { op: string }).op
              )
          );
          const refetch = fixture.records.findLast(
            record =>
              record.kind === 'websocket' &&
              record.websocket.direction === 'receive' &&
              (JSON.parse(record.websocket.payloadText!) as { op: string }).op === 'NOTE'
          )!;
          const faults = selected.map((record, index) => ({
            sequence: record.sequence,
            copies: index % 2 ? 0 : 2,
            afterSequence: refetch.sequence - 1,
            delayMs: 5
          }));
          const frames = await replayLifecycleTrace(browser, fixture, faults);
          const snapshots = [...frames.values()].map(values => values.findLast(value => value.op === 'NOTE')!.data);
          expect(snapshots[0]).toEqual(snapshots[1]);
        });
      } finally {
        await cleanUpLifecycleCapture(page, recorder, [noteId], [context]);
      }
    }
  );

  test(
    'captures association transitions and live route changes without tagging broadcasts',
    { tag: '@live' },
    async ({ page, browser }) => {
      await view.install();
      await page.goto('/#/');
      await waitForZeppelinReady(page);
      const settingsResponse = await page.request.get('/api/interpreter/setting');
      const settings = (await settingsResponse.json()).body as { name: string }[];
      test.skip(settings.length === 0, 'Association clone capture requires a configured default interpreter setting');
      const defaultInterpreterGroup = settings[0].name;
      const notes: string[] = [];
      for (const label of ['a', 'b']) {
        const response = await page.request.post('/api/notebook', {
          data: {
            notePath: `LifecycleCapture/association-${label}-${Date.now()}`,
            paragraphs: [{ text: `%md route ${label}` }]
          }
        });
        expect(response.ok()).toBe(true);
        notes.push((await response.json()).body as string);
      }
      const [a, b] = notes;
      const recorder = createLifecycleRecorder(
        await captureMetadata(page, 'Socket association transitions', operationSets.association)
      );
      recorder.install(page, 'viewer-a');
      try {
        await test.step('Given an associated live route A', async () => {
          await page.reload();
          recorder.context('viewer-a', { state: 'active', noteId: a, revisionId: null });
          await view.open(a);
        });

        await test.step('When route B establishes its association and receives incremental edits', async () => {
          recorder.context('viewer-a', { state: 'inactive', noteId: a, revisionId: null });
          recorder.context('viewer-a', { state: 'active', noteId: b, revisionId: null });
          await view.open(b);
          const added = await view.reply(
            'PARAGRAPH_ADDED',
            await view.send('INSERT_PARAGRAPH', { index: 1, config: {} })
          );
          const paragraphId = (added.data.paragraph as { id: string }).id;
          await view.reply('PARAGRAPH_MOVED', await view.send('MOVE_PARAGRAPH', { id: paragraphId, index: 0 }));
          await view.reply('PARAGRAPH_REMOVED', await view.send('PARAGRAPH_REMOVE', { id: paragraphId }));
          await view.reply(
            'NOTE_UPDATED',
            await view.send('NOTE_UPDATE', { id: b, name: 'route-b-updated', config: { looknfeel: 'default' } })
          );
          await expect(view.editorText.first()).toContainText('route b');
        });

        await test.step('When Job Manager, reload, home, create and clone change socket association', async () => {
          recorder.context('viewer-a', { state: 'inactive', noteId: b, revisionId: null });
          await view.reply('LIST_NOTE_JOBS', await view.send('LIST_NOTE_JOBS', {}));
          recorder.context('viewer-a', { state: 'active', noteId: a, revisionId: null });
          await view.open(a);
          await view.reply('NOTE', await view.send('RELOAD_NOTE', { id: a }));
          recorder.context('viewer-a', { state: 'inactive', noteId: a, revisionId: null });
          const home = await view.reply('NOTE', await view.send('GET_HOME_NOTE', {}));
          expect(home.data).toHaveProperty('note');
          const homeNote = home.data.note as { id: string } | null;
          if (homeNote) {
            recorder.context('viewer-a', { state: 'active', noteId: homeNote.id, revisionId: null });
            recorder.context('viewer-a', { state: 'inactive', noteId: homeNote.id, revisionId: null });
          }
          recorder.context('viewer-a', { state: 'active', noteId: b, revisionId: null });
          await view.open(b);
          recorder.context('viewer-a', { state: 'inactive', noteId: b, revisionId: null });
          const createdAt = await view.send('NEW_NOTE', {
            name: `LifecycleCapture/new-${Date.now()}`,
            defaultInterpreterGroup
          });
          const created = await view.reply('NEW_NOTE', createdAt);
          const createdId = (created.data.note as { id: string }).id;
          notes.push(createdId);
          recorder.context('viewer-a', { state: 'active', noteId: createdId, revisionId: null });
          await expect(page).toHaveURL(new RegExp(`/notebook/${createdId}$`));
          const createdNote = await view.reply('NOTE', createdAt);
          expect((createdNote.data.note as { id: string }).id).toBe(createdId);
          recorder.context('viewer-a', { state: 'inactive', noteId: createdId, revisionId: null });
          const clonedAt = await view.send('CLONE_NOTE', { id: b, name: `LifecycleCapture/clone-${Date.now()}` });
          const cloned = await view.reply('NEW_NOTE', clonedAt);
          const cloneId = (cloned.data.note as { id: string }).id;
          notes.push(cloneId);
          recorder.context('viewer-a', { state: 'active', noteId: cloneId, revisionId: null });
          await expect(page).toHaveURL(new RegExp(`/notebook/${cloneId}$`));
          const clonedNote = await view.reply('NOTE', clonedAt);
          expect((clonedNote.data.note as { id: string }).id).toBe(cloneId);
        });

        await test.step('Then live incremental events replay both before and after route B NOTE', async () => {
          const fixture = await recorder.write(path.join(lifecycleFixtureDirectory(), 'association.json'));
          const note = fixture.records.find(
            record =>
              record.kind === 'websocket' &&
              record.websocket.direction === 'receive' &&
              (JSON.parse(record.websocket.payloadText!) as { op: string; data: { note?: { id: string } } }).data?.note
                ?.id === b &&
              (JSON.parse(record.websocket.payloadText!) as { op: string }).op === 'NOTE'
          )!;
          const events = fixture.records.filter(
            record =>
              record.kind === 'websocket' &&
              record.websocket.direction === 'receive' &&
              ['PARAGRAPH_ADDED', 'PARAGRAPH_MOVED', 'PARAGRAPH_REMOVED', 'NOTE_UPDATED'].includes(
                (JSON.parse(record.websocket.payloadText!) as { op: string }).op
              )
          );
          expect(events.length).toBeGreaterThanOrEqual(4);
          const normal = await replayLifecycleTrace(browser, fixture);
          const reordered = await replayLifecycleTrace(browser, fixture, [
            { sequence: note.sequence, afterSequence: events.at(-1)!.sequence }
          ]);
          const matchingNote = (frame: Record<string, unknown>) =>
            frame.op === 'NOTE' && (frame.data as { note?: { id: string } }).note?.id === b;
          const eventOperations = ['PARAGRAPH_ADDED', 'PARAGRAPH_REMOVED', 'PARAGRAPH_MOVED', 'NOTE_UPDATED'];
          const normalFrames = normal.get('viewer-a')!;
          const reorderedFrames = reordered.get('viewer-a')!;

          for (const operation of eventOperations) {
            const normalEvent = normalFrames.findIndex(frame => frame.op === operation);
            const reorderedEvent = reorderedFrames.findIndex(frame => frame.op === operation);
            expect(normalEvent).toBeGreaterThan(normalFrames.findIndex(matchingNote));
            expect(reorderedEvent).toBeLessThan(reorderedFrames.findIndex(matchingNote));
            expect(reorderedFrames[reorderedEvent]).toEqual(normalFrames[normalEvent]);
          }
        });
      } finally {
        await cleanUpLifecycleCapture(page, recorder, notes);
      }
    }
  );
});
