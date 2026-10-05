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
import { createLifecycleReplay, validateLifecycleFixture } from '../fixture.mjs';
import { createConnectionRecord, createWebSocketRecord, createLifecycleFixture } from './helpers/fixture-records.mjs';

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
