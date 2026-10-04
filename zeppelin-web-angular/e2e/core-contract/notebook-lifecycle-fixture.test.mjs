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
import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import {
  createLifecycleRecorder,
  createLifecycleReplay,
  validateLifecycleFixture
} from './notebook-lifecycle-fixture.mjs';
import { fixtureMetadata, request } from './fixture-doubles.mjs';

const open = (sessionId, connectionId) => ({ kind: 'connection', event: 'open', sessionId, connectionId });
const frame = (sessionId, connectionId, direction, payload) => ({
  kind: 'websocket',
  sessionId,
  connectionId,
  websocket: { direction, payloadText: JSON.stringify(payload) }
});
const fixture = records => ({
  version: 2,
  sessions: [{ id: 'a' }, { id: 'b' }],
  metadata: fixtureMetadata(),
  records: records.map((record, index) => ({ ...record, sequence: index + 1 }))
});
const harness = async (replay, sessionId) => {
  const result = { received: [], closed: [] };
  await replay.install(
    {
      route: async (_pattern, handler) => {
        result.rest = handler;
      },
      routeWebSocket: async (_pattern, handler) => {
        result.connect = () => {
          const socket = {
            send: payload => result.received.push(JSON.parse(payload)),
            close: options => result.closed.push(options),
            onMessage: callback => {
              result.send = callback;
            }
          };
          handler(socket);
        };
      }
    },
    sessionId
  );
  return result;
};

test('v2 rejects stale schemas, sequence gaps, session drift and frames after close', () => {
  const valid = fixture([open('a', 'a1'), frame('a', 'a1', 'receive', { op: 'NOTE' })]);
  assert.deepEqual(validateLifecycleFixture(valid), []);
  assert.match(validateLifecycleFixture({ ...valid, version: 1 }).join(), /Unsupported/);
  const missing = JSON.parse(JSON.stringify(valid));
  missing.records[1].sequence = 3;
  assert.match(validateLifecycleFixture(missing).join(), /ordering or records lost/);
  assert.match(
    validateLifecycleFixture(fixture([open('a', 'a1'), frame('b', 'a1', 'receive', {})])).join(),
    /inactive/
  );
  assert.match(
    validateLifecycleFixture(
      fixture([open('a', 'a1'), { ...open('a', 'a1'), event: 'close' }, frame('a', 'a1', 'receive', {})])
    ).join(),
    /inactive/
  );
});

test('identical captured msgIds retain equality across independent viewers', async () => {
  const replay = createLifecycleReplay(
    fixture([
      open('a', 'a1'),
      frame('a', 'a1', 'send', { op: 'INSERT_PARAGRAPH', msgId: '<msgId:1>' }),
      open('b', 'b1'),
      frame('b', 'b1', 'send', { op: 'INSERT_PARAGRAPH', msgId: '<msgId:1>' }),
      frame('b', 'b1', 'receive', { op: 'PARAGRAPH_ADDED', msgId: '<msgId:1>' }),
      frame('a', 'a1', 'receive', { op: 'PARAGRAPH_ADDED', msgId: '<msgId:1>' })
    ])
  );
  const a = await harness(replay, 'a');
  const b = await harness(replay, 'b');
  a.connect();
  a.send(JSON.stringify({ op: 'INSERT_PARAGRAPH', msgId: 'live-a' }));
  b.connect();
  b.send(JSON.stringify({ op: 'INSERT_PARAGRAPH', msgId: 'live-a' }));
  assert.deepEqual(a.received, [{ op: 'PARAGRAPH_ADDED', msgId: 'live-a' }]);
  assert.deepEqual(b.received, [{ op: 'PARAGRAPH_ADDED', msgId: 'live-a' }]);
  replay.assertComplete();
});

