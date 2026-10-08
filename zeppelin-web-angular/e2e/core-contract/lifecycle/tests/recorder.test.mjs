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
import test from 'node:test';
import { createLifecycleRecorder, validateLifecycleFixture } from '../fixture.mjs';
import { fixtureMetadata, request } from '../../transport/doubles.mjs';

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
