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
import test from 'node:test';
import { createLifecycleReplay } from '../fixture.mjs';
import { request } from '../../transport/doubles.mjs';
import { createConnectionRecord, createWebSocketRecord, createLifecycleFixture } from './helpers/fixture-records.mjs';
import { installReplaySession } from './helpers/replay-session.mjs';

test('socket messages arriving after disposal preserve the original replay failure', async () => {
  const replay = createLifecycleReplay(
    createLifecycleFixture([
      createConnectionRecord('a', 'a1'),
      createWebSocketRecord('a', 'a1', 'send', { op: 'GET_NOTE' })
    ])
  );
  const viewer = await installReplaySession(replay, 'a');
  viewer.connect();
  const failure = new Error('Consumer rejected the captured message');
  replay.dispose(failure);

  assert.doesNotThrow(() => viewer.send('{"op":"GET_NOTE"}'));
  assert.throws(
    () => replay.assertComplete(),
    error => error === failure
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
  const viewer = await installReplaySession(replay, 'a');

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
  const viewer = await installReplaySession(replay, 'a');
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
  const a = await installReplaySession(replay, 'a');

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
  const a = await installReplaySession(replay, 'a');
  const b = await installReplaySession(replay, 'b');

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
  const viewer = await installReplaySession(replay, 'a');
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
  const viewer = await installReplaySession(replay, 'a');

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
  const viewer = await installReplaySession(replay, 'b');
  viewer.connect();

  const waiting = replay.waitForPosition(2);
  replay.dispose();

  await assert.rejects(waiting, /disposed/);
  assert.equal(viewer.closed.length, 1);
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