test('validation rejects missing metadata, erased correlation and lost REST request bodies', () => {
  const source = fixture([open('a', 'a1')]);
  delete source.metadata;
  assert.match(validateLifecycleFixture(source).join(), /metadata/);

  assert.match(
    validateLifecycleFixture(
      fixture([open('a', 'a1'), frame('a', 'a1', 'send', { op: 'GET_NOTE', msgId: '<msgId>' })])
    ).join(),
    /msgId/
  );

  const shape = { method: 'POST', url: '/api/notebook/n/paragraph', headers: {} };
  assert.match(
    validateLifecycleFixture(
      fixture([
        { kind: 'rest', sessionId: 'a', requestId: 'r', rest: { direction: 'request', request: shape } },
        {
          kind: 'rest',
          sessionId: 'a',
          requestId: 'r',
          rest: { direction: 'response', request: shape, status: 200, headers: {}, bodyRaw: '' }
        }
      ])
    ).join(),
    /preserve request shape/
  );
});

test('REST requests cannot arrive before a recorded socket or context barrier', async () => {
  const shape = { method: 'GET', url: '/api/notebook/n', headers: {}, bodyRaw: '' };
  const replay = createLifecycleReplay(
    fixture([
      open('a', 'a1'),
      { kind: 'context', sessionId: 'a', context: { state: 'active', noteId: 'n', revisionId: null } },
      { kind: 'rest', sessionId: 'a', requestId: 'r', rest: { direction: 'request', request: shape } },
      {
        kind: 'rest',
        sessionId: 'a',
        requestId: 'r',
        rest: { direction: 'response', request: shape, status: 200, headers: {}, bodyRaw: '' }
      }
    ])
  );
  const viewer = await harness(replay, 'a');
  viewer.connect();

  await assert.rejects(
    viewer.rest({ fulfill: async () => {} }, request('GET', 'http://fixture.test/api/notebook/n', '', {})),
    /Unexpected lifecycle REST/
  );
  replay.dispose();
});

test('a browser can send its next frame while REST fulfillment is still in flight', async () => {
  const shape = { method: 'GET', url: '/api/notebook/n', headers: {}, bodyRaw: '' };
  const replay = createLifecycleReplay(
    fixture([
      open('a', 'a1'),
      { kind: 'rest', sessionId: 'a', requestId: 'r', rest: { direction: 'request', request: shape } },
      {
        kind: 'rest',
        sessionId: 'a',
        requestId: 'r',
        rest: { direction: 'response', request: shape, status: 200, headers: {}, bodyRaw: '' }
      },
      frame('a', 'a1', 'send', { op: 'GET_NOTE' })
    ])
  );
  const viewer = await harness(replay, 'a');
  viewer.connect();

  await viewer.rest(
    {
      fulfill: async () => {
        viewer.send('{"op":"GET_NOTE"}');
        assert.throws(() => replay.assertComplete(), /pending deliveries/);
      }
    },
    request('GET', 'http://fixture.test/api/notebook/n', '', {})
  );

  replay.assertComplete();
  replay.dispose();
});

test('reconnect keeps raw requests and requires the recorded route context', async () => {
  const context = { state: 'active', noteId: 'note-a', revisionId: 'rev-a' };
  const replay = createLifecycleReplay(
    fixture([
      open('a', 'a1'),
      { kind: 'context', sessionId: 'a', context },
      frame('a', 'a1', 'send', { op: 'NOTE_REVISION', data: { noteId: 'note-a', revisionId: 'rev-a' } }),
      { ...open('a', 'a1'), event: 'close' },
      open('a', 'a2'),
      frame('a', 'a2', 'send', { op: 'NOTE_REVISION', data: { noteId: 'note-a', revisionId: 'rev-a' } }),
      frame('a', 'a2', 'receive', { op: 'NOTE_REVISION', data: { note: { id: 'note-a' } } })
    ])
  );
  const a = await harness(replay, 'a');
  a.connect();
  assert.throws(() => replay.assertComplete(), /unconsumed/);
  replay.context('a', context);
  a.send(JSON.stringify({ op: 'NOTE_REVISION', data: { noteId: 'note-a', revisionId: 'rev-a' } }));
  assert.equal(a.closed[0].code, 1012);
  a.connect();
  a.send(JSON.stringify({ op: 'NOTE_REVISION', data: { noteId: 'note-a', revisionId: 'rev-a' } }));
  assert.deepEqual(a.received[0].data, { note: { id: 'note-a' } });
  replay.assertComplete();
});

