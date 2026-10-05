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
import { BehaviorSubject, Observable } from 'rxjs';
import { createLifecycleDeliveryScheduler } from './delivery-scheduler.mjs';
import { createLifecycleRecorder, createLifecycleReplay, validateLifecycleFixture } from './fixture.mjs';
import { fixtureMetadata, request } from '../transport/doubles.mjs';

const createConnectionRecord = (sessionId, connectionId, event = 'open') => ({
  kind: 'connection',
  event,
  sessionId,
  connectionId
});

const createWebSocketRecord = (sessionId, connectionId, direction, payload) => ({
  kind: 'websocket',
  sessionId,
  connectionId,
  websocket: { direction, payloadText: JSON.stringify(payload) }
});

const createLifecycleFixture = records => ({
  version: 2,
  sessions: [{ id: 'a' }, { id: 'b' }],
  metadata: fixtureMetadata(),
  records: records.map((record, index) => ({ ...record, sequence: index + 1 }))
});

const installReplaySessionDouble = async (replay, sessionId) => {
  const session = { received: [], closed: [] };
  await replay.install(
    {
      route: async (_pattern, handler) => {
        session.rest = handler;
      },
      routeWebSocket: async (_pattern, handler) => {
        session.connect = () => {
          const socket = {
            send: payload => session.received.push(JSON.parse(payload)),
            close: options => session.closed.push(options),
            onMessage: callback => {
              session.send = callback;
            }
          };
          handler(socket);
        };
      }
    },
    sessionId
  );
  return session;
};

test('socket messages arriving after disposal preserve the original replay failure', async () => {
  const replay = createLifecycleReplay(
    createLifecycleFixture([
      createConnectionRecord('a', 'a1'),
      createWebSocketRecord('a', 'a1', 'send', { op: 'GET_NOTE' })
    ])
  );
  const viewer = await installReplaySessionDouble(replay, 'a');
  viewer.connect();
  const failure = new Error('Consumer rejected the captured message');
  replay.dispose(failure);

  assert.doesNotThrow(() => viewer.send('{"op":"GET_NOTE"}'));
  assert.throws(
    () => replay.assertComplete(),
    error => error === failure
  );
});

test('v2 rejects stale schemas, sequence gaps, session drift and frames after close', () => {
  const valid = createLifecycleFixture([
    createConnectionRecord('a', 'a1'),
    createWebSocketRecord('a', 'a1', 'receive', { op: 'NOTE' })
  ]);
  assert.deepEqual(validateLifecycleFixture(valid), []);
  assert.match(validateLifecycleFixture({ ...valid, version: 1 }).join(), /Unsupported/);

  const missing = JSON.parse(JSON.stringify(valid));
  missing.records[1].sequence = 3;
  assert.match(validateLifecycleFixture(missing).join(), /ordering or records lost/);
  assert.match(
    validateLifecycleFixture(
      createLifecycleFixture([createConnectionRecord('a', 'a1'), createWebSocketRecord('b', 'a1', 'receive', {})])
    ).join(),
    /inactive/
  );
  assert.match(
    validateLifecycleFixture(
      createLifecycleFixture([
        createConnectionRecord('a', 'a1'),
        createConnectionRecord('a', 'a1', 'close'),
        createWebSocketRecord('a', 'a1', 'receive', {})
      ])
    ).join(),
    /inactive/
  );
});

test('lifecycle validation rejects missing or invalid operations before replay', () => {
  for (const op of [undefined, null, '', 42, {}]) {
    const capture = createLifecycleFixture([
      createConnectionRecord('a', 'a1'),
      createWebSocketRecord('a', 'a1', 'receive', { op, data: { note: { id: 'n' } } })
    ]);
    assert.match(validateLifecycleFixture(capture).join('\n'), /envelope op must be a non-empty string/);
    assert.throws(() => createLifecycleReplay(capture), /envelope op must be a non-empty string/);
  }

  const futureOperation = createLifecycleFixture([
    createConnectionRecord('a', 'a1'),
    createWebSocketRecord('a', 'a1', 'receive', { op: 'FUTURE_OPERATION' })
  ]);
  assert.deepEqual(validateLifecycleFixture(futureOperation), []);
});

