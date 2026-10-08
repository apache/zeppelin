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
import { createConnectionRecord, createWebSocketRecord, createLifecycleFixture } from './helpers/fixture-records.mjs';
import { installReplaySession } from './helpers/replay-session.mjs';

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
  const a = await installReplaySession(replay, 'a');
  const b = await installReplaySession(replay, 'b');

  a.connect();
  a.send(JSON.stringify({ op: 'INSERT_PARAGRAPH', msgId: 'live-a' }));
  b.connect();
  b.send(JSON.stringify({ op: 'INSERT_PARAGRAPH', msgId: 'live-a' }));
  assert.deepEqual(a.received, [{ op: 'PARAGRAPH_ADDED', msgId: 'live-a' }]);
  assert.deepEqual(b.received, [{ op: 'PARAGRAPH_ADDED', msgId: 'live-a' }]);
  replay.assertComplete();
});

test('runtime request IDs cannot alias another viewer operation received on the same session', async () => {
  const replay = createLifecycleReplay(
    createLifecycleFixture([
      createConnectionRecord('a', 'a1'),
      createWebSocketRecord('a', 'a1', 'send', { op: 'GET_NOTE', msgId: 'local-id' }),
      createWebSocketRecord('a', 'a1', 'receive', { op: 'PARAGRAPH', msgId: 'remote-id' })
    ])
  );
  const viewer = await installReplaySession(replay, 'a');
  viewer.connect();

  assert.throws(() => viewer.send('{"op":"GET_NOTE","msgId":"remote-id"}'), /correlation mismatch/);
  assert.throws(() => replay.assertComplete(), /correlation mismatch/);
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
  const a = await installReplaySession(replay, 'a');
  const b = await installReplaySession(replay, 'b');

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
  const a = await installReplaySession(replay, 'a');
  const b = await installReplaySession(replay, 'b');

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
  const a = await installReplaySession(replay, 'a');

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
  const a = await installReplaySession(replay, 'a');

  a.connect();
  assert.throws(() => a.send(JSON.stringify({ op: 'GET_NOTE', msgId: 'invented-id' })), /correlation mismatch/);
  replay.dispose();
});