test('fault plan drops, duplicates and reorders receive frames without rewriting the capture', async () => {
  const source = fixture([
    open('a', 'a1'),
    frame('a', 'a1', 'receive', { op: 'PATCH_PARAGRAPH' }),
    frame('a', 'a1', 'receive', { op: 'NOTE_UPDATED' }),
    frame('a', 'a1', 'receive', { op: 'NOTE' })
  ]);
  const original = JSON.parse(JSON.stringify(source));
  const replay = createLifecycleReplay(source, [
    { sequence: 2, copies: 2, afterSequence: 4 },
    { sequence: 3, copies: 0 }
  ]);
  const a = await harness(replay, 'a');
  a.connect();
  assert.deepEqual(
    a.received.map(value => value.op),
    ['NOTE', 'PATCH_PARAGRAPH', 'PATCH_PARAGRAPH']
  );
  assert.deepEqual(source, original);
  replay.assertComplete();
  assert.throws(() => createLifecycleReplay(source, [{ sequence: 1, copies: 0 }]), /receive frame/);
});

test('delayed deliveries cannot pass completion until they settle', async () => {
  const replay = createLifecycleReplay(fixture([open('a', 'a1'), frame('a', 'a1', 'receive', { op: 'NOTE' })]), [
    { sequence: 2, delayMs: 10 }
  ]);
  const a = await harness(replay, 'a');
  a.connect();
  assert.throws(() => replay.assertComplete(), /pending deliveries/);
  await new Promise(resolve => setTimeout(resolve, 25));
  assert.deepEqual(a.received, [{ op: 'NOTE' }]);
  replay.assertComplete();
  replay.dispose();
});

test('REST requests belong to a session and retain their observed response order', async () => {
  const shape = { method: 'GET', url: '/api/notebook/n', headers: {}, bodyRaw: '' };
  const replay = createLifecycleReplay(
    fixture([
      { kind: 'rest', sessionId: 'a', requestId: 'a-r', rest: { direction: 'request', request: shape } },
      { kind: 'rest', sessionId: 'b', requestId: 'b-r', rest: { direction: 'request', request: shape } },
      {
        kind: 'rest',
        sessionId: 'b',
        requestId: 'b-r',
        rest: { direction: 'response', request: shape, headers: {}, status: 200, bodyJson: { user: 'b' } }
      },
      {
        kind: 'rest',
        sessionId: 'a',
        requestId: 'a-r',
        rest: { direction: 'response', request: shape, headers: {}, status: 200, bodyJson: { user: 'a' } }
      }
    ])
  );
  const a = await harness(replay, 'a');
  const b = await harness(replay, 'b');
  const order = [];
  const rb = b.rest(
    { fulfill: async () => order.push('b') },
    request('GET', 'http://fixture.test/api/notebook/n', '', {})
  );
  const ra = a.rest(
    { fulfill: async () => order.push('a') },
    request('GET', 'http://fixture.test/api/notebook/n', '', {})
  );
  await Promise.all([ra, rb]);
  assert.deepEqual(order, ['b', 'a']);
  replay.assertComplete();
});