test('identical captured msgIds retain equality across independent viewers', async () => {
  const replay = createLifecycleReplay(
    createLifecycleFixture([
      createConnectionRecord('a', 'a1'),
      createWebSocketRecord('a', 'a1', 'send', { op: 'INSERT_PARAGRAPH', msgId: '<msgId:1>' }),
      createConnectionRecord('b', 'b1'),
      createWebSocketRecord('b', 'b1', 'send', { op: 'INSERT_PARAGRAPH', msgId: '<msgId:1>' }),
      createWebSocketRecord('b', 'b1', 'receive', { op: 'PARAGRAPH_ADDED', msgId: '<msgId:1>' }),
      createWebSocketRecord('a', 'a1', 'receive', { op: 'PARAGRAPH_ADDED', msgId: '<msgId:1>' })
    ])
  );
  const a = await installReplaySessionDouble(replay, 'a');
  const b = await installReplaySessionDouble(replay, 'b');

  a.connect();
  a.send(JSON.stringify({ op: 'INSERT_PARAGRAPH', msgId: 'live-a' }));
  b.connect();
  b.send(JSON.stringify({ op: 'INSERT_PARAGRAPH', msgId: 'live-a' }));
  assert.deepEqual(a.received, [{ op: 'PARAGRAPH_ADDED', msgId: 'live-a' }]);
  assert.deepEqual(b.received, [{ op: 'PARAGRAPH_ADDED', msgId: 'live-a' }]);
  replay.assertComplete();
});

