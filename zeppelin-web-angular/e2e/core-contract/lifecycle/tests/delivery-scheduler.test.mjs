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
import { BehaviorSubject, Observable } from 'rxjs';
import { createLifecycleDeliveryScheduler } from '../delivery-scheduler.mjs';
import { createLifecycleReplay } from '../fixture.mjs';
import { request } from '../../transport/doubles.mjs';
import { createConnectionRecord, createWebSocketRecord, createLifecycleFixture } from './helpers/fixture-records.mjs';
import { installReplaySession } from './helpers/replay-session.mjs';

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
  const a = await installReplaySession(replay, 'a');

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
  const a = await installReplaySession(replay, 'a');
  a.connect();
  assert.throws(() => replay.assertComplete(), /pending deliveries/);
  await new Promise(resolve => setTimeout(resolve, 25));
  assert.deepEqual(a.received, [{ op: 'NOTE' }]);
  replay.assertComplete();
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
  const viewer = await installReplaySession(replay, 'a');
  viewer.connect();
  viewer.connect();
  viewer.send('{"op":"GET_NOTE"}');

  assert.throws(() => replay.assertComplete(), /closed connection/);
  assert.equal(viewer.received.length, 1);
  replay.dispose();
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