test('capture preserves reconnect generations and redacts identities without inventing noteId', async () => {
  const page = new EventEmitter();
  const recorder = createLifecycleRecorder(fixtureMetadata());
  recorder.install(page, 'viewer-a');
  const socket = () => Object.assign(new EventEmitter(), { url: () => 'ws://fixture.test/ws' });
  const first = socket();
  page.emit('websocket', first);
  recorder.context('viewer-a', { state: 'active', noteId: 'n', revisionId: null });
  first.emit('framereceived', { payload: '{"op":"NOTE_UPDATED","principal":"private-user","data":{"name":"n"}}' });
  first.emit('close');
  page.emit('websocket', socket());
  await recorder.stop();
  const captured = recorder.snapshot();
  assert.deepEqual(validateLifecycleFixture(captured), []);
  assert.deepEqual(
    captured.records.filter(value => value.kind === 'connection').map(value => value.connectionId),
    ['viewer-a:socket:1', 'viewer-a:socket:1', 'viewer-a:socket:2']
  );
  assert.equal(JSON.stringify(captured).includes('private-user'), false);
  assert.deepEqual(JSON.parse(captured.records[2].websocket.payloadText).data, { name: 'n' });
});

const readCapture = file => JSON.parse(readFileSync(path.resolve('e2e/fixtures/notebook-lifecycle', file), 'utf8'));
const receivedEnvelopes = capture =>
  capture.records
    .filter(record => record.kind === 'websocket' && record.websocket.direction === 'receive')
    .map(record => JSON.parse(record.websocket.payloadText));

test('committed lifecycle inventory validates every capture and its Apache master provenance', () => {
  const manifest = readCapture('manifest.json');
  const requiredFiles = [
    'structural.json',
    'revision-reconnect.json',
    'association.json',
    'collaboration-anonymous.json',
    'collaboration-auth.json',
    'commit-request-loss.json',
    'commit-reply-loss.json'
  ];
  assert.deepEqual(manifest.fixtures.map(entry => entry.file).sort(), requiredFiles.sort());

  for (const entry of manifest.fixtures) {
    assert.equal(entry.status, 'supported', `${entry.file}: ${entry.reason ?? 'capture must pass before committing'}`);
    const capture = readCapture(entry.file);
    assert.deepEqual(validateLifecycleFixture(capture), [], entry.file);
    assert.equal(capture.metadata.source.repository, 'apache/zeppelin');
    assert.match(capture.metadata.source.commit, /^[a-f0-9]{40}$/);
    assert.match(capture.metadata.source.serverCommit, /^[a-f0-9]{7,40}$/);
    assert.ok(capture.metadata.source.commit.startsWith(capture.metadata.source.serverCommit));
    const environment = capture.metadata.environment;
    assert.equal(environment.authentication, entry.file === 'collaboration-auth.json' ? 'auth' : 'anonymous');
    assert.match(environment.paragraphStatusProgress, /^(true|false)$/);
    assert.equal(environment.browser.name, 'chromium');
    assert.ok(environment.browser.version);
    assert.ok(new URL(environment.origin).port);
    assert.ok(Number.isInteger(environment.serverPort));
    assert.equal(environment.interpreterExecution, 'none');
    for (const directory of ['notebook', 'search', 'recovery', 'logs', 'pid'])
      assert.ok(environment.directories[directory]);
    assert.ok(environment.serverPidFile);
  }
});

test('structural captures keep REST full-note authority separate from granular WebSocket authority', () => {
  const capture = readCapture('structural.json');
  assert.deepEqual(capture.metadata.authoritativeInputs[0].frames, ['NOTE']);
  const operations = new Set(
    capture.records
      .filter(record => record.kind === 'websocket')
      .map(record => JSON.parse(record.websocket.payloadText).op)
  );

  for (const operation of [
    'INSERT_PARAGRAPH',
    'MOVE_PARAGRAPH',
    'COPY_PARAGRAPH',
    'PARAGRAPH_REMOVE',
    'COMMIT_PARAGRAPH',
    'PARAGRAPH_ADDED',
    'PARAGRAPH_MOVED',
    'PARAGRAPH_REMOVED'
  ]) {
    assert.ok(operations.has(operation), operation);
  }

  for (const method of ['POST', 'DELETE']) {
    assert.ok(
      capture.records.some(
        record =>
          record.kind === 'rest' &&
          record.rest.direction === 'request' &&
          record.rest.request.method === method &&
          record.rest.request.url.includes('/paragraph')
      )
    );
  }

  const commit = capture.records.find(
    record =>
      record.kind === 'websocket' &&
      record.websocket.direction === 'send' &&
      JSON.parse(record.websocket.payloadText).op === 'COMMIT_PARAGRAPH'
  );
  const envelope = JSON.parse(commit.websocket.payloadText);
  assert.ok(receivedEnvelopes(capture).some(reply => reply.op === 'PARAGRAPH' && reply.msgId === envelope.msgId));
});