test('validation rejects missing metadata, erased correlation and lost REST request bodies', () => {
  const source = createLifecycleFixture([createConnectionRecord('a', 'a1')]);
  delete source.metadata;
  assert.match(validateLifecycleFixture(source).join(), /metadata/);

  assert.match(
    validateLifecycleFixture(
      createLifecycleFixture([
        createConnectionRecord('a', 'a1'),
        createWebSocketRecord('a', 'a1', 'send', { op: 'GET_NOTE', msgId: '<msgId>' })
      ])
    ).join(),
    /msgId/
  );

  const shape = { method: 'POST', url: '/api/notebook/n/paragraph', headers: {} };
  assert.match(
    validateLifecycleFixture(
      createLifecycleFixture([
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
    createLifecycleFixture([
      createConnectionRecord('a', 'a1'),
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
  const viewer = await installReplaySessionDouble(replay, 'a');

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
    createLifecycleFixture([
      createConnectionRecord('a', 'a1'),
      { kind: 'rest', sessionId: 'a', requestId: 'r', rest: { direction: 'request', request: shape } },
      {
        kind: 'rest',
        sessionId: 'a',
        requestId: 'r',
        rest: { direction: 'response', request: shape, status: 200, headers: {}, bodyRaw: '' }
      },
      createWebSocketRecord('a', 'a1', 'send', { op: 'GET_NOTE' })
    ])
  );
  const viewer = await installReplaySessionDouble(replay, 'a');
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
    createLifecycleFixture([
      createConnectionRecord('a', 'a1'),
      { kind: 'context', sessionId: 'a', context },
      createWebSocketRecord('a', 'a1', 'send', {
        op: 'NOTE_REVISION',
        data: { noteId: 'note-a', revisionId: 'rev-a' }
      }),
      createConnectionRecord('a', 'a1', 'close'),
      createConnectionRecord('a', 'a2'),
      createWebSocketRecord('a', 'a2', 'send', {
        op: 'NOTE_REVISION',
        data: { noteId: 'note-a', revisionId: 'rev-a' }
      }),
      createWebSocketRecord('a', 'a2', 'receive', { op: 'NOTE_REVISION', data: { note: { id: 'note-a' } } })
    ])
  );
  const a = await installReplaySessionDouble(replay, 'a');

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
  const source = createLifecycleFixture([
    createConnectionRecord('a', 'a1'),
    createWebSocketRecord('a', 'a1', 'receive', { op: 'PATCH_PARAGRAPH' }),
    createWebSocketRecord('a', 'a1', 'receive', { op: 'NOTE_UPDATED' }),
    createWebSocketRecord('a', 'a1', 'receive', { op: 'NOTE' })
  ]);
  const original = JSON.parse(JSON.stringify(source));
  const replay = createLifecycleReplay(source, [
    { sequence: 2, copies: 2, afterSequence: 4 },
    { sequence: 3, copies: 0 }
  ]);
  const a = await installReplaySessionDouble(replay, 'a');

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
  const replay = createLifecycleReplay(
    createLifecycleFixture([
      createConnectionRecord('a', 'a1'),
      createWebSocketRecord('a', 'a1', 'receive', { op: 'NOTE' })
    ]),
    [{ sequence: 2, delayMs: 10 }]
  );
  const a = await installReplaySessionDouble(replay, 'a');
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
    createLifecycleFixture([
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
  const a = await installReplaySessionDouble(replay, 'a');
  const b = await installReplaySessionDouble(replay, 'b');

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

    for (const directory of ['notebook', 'search', 'recovery', 'logs', 'pid']) {
      assert.ok(environment.directories[directory]);
    }
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
  assert.throws(() => recorder.droppedSend('viewer-a', '{}'), /after stop/);
});

test('runtime request IDs cannot alias another viewer operation received on the same session', async () => {
  const replay = createLifecycleReplay(
    createLifecycleFixture([
      createConnectionRecord('a', 'a1'),
      createWebSocketRecord('a', 'a1', 'send', { op: 'GET_NOTE', msgId: 'local-id' }),
      createWebSocketRecord('a', 'a1', 'receive', { op: 'PARAGRAPH', msgId: 'remote-id' })
    ])
  );
  const viewer = await installReplaySessionDouble(replay, 'a');
  viewer.connect();

  assert.throws(() => viewer.send('{"op":"GET_NOTE","msgId":"remote-id"}'), /correlation mismatch/);
  assert.throws(() => replay.assertComplete(), /correlation mismatch/);
  replay.dispose();
});

test('deferred frames cannot be delivered into the replacement socket generation', async () => {
  const replay = createLifecycleReplay(
    createLifecycleFixture([
      createConnectionRecord('a', 'a1'),
      createWebSocketRecord('a', 'a1', 'receive', { op: 'NOTE' }),
      createConnectionRecord('a', 'a1', 'close'),
      createConnectionRecord('a', 'a2'),
      createWebSocketRecord('a', 'a2', 'send', { op: 'GET_NOTE' }),
      createWebSocketRecord('a', 'a2', 'receive', { op: 'NOTE' })
    ]),
    [{ sequence: 2, afterSequence: 6 }]
  );
  const viewer = await installReplaySessionDouble(replay, 'a');
  viewer.connect();
  viewer.connect();
  viewer.send('{"op":"GET_NOTE"}');

  assert.throws(() => replay.assertComplete(), /closed connection/);
  assert.equal(viewer.received.length, 1);
  replay.dispose();
});

test('sender correlation is preserved across broadcast recipients and unrelated local sends', async () => {
  const replay = createLifecycleReplay(
    createLifecycleFixture([
      createConnectionRecord('a', 'a1'),
      createConnectionRecord('b', 'b1'),
      createWebSocketRecord('b', 'b1', 'send', { op: 'GET_NOTE', msgId: 'b-local' }),
      createWebSocketRecord('b', 'b1', 'receive', { op: 'NOTE', msgId: 'b-local' }),
      createWebSocketRecord('a', 'a1', 'send', { op: 'COMMIT_PARAGRAPH', msgId: 'a-commit' }),
      createWebSocketRecord('b', 'b1', 'receive', { op: 'PARAGRAPH', msgId: 'a-commit' }),
      createWebSocketRecord('a', 'a1', 'receive', { op: 'PARAGRAPH', msgId: 'a-commit' })
    ])
  );
  const a = await installReplaySessionDouble(replay, 'a');
  const b = await installReplaySessionDouble(replay, 'b');

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
  for (const envelope of envelopes) {
    socket.emit('framereceived', { payload: JSON.stringify(envelope) });
  }
  await recorder.stop();

  assert.deepEqual(recorder.clientDelivery('a', [envelopes[1], envelopes[2], envelopes[0]]), [3, 4, 2]);
  assert.throws(() => recorder.clientDelivery('a', envelopes.slice(1)), /lost upstream/);
  assert.throws(() => recorder.clientDelivery('a', [...envelopes, envelopes[0]]), /no matching/);
});

test('timer delivery failure rejects a parked REST request with the original cause', async () => {
  const shape = { method: 'GET', url: '/api/notebook/n', headers: {}, bodyRaw: '' };
  const replay = createLifecycleReplay(
    createLifecycleFixture([
      createConnectionRecord('a', 'a1'),
      createWebSocketRecord('a', 'a1', 'receive', { op: 'NOTE' }),
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
    createLifecycleFixture([
      createConnectionRecord('a', 'a1'),
      createConnectionRecord('b', 'b1'),
      createWebSocketRecord('a', 'a1', 'send', { op: 'COMMIT_PARAGRAPH', msgId: 'shared-id' }),
      createWebSocketRecord('b', 'b1', 'receive', { op: 'PARAGRAPH', msgId: 'shared-id' }),
      createWebSocketRecord('b', 'b1', 'send', { op: 'COMMIT_PARAGRAPH', msgId: 'shared-id' })
    ])
  );
  const a = await installReplaySessionDouble(replay, 'a');
  const b = await installReplaySessionDouble(replay, 'b');

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
  const replay = createLifecycleReplay(
    createLifecycleFixture([
      createConnectionRecord('a', 'a1'),
      createWebSocketRecord('a', 'a1', 'receive', { op: 'PARAGRAPH', msgId: 'early-id' }),
      createWebSocketRecord('a', 'a1', 'send', { op: 'GET_NOTE', msgId: 'early-id' })
    ])
  );
  const a = await installReplaySessionDouble(replay, 'a');

  a.connect();
  assert.equal(a.received[0].msgId, 'early-id');
  assert.throws(() => a.send(JSON.stringify({ op: 'GET_NOTE', msgId: 'changed-id' })), /correlation mismatch/);
  replay.dispose();
});

test('a captured null msgId cannot be converted into a correlated request', async () => {
  const replay = createLifecycleReplay(
    createLifecycleFixture([
      createConnectionRecord('a', 'a1'),
      createWebSocketRecord('a', 'a1', 'send', { op: 'GET_NOTE', msgId: null })
    ])
  );
  const a = await installReplaySessionDouble(replay, 'a');

  a.connect();
  assert.throws(() => a.send(JSON.stringify({ op: 'GET_NOTE', msgId: 'invented-id' })), /correlation mismatch/);
  replay.dispose();
});

test('fatal send during REST fulfillment terminates delivery and preserves the failure cause', async () => {
  const shape = { method: 'GET', url: '/api/notebook/n', headers: {}, bodyRaw: '' };
  const replay = createLifecycleReplay(
    createLifecycleFixture([
      createConnectionRecord('a', 'a1'),
      { kind: 'rest', sessionId: 'a', requestId: 'r', rest: { direction: 'request', request: shape } },
      {
        kind: 'rest',
        sessionId: 'a',
        requestId: 'r',
        rest: { direction: 'response', request: shape, status: 200, headers: {}, bodyRaw: '' }
      },
      createWebSocketRecord('a', 'a1', 'receive', { op: 'NOTE' })
    ])
  );
  const viewer = await installReplaySessionDouble(replay, 'a');
  viewer.connect();

  const failedResponse = viewer.rest(
    {
      fulfill: async () => {
        assert.throws(() => viewer.send('{"op":"GET_NOTE"}'), /send out of order/);
      }
    },
    request('GET', 'http://fixture.test/api/notebook/n', '', {})
  );

  await assert.rejects(failedResponse, /send out of order/);
  assert.deepEqual(viewer.received, []);
  assert.throws(() => replay.position(), /send out of order/);
  assert.throws(() => replay.isComplete(), /send out of order/);
  replay.dispose();
});

for (const succeeds of [true, false]) {
  test(`concurrent recorder stops wait for body reads and preserve ${succeeds ? 'completion' : 'failure'}`, async () => {
    const page = new EventEmitter();
    const recorder = createLifecycleRecorder(fixtureMetadata());
    recorder.install(page, 'a');

    let finishBody;
    const body = new Promise((resolve, reject) => {
      finishBody = succeeds ? resolve : reject;
    });
    const source = request('GET', 'http://fixture.test/api/notebook/n', '', {});
    source.response = async () => ({
      headers: () => ({ 'content-type': 'application/json' }),
      status: () => 200,
      text: () => body
    });

    page.emit('request', source);
    page.emit('requestfinished', source);

    const first = recorder.stop();
    const second = recorder.stop();
    assert.equal(first, second);
    assert.throws(() => recorder.clientDelivery('a', []), /stopped recorder/);

    const error = new Error('body read failed');
    finishBody(succeeds ? '{}' : error);

    if (succeeds) {
      await first;
      assert.deepEqual(recorder.clientDelivery('a', []), []);
    } else {
      await assert.rejects(first, candidate => candidate === error);
      await assert.rejects(second, candidate => candidate === error);
      assert.throws(() => recorder.clientDelivery('a', []), /stopped recorder/);
    }

    assert.equal(page.listenerCount('requestfinished'), 0);
    assert.equal(page.listenerCount('request'), 0);
  });
}

test('fault selection rejects coercible sequence IDs and diagnoses invalid schedule fields', () => {
  const source = createLifecycleFixture([
    createConnectionRecord('a', 'a1'),
    createWebSocketRecord('a', 'a1', 'receive', { op: 'NOTE' })
  ]);

  assert.throws(() => createLifecycleReplay(source, [{ sequence: '2', copies: 0 }]), /Fault sequence/);
  assert.throws(() => createLifecycleReplay(source, [{ sequence: 2, copies: 1.5 }]), /Fault copies/);
  assert.throws(() => createLifecycleReplay(source, [{ sequence: 2, afterSequence: 1 }]), /Fault afterSequence/);
  assert.throws(() => createLifecycleReplay(source, [{ sequence: 2, delayMs: Infinity }]), /Fault delayMs/);
  assert.throws(() => createLifecycleReplay(source, [{ sequence: 2, delayMs: 2_147_483_648 }]), /Fault delayMs/);

  createLifecycleReplay(source, [{ sequence: 2, delayMs: 2_147_483_647 }]).dispose();
  assert.throws(() => createLifecycleReplay(source, [{ sequence: 2 }, { sequence: 2 }]), /must be unique/);
});

test('stop waits for every observed body read after one fails and detaches event sources immediately', async () => {
  const page = new EventEmitter();
  const recorder = createLifecycleRecorder(fixtureMetadata());
  recorder.install(page, 'a');
  const finishes = [];
  for (const id of ['first', 'second']) {
    const body = new Promise((resolve, reject) => finishes.push({ resolve, reject }));
    const source = request('GET', `http://fixture.test/api/notebook/${id}`, '', {});
    source.response = async () => ({
      headers: () => ({ 'content-type': 'application/json' }),
      status: () => 200,
      text: () => body
    });
    page.emit('request', source);
    page.emit('requestfinished', source);
  }

  let settled = false;
  const stopping = recorder.stop();
  const cause = new Error('first body failed');
  const rejection = assert
    .rejects(stopping, error => error === cause)
    .then(() => {
      settled = true;
    });

  assert.equal(page.listenerCount('requestfinished'), 0);
  assert.equal(page.listenerCount('request'), 0);
  finishes[0].reject(cause);
  await new Promise(resolve => setImmediate(resolve));

  assert.equal(settled, false);
  finishes[1].resolve('{}');
  await rejection;
});

test('capture normalization errors reject stop with their original cause', async () => {
  const page = new EventEmitter();
  const recorder = createLifecycleRecorder(fixtureMetadata());
  recorder.install(page, 'a');

  const cause = new Error('request URL unavailable');
  page.emit('request', {
    url: () => {
      throw cause;
    }
  });
  await assert.rejects(recorder.stop(), error => error === cause);
  assert.equal(page.listenerCount('request'), 0);
});

test('all consecutive inputs remain admissible while a REST response is being fulfilled', async () => {
  const shape = { method: 'GET', url: '/api/notebook/n', headers: {}, bodyRaw: '' };
  const source = createLifecycleFixture([
    createConnectionRecord('a', 'a1'),
    { kind: 'rest', sessionId: 'a', requestId: 'r', rest: { direction: 'request', request: shape } },
    {
      kind: 'rest',
      sessionId: 'a',
      requestId: 'r',
      rest: { direction: 'response', request: shape, status: 200, headers: {}, bodyRaw: '' }
    },
    { kind: 'context', sessionId: 'a', context: { state: 'active', noteId: 'n', revisionId: null } },
    createWebSocketRecord('a', 'a1', 'send', { op: 'GET_NOTE' }),
    createWebSocketRecord('a', 'a1', 'send', { op: 'LIST_REVISION_HISTORY' }),
    createWebSocketRecord('a', 'a1', 'receive', { op: 'NOTE' })
  ]);
  const replay = createLifecycleReplay(source);
  const viewer = await installReplaySessionDouble(replay, 'a');

  viewer.connect();
  await viewer.rest(
    {
      fulfill: async () => {
        replay.context('a', source.records[3].context);
        viewer.send('{"op":"GET_NOTE"}');
        viewer.send('{"op":"LIST_REVISION_HISTORY"}');
        assert.equal(replay.position(), 6);
        assert.deepEqual(viewer.received, []);
      }
    },
    request('GET', 'http://fixture.test/api/notebook/n', '', {})
  );
  await replay.waitForComplete();

  assert.deepEqual(viewer.received, [{ op: 'NOTE' }]);
  replay.dispose();
});

test('dispose closes an accepted socket waiting behind an earlier connection input', async () => {
  const replay = createLifecycleReplay(
    createLifecycleFixture([createConnectionRecord('a', 'a1'), createConnectionRecord('b', 'b1')])
  );
  const viewer = await installReplaySessionDouble(replay, 'b');
  viewer.connect();

  const waiting = replay.waitForPosition(2);
  replay.dispose();

  await assert.rejects(waiting, /disposed/);
  assert.equal(viewer.closed.length, 1);
});

test('native delivery matching uses raw identity and closing a socket releases its listeners', async () => {
  const page = new EventEmitter();
  const socket = new EventEmitter();
  socket.url = () => 'ws://fixture.test/ws';
  const recorder = createLifecycleRecorder(fixtureMetadata());
  recorder.install(page, 'a');

  const envelope = { op: 'NOTE', msgId: 'capture:1', principal: 'private-user', data: { timestamp: 1740000000000 } };

  page.emit('websocket', socket);
  socket.emit('framereceived', { payload: JSON.stringify(envelope) });
  socket.emit('close');
  assert.equal(socket.listenerCount('framereceived'), 0);
  assert.equal(socket.listenerCount('close'), 0);
  assert.equal(socket.listenerCount('socketerror'), 0);
  await recorder.stop();

  assert.deepEqual(recorder.clientDelivery('a', [envelope]), [2]);
  assert.doesNotMatch(JSON.stringify(recorder.snapshot()), /private-user|capture:1/);
});

test('automatic delivery failure rejects completion with the original cause', async () => {
  const replay = createLifecycleReplay(
    createLifecycleFixture([
      createConnectionRecord('a', 'a1'),
      createWebSocketRecord('a', 'a1', 'receive', { op: 'NOTE' })
    ])
  );
  let connect;
  await replay.install(
    {
      route: async () => {},
      routeWebSocket: async (_pattern, handler) => {
        connect = handler;
      }
    },
    'a'
  );
  const cause = new Error('socket send failure');
  const completion = replay.waitForComplete();

  connect({
    onMessage: () => {},
    close: () => {},
    send: () => {
      throw cause;
    }
  });

  await assert.rejects(completion, error => error === cause);
  assert.throws(
    () => replay.position(),
    error => error === cause
  );
  replay.dispose();
});

test('a large delivery plan observes only the next release boundary', () => {
  const count = 4000;
  const records = Array.from({ length: count }, (_, index) => ({
    sequence: index + 1,
    kind: 'websocket',
    websocket: { direction: 'receive' }
  }));
  const positions = new BehaviorSubject(0);

  let active = 0;
  let maximum = 0;
  const consumed$ = new Observable(subscriber => {
    maximum = Math.max(maximum, ++active);
    const subscription = positions.subscribe(subscriber);
    return () => {
      active--;
      subscription.unsubscribe();
    };
  });
  let delivered = 0;
  const scheduler = createLifecycleDeliveryScheduler(
    { records },
    [],
    consumed$,
    () => {
      delivered++;
    },
    error => {
      throw error;
    }
  );

  for (let sequence = 1; sequence <= count; sequence++) {
    positions.next(sequence);
  }

  assert.equal(maximum, 1);
  assert.equal(active, 0);
  assert.equal(delivered, count);
  assert.equal(scheduler.hasPending(), false);
  scheduler.dispose();
});

test('recorder settles independent sessions while preserving their shared observation order', async () => {
  const recorder = createLifecycleRecorder(fixtureMetadata());
  const finishes = [];
  const pages = [];

  for (const sessionId of ['a', 'b']) {
    const page = new EventEmitter();
    pages.push(page);
    recorder.install(page, sessionId);
    const body = new Promise(resolve => finishes.push(resolve));
    const source = request('GET', 'http://fixture.test/api/notebook/n', '', {});
    source.response = async () => ({
      headers: () => ({ 'content-type': 'application/json' }),
      status: () => 200,
      text: () => body
    });
    page.emit('request', source);
    page.emit('requestfinished', source);
  }

  let settled = false;
  const stopping = recorder.stop().then(() => {
    settled = true;
  });
  for (const page of pages) assert.equal(page.listenerCount('requestfinished'), 0);
  finishes[1]('{"viewer":"b"}');
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(settled, false);
  finishes[0]('{"viewer":"a"}');
  await stopping;

  const capture = recorder.snapshot();
  assert.deepEqual(validateLifecycleFixture(capture), []);
  assert.deepEqual(capture.sessions, [{ id: 'a' }, { id: 'b' }]);
  assert.deepEqual(
    capture.records.map(record => [record.sequence, record.sessionId, record.requestId]),
    [
      [1, 'a', 'a:request:1'],
      [2, 'a', 'a:request:1'],
      [3, 'b', 'b:request:1'],
      [4, 'b', 'b:request:1']
    ]
  );
  assert.deepEqual(
    capture.records.filter(record => record.rest.direction === 'response').map(record => record.rest.bodyJson),
    [{ viewer: 'a' }, { viewer: 'b' }]
  );
});

test('dispose attempts every owned socket and releases ownership when close fails', async () => {
  const replay = createLifecycleReplay(
    createLifecycleFixture([createConnectionRecord('a', 'a1'), createConnectionRecord('b', 'b1')])
  );
  const cause = new Error('first socket close failed');
  const closed = [];
  for (const sessionId of ['a', 'b']) {
    await replay.install(
      {
        route: async () => {},
        routeWebSocket: async (_pattern, connect) =>
          connect({
            onMessage: () => {},
            close: () => {
              closed.push(sessionId);
              if (sessionId === 'a') throw cause;
            }
          })
      },
      sessionId
    );
  }
  assert.throws(
    () => replay.dispose(),
    error => error instanceof AggregateError && error.errors[0] === cause
  );
  assert.deepEqual(closed, ['a', 'b']);
  replay.dispose();
  assert.deepEqual(closed, ['a', 'b']);
});