test('missing commit acknowledgement requires canonical refetch to distinguish request loss from reply loss', () => {
  const lostRequest = readCapture('commit-request-loss.json');
  const lostReply = readCapture('commit-reply-loss.json');
  assert.equal(lostRequest.metadata.commitLoss.localDraft, lostReply.metadata.commitLoss.localDraft);
  assert.notEqual(lostRequest.metadata.commitLoss.canonicalText, lostReply.metadata.commitLoss.canonicalText);

  for (const capture of [lostRequest, lostReply]) {
    const commit = capture.records.find(
      record =>
        record.kind === 'websocket' &&
        record.websocket.direction === 'send' &&
        JSON.parse(record.websocket.payloadText).op === 'COMMIT_PARAGRAPH'
    );
    const commitId = JSON.parse(commit.websocket.payloadText).msgId;
    const envelopes = receivedEnvelopes({
      ...capture,
      records: capture.records.filter(
        record =>
          !capture.metadata.commitLoss.faults.some(fault => fault.sequence === record.sequence && fault.copies === 0)
      )
    });
    assert.equal(
      envelopes.some(frame => frame.op === 'PARAGRAPH' && frame.msgId === commitId),
      false
    );
    const finalNote = envelopes.findLast(frame => frame.op === 'NOTE').data.note;
    assert.equal(finalNote.paragraphs[0].text, capture.metadata.commitLoss.canonicalText);
    assert.ok(
      capture.records.some(
        record =>
          record.kind === 'rest' &&
          record.rest.direction === 'response' &&
          record.rest.bodyJson?.body?.paragraphs?.[0]?.text === capture.metadata.commitLoss.canonicalText
      )
    );
  }
});

test('a dropped client command is recorded as an observation rather than a server receipt', async () => {
  const page = new EventEmitter();
  const recorder = createLifecycleRecorder(fixtureMetadata());
  recorder.install(page, 'viewer-a');
  const socket = Object.assign(new EventEmitter(), { url: () => 'ws://fixture.test/ws' });
  page.emit('websocket', socket);
  recorder.droppedSend('viewer-a', '{"op":"COMMIT_PARAGRAPH","msgId":"private-id","principal":"private-user"}');
  await recorder.stop();

  const capture = recorder.snapshot();
  assert.equal(capture.records[1].delivery, 'dropped-before-server');
  assert.equal(JSON.stringify(capture).includes('private-user'), false);
  assert.equal(JSON.stringify(capture).includes('private-id'), false);
  assert.throws(() => recorder.droppedSend('viewer-a', '{}'), /active captured socket/);
});

test('runtime request IDs cannot alias another viewer operation received on the same session', async () => {
  const replay = createLifecycleReplay(
    fixture([
      open('a', 'a1'),
      frame('a', 'a1', 'send', { op: 'GET_NOTE', msgId: 'local-id' }),
      frame('a', 'a1', 'receive', { op: 'PARAGRAPH', msgId: 'remote-id' })
    ])
  );
  const viewer = await harness(replay, 'a');
  viewer.connect();

  assert.throws(() => viewer.send('{"op":"GET_NOTE","msgId":"remote-id"}'), /correlation mismatch/);
  assert.throws(() => replay.assertComplete(), /correlation mismatch/);
  replay.dispose();
});

test('deferred frames cannot be delivered into the replacement socket generation', async () => {
  const replay = createLifecycleReplay(
    fixture([
      open('a', 'a1'),
      frame('a', 'a1', 'receive', { op: 'NOTE' }),
      { ...open('a', 'a1'), event: 'close' },
      open('a', 'a2'),
      frame('a', 'a2', 'send', { op: 'GET_NOTE' }),
      frame('a', 'a2', 'receive', { op: 'NOTE' })
    ]),
    [{ sequence: 2, afterSequence: 6 }]
  );
  const viewer = await harness(replay, 'a');
  viewer.connect();
  viewer.connect();
  viewer.send('{"op":"GET_NOTE"}');

  assert.throws(() => replay.assertComplete(), /closed connection/);
  assert.equal(viewer.received.length, 1);
  replay.dispose();
});

test('sender correlation is preserved across broadcast recipients and unrelated local sends', async () => {
  const replay = createLifecycleReplay(
    fixture([
      open('a', 'a1'),
      open('b', 'b1'),
      frame('b', 'b1', 'send', { op: 'GET_NOTE', msgId: 'b-local' }),
      frame('b', 'b1', 'receive', { op: 'NOTE', msgId: 'b-local' }),
      frame('a', 'a1', 'send', { op: 'COMMIT_PARAGRAPH', msgId: 'a-commit' }),
      frame('b', 'b1', 'receive', { op: 'PARAGRAPH', msgId: 'a-commit' }),
      frame('a', 'a1', 'receive', { op: 'PARAGRAPH', msgId: 'a-commit' })
    ])
  );
  const a = await harness(replay, 'a');
  const b = await harness(replay, 'b');
  a.connect();
  b.connect();
  await new Promise(resolve => setImmediate(resolve));
  b.send(JSON.stringify({ op: 'GET_NOTE', msgId: 'live-b-local' }));
  await new Promise(resolve => setImmediate(resolve));
  a.send(JSON.stringify({ op: 'COMMIT_PARAGRAPH', msgId: 'live-a-commit' }));
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(a.received[0].msgId, 'live-a-commit');
  assert.deepEqual(
    b.received.map(envelope => envelope.msgId),
    ['live-b-local', 'live-a-commit']
  );
  replay.assertComplete();
  replay.dispose();
});

test('client delivery records actual reordered receive occurrences and rejects missing or invented frames', async () => {
  const page = new EventEmitter();
  const recorder = createLifecycleRecorder(fixtureMetadata());
  recorder.install(page, 'a');
  const socket = Object.assign(new EventEmitter(), { url: () => 'ws://fixture.test/ws' });
  page.emit('websocket', socket);
  const envelopes = [
    { op: 'NOTE', data: { note: { id: 'n' } } },
    { op: 'NOTE_UPDATED', data: {} },
    { op: 'NOTE_UPDATED', data: {} }
  ];
  for (const envelope of envelopes) socket.emit('framereceived', { payload: JSON.stringify(envelope) });
  await recorder.stop();
  assert.deepEqual(recorder.clientDelivery('a', [envelopes[1], envelopes[2], envelopes[0]]), [3, 4, 2]);
  assert.throws(() => recorder.clientDelivery('a', envelopes.slice(1)), /lost upstream/);
  assert.throws(() => recorder.clientDelivery('a', [...envelopes, envelopes[0]]), /no matching/);
});

test('timer delivery failure rejects a parked REST request with the original cause', async () => {
  const shape = { method: 'GET', url: '/api/notebook/n', headers: {}, bodyRaw: '' };
  const replay = createLifecycleReplay(
    fixture([
      open('a', 'a1'),
      frame('a', 'a1', 'receive', { op: 'NOTE' }),
      { kind: 'rest', sessionId: 'a', requestId: 'r', rest: { direction: 'request', request: shape } },
      { kind: 'context', sessionId: 'a', context: { state: 'active', noteId: 'n', revisionId: null } },
      {
        kind: 'rest',
        sessionId: 'a',
        requestId: 'r',
        rest: { direction: 'response', request: shape, status: 200, headers: {}, bodyJson: {} }
      }
    ]),
    [{ sequence: 2, delayMs: 10 }]
  );
  let connect, route;
  await replay.install(
    {
      route: async (_pattern, handler) => {
        route = handler;
      },
      routeWebSocket: async (_pattern, handler) => {
        connect = () =>
          handler({
            send: () => {
              throw new Error('route is closed');
            },
            close: () => {},
            onMessage: () => {}
          });
      }
    },
    'a'
  );
  connect();
  await new Promise(resolve => setImmediate(resolve));
  const pending = route({ fulfill: async () => {} }, request('GET', 'http://fixture.test/api/notebook/n', '', {}));
  await assert.rejects(pending, /route is closed/);
  assert.throws(() => replay.assertComplete(), /route is closed/);
  replay.dispose();
});

test('route delivery validation rejects missing occurrences, invalid release boundaries and undeclared reorders', () => {
  const captured = readCapture('association.json');
  assert.deepEqual(validateLifecycleFixture(captured), []);
  const missing = globalThis.structuredClone(captured);
  missing.metadata.routeTransition.deliveredSequences.pop();
  assert.match(validateLifecycleFixture(missing).join(), /every upstream receive/);
  const wrongBoundary = globalThis.structuredClone(captured);
  wrongBoundary.metadata.routeTransition.releaseAfterSequence = wrongBoundary.metadata.routeTransition.heldNoteSequence;
  assert.match(validateLifecycleFixture(wrongBoundary).join(), /release boundary/);
  const reordered = globalThis.structuredClone(captured);
  const sequences = reordered.metadata.routeTransition.deliveredSequences;
  [sequences[0], sequences[1]] = [sequences[1], sequences[0]];
  assert.match(validateLifecycleFixture(reordered).join(), /beyond the declared/);
});

test('a shared recorded msgId cannot be rebound to a different runtime ID by a later sender', async () => {
  const replay = createLifecycleReplay(
    fixture([
      open('a', 'a1'),
      open('b', 'b1'),
      frame('a', 'a1', 'send', { op: 'COMMIT_PARAGRAPH', msgId: 'shared-id' }),
      frame('b', 'b1', 'receive', { op: 'PARAGRAPH', msgId: 'shared-id' }),
      frame('b', 'b1', 'send', { op: 'COMMIT_PARAGRAPH', msgId: 'shared-id' })
    ])
  );
  const a = await harness(replay, 'a'),
    b = await harness(replay, 'b');
  a.connect();
  b.connect();
  await new Promise(resolve => setImmediate(resolve));
  a.send(JSON.stringify({ op: 'COMMIT_PARAGRAPH', msgId: 'runtime-a' }));
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(b.received[0].msgId, 'runtime-a');
  assert.throws(() => b.send(JSON.stringify({ op: 'COMMIT_PARAGRAPH', msgId: 'runtime-b' })), /correlation mismatch/);
  replay.dispose();
});

test('an ID delivered before its first send cannot subsequently be rewritten', async () => {
  const replay = createLifecycleReplay(fixture([
    open('a', 'a1'),
    frame('a', 'a1', 'receive', { op: 'PARAGRAPH', msgId: 'early-id' }),
    frame('a', 'a1', 'send', { op: 'GET_NOTE', msgId: 'early-id' })
  ]));
  const a = await harness(replay, 'a');
  a.connect();
  assert.equal(a.received[0].msgId, 'early-id');
  assert.throws(() => a.send(JSON.stringify({ op: 'GET_NOTE', msgId: 'changed-id' })), /correlation mismatch/);
  replay.dispose();
});

test('a captured null msgId cannot be converted into a correlated request', async () => {
  const replay = createLifecycleReplay(fixture([
    open('a', 'a1'), frame('a', 'a1', 'send', { op: 'GET_NOTE', msgId: null })
  ]));
  const a = await harness(replay, 'a');
  a.connect();
  assert.throws(() => a.send(JSON.stringify({ op: 'GET_NOTE', msgId: 'invented-id' })), /correlation mismatch/);
  replay.dispose();
});
