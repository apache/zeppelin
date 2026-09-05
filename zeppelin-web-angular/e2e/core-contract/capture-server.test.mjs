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
import { spawnSync } from 'node:child_process';
import { EventEmitter } from 'node:events';
import { existsSync, mkdtempSync, readFileSync } from 'node:fs';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';

import * as fixtureModule from './notebook-transport-fixture.mjs';
import {
  createNotebookTransportRecorder,
  createPlaywrightFixtureAdapter,
  fixtureVersion,
  isNotebookRestUrl,
  parseRestBody,
  validateFixture,
  webSocketPayloadMatches
} from './notebook-transport-fixture.mjs';

const script = path.resolve('e2e/core-contract/capture-server.sh');
const stub = path.resolve('e2e/core-contract/capture-stub-zeppelin.mjs');
const fixtureMetadata = () => ({
  coveredOperations: ['GET_NOTE'],
  knownExclusions: [],
  owner: 'zeppelin-web-angular',
  scenario: 'Notebook transport fixture test'
});

test('capture-server start and stop do not require a fixture arg', () => {
  const root = createRoot();

  start(root);
  stop(root);

  assert.equal(existsSync(path.join(root.root, '.zeppelin-capture-root')), true);
  assert.equal(existsSync(path.join(root.root, 'zeppelin.pid')), false);
});

test('capture-server writes anonymous and auth config in an isolated temp root', () => {
  const anonymous = createRoot();
  const auth = createRoot();

  start(anonymous);
  stop(anonymous);
  start(auth, { mode: 'auth' });
  stop(auth);

  assert.equal(existsSync(path.join(anonymous.root, 'conf/shiro.ini')), false);
  assert.equal(existsSync(path.join(auth.root, 'conf/shiro.ini')), true);
});

test('capture-server reports explicit port conflicts', async () => {
  const server = await listen();
  const root = createRoot();

  try {
    const result = run(['start', '--root', root.root, '--port', String(server.address().port)]);

    assert.equal(result.status, 1);
    assert.match(result.stderr, /port .* is already in use/);
  } finally {
    await close(server);
  }
});

test('recorder captures notebook REST and WebSocket browser events only', async () => {
  const page = new EventEmitter();
  const socket = new EventEmitter();
  socket.url = () => 'http://127.0.0.1:8080/ws';
  const recorder = createNotebookTransportRecorder(fixtureMetadata());

  recorder.install(page);
  page.emit('request', request('POST', 'http://127.0.0.1:8080/api/notebook/note-a', '{"msgId":"runtime"}'));
  page.emit('response', response(request('GET', 'http://127.0.0.1:8080/api/notebook/note-a'), 200, '{"id":"note-a"}'));
  page.emit('request', request('GET', 'http://127.0.0.1:8080/assets/app.js'));
  page.emit('websocket', socket);
  socket.emit('framesent', { payload: '{"op":"GET_NOTE","msgId":"runtime"}' });
  socket.emit('framereceived', { payload: '{"op":"NOTE","noteId":"note-a"}' });

  await recorder.stop();
  const fixture = recorder.snapshot();

  assert.deepEqual(validateFixture(fixture), []);
  assert.deepEqual(
    fixture.records.map(record => [
      record.sequence,
      record.kind,
      record.rest?.direction ?? record.websocket?.direction
    ]),
    [
      [1, 'rest', 'request'],
      [2, 'rest', 'response'],
      [3, 'websocket', 'send'],
      [4, 'websocket', 'receive']
    ]
  );
  assert.equal(fixture.records[0].rest.request.bodyJson.msgId, '<msgId>');
  assert.equal(fixture.records[1].rest.bodyJson.id, 'note-a');
  assert.deepEqual(fixture.metadata, fixtureMetadata());
});

test('recorder requires scenario ownership and coverage metadata before capture', () => {
  assert.throws(() => createNotebookTransportRecorder(), /metadata must be an object/);
  assert.throws(
    () => createNotebookTransportRecorder({ ...fixtureMetadata(), coveredOperations: [] }),
    /metadata.coveredOperations must be a non-empty string array/
  );
  assert.deepEqual(
    validateFixture({
      metadata: { ...fixtureMetadata(), scenario: '' },
      records: [wsRecord(1, 'send', '{}')],
      version: fixtureVersion
    }),
    ['metadata.scenario must be a non-empty string']
  );
  assert.deepEqual(validateFixture({ records: [wsRecord(1, 'send', '{}')], version: fixtureVersion }), [
    'metadata must be an object'
  ]);
});

test('recorder redacts sensitive headers and fields before writing fixture files', async () => {
  const root = createRoot();
  const page = new EventEmitter();
  const recorder = createNotebookTransportRecorder(fixtureMetadata());

  recorder.install(page);
  page.emit(
    'request',
    request('POST', 'http://127.0.0.1:8080/api/notebook', '{"ticket":"secret","id":"stable-id"}', {
      accept: 'application/json',
      authorization: 'Bearer secret',
      cookie: 'ticket=secret',
      'content-type': 'application/json'
    })
  );
  const written = await recorder.write(path.join(root.root, 'fixtures/notebook-transport.json'));
  const text = readFileSync(path.join(root.root, 'fixtures/notebook-transport.json'), 'utf8');

  assert.equal(text.includes('Bearer secret'), false);
  assert.equal(text.includes('ticket=secret'), false);
  assert.deepEqual(written.records[0].rest.request.headers, {
    accept: 'application/json',
    'content-type': 'application/json'
  });
  assert.deepEqual(written.records[0].rest.request.bodyJson, { id: 'stable-id', ticket: '<ticket>' });
});

test('recorder redacts WebSocket JSON payload secrets before writing fixture files', async () => {
  const root = createRoot();
  const page = new EventEmitter();
  const socket = new EventEmitter();
  socket.url = () => 'http://127.0.0.1:8080/ws';
  const recorder = createNotebookTransportRecorder(fixtureMetadata());

  recorder.install(page);
  page.emit('websocket', socket);
  socket.emit('framesent', {
    payload:
      '{"op":"GET_NOTE","id":"stable-id","noteId":"note-a","ticket":"secret-ticket","principal":"alice","msgId":"runtime"}'
  });

  await recorder.write(path.join(root.root, 'fixtures/notebook-transport.json'));
  const text = readFileSync(path.join(root.root, 'fixtures/notebook-transport.json'), 'utf8');
  const fixture = JSON.parse(text);

  assert.equal(text.includes('secret-ticket'), false);
  assert.equal(text.includes('alice'), false);
  assert.equal(text.includes('runtime'), false);
  assert.equal(fixture.records[0].websocket.payloadText.includes('"op":"GET_NOTE"'), true);
  assert.equal(fixture.records[0].websocket.payloadText.includes('"id":"stable-id"'), true);
  assert.equal(fixture.records[0].websocket.payloadText.includes('"noteId":"note-a"'), true);
});

test('recorder rejects binary WebSocket frames until binary redaction is defined', () => {
  const page = new EventEmitter();
  const socket = new EventEmitter();
  socket.url = () => 'http://127.0.0.1:8080/ws';
  const recorder = createNotebookTransportRecorder(fixtureMetadata());

  recorder.install(page);
  page.emit('websocket', socket);

  assert.throws(
    () => socket.emit('framesent', { payload: Buffer.from([0, 255, 1]) }),
    /Binary WebSocket frames cannot be captured/
  );
});

test('recorder normalizes sensitive and volatile REST URL query values before writing', async () => {
  const root = createRoot();
  const page = new EventEmitter();
  const recorder = createNotebookTransportRecorder(fixtureMetadata());

  recorder.install(page);
  page.emit(
    'request',
    request(
      'GET',
      'http://127.0.0.1:8080/api/notebook/note-a?ticket=secret-ticket&token=secret-token&msgId=runtime&view=stable'
    )
  );

  await recorder.write(path.join(root.root, 'fixtures/notebook-transport.json'));
  const text = readFileSync(path.join(root.root, 'fixtures/notebook-transport.json'), 'utf8');
  const fixture = JSON.parse(text);

  assert.equal(text.includes('secret-ticket'), false);
  assert.equal(text.includes('secret-token'), false);
  assert.equal(
    fixture.records[0].rest.request.url,
    '/api/notebook/note-a?ticket=%3Cticket%3E&token=%3Ctoken%3E&msgId=%3CmsgId%3E&view=stable'
  );
});

test('recorder redacts credential-shaped body and query fields before writing', async () => {
  const root = createRoot();
  const page = new EventEmitter();
  const recorder = createNotebookTransportRecorder(fixtureMetadata());

  recorder.install(page);
  page.emit(
    'request',
    request(
      'POST',
      'http://127.0.0.1:8080/api/notebook/note-a?apiKey=query-key&clientSecret=query-secret&view=stable',
      '{"apiKey":"body-key","credential":"body-credential","secret":"body-secret","id":"stable-id"}',
      { 'content-type': 'application/json' }
    )
  );

  await recorder.write(path.join(root.root, 'fixtures/notebook-transport.json'));
  const text = readFileSync(path.join(root.root, 'fixtures/notebook-transport.json'), 'utf8');
  const fixture = JSON.parse(text);

  for (const value of ['query-key', 'query-secret', 'body-key', 'body-credential', 'body-secret']) {
    assert.equal(text.includes(value), false);
  }
  assert.deepEqual(fixture.records[0].rest.request.bodyJson, {
    apiKey: '<apiKey>',
    credential: '<credential>',
    id: 'stable-id',
    secret: '<secret>'
  });
  assert.equal(
    fixture.records[0].rest.request.url,
    '/api/notebook/note-a?apiKey=%3CapiKey%3E&clientSecret=%3CclientSecret%3E&view=stable'
  );
});

test('replay applies the same query normalization and ignores JSON property order', async () => {
  const fulfilled = [];
  const adapter = fixtureModule.createFixtureReplayAdapter({
    records: [
      {
        kind: 'rest',
        sequence: 1,
        rest: {
          bodyJson: { id: 'note-a' },
          direction: 'response',
          headers: { 'content-type': 'application/json' },
          request: {
            bodyRaw: '',
            headers: { accept: 'application/json' },
            method: 'GET',
            url: '/api/notebook/note-a?ticket=%3Cticket%3E&msgId=%3CmsgId%3E'
          },
          status: 200
        }
      }
    ],
    version: fixtureVersion
  });

  await adapter.route(
    { fulfill: async value => fulfilled.push(value) },
    request('GET', 'http://127.0.0.1:8080/api/notebook/note-a?ticket=runtime-secret&msgId=runtime-id')
  );

  assert.equal(fulfilled.length, 1);
  assert.equal(
    webSocketPayloadMatches('{"op":"GET_NOTE","msgId":"<msgId>"}', '{"msgId":"runtime","op":"GET_NOTE"}'),
    true
  );
});

test('replay rejects REST requests when the recorded request body or safe headers differ', async () => {
  const adapter = fixtureModule.createFixtureReplayAdapter({
    records: [
      {
        kind: 'rest',
        sequence: 1,
        rest: {
          bodyJson: { id: 'note-a' },
          direction: 'response',
          headers: { 'content-type': 'application/json' },
          request: {
            bodyJson: { paragraphId: 'paragraph-a', text: 'print(1)' },
            headers: { accept: 'application/json', 'content-type': 'application/json' },
            method: 'POST',
            url: '/api/notebook/job/note-a/paragraph-a'
          },
          status: 200
        }
      }
    ],
    version: fixtureVersion
  });

  await assert.rejects(
    () =>
      adapter.route(
        { fulfill: async () => undefined },
        request(
          'POST',
          'http://127.0.0.1:8080/api/notebook/job/note-a/paragraph-a',
          '{"paragraphId":"paragraph-a","text":"print(2)"}',
          {
            accept: 'application/json',
            'content-type': 'application/json'
          }
        )
      ),
    /REST fixture request mismatch/
  );

  await assert.rejects(
    () =>
      adapter.route(
        { fulfill: async () => undefined },
        request(
          'POST',
          'http://127.0.0.1:8080/api/notebook/job/note-a/paragraph-a',
          '{"text":"print(1)","paragraphId":"paragraph-a"}',
          {
            accept: 'text/plain',
            'content-type': 'application/json'
          }
        )
      ),
    /REST fixture request mismatch/
  );
});

test('recorder fails closed when response body capture fails', async () => {
  const page = new EventEmitter();
  const recorder = createNotebookTransportRecorder(fixtureMetadata());

  recorder.install(page);
  page.emit(
    'response',
    response(request('GET', 'http://127.0.0.1:8080/api/notebook/note-a'), 200, async () => {
      throw new Error('body unavailable');
    })
  );

  await assert.rejects(() => recorder.stop(), /body unavailable/);
});

test('Playwright adapter replays WebSocket payloadBase64 as binary data', async () => {
  const calls = [];
  const page = {
    route: async () => undefined,
    routeWebSocket: async (_pattern, handler) => calls.push(handler)
  };
  await createPlaywrightFixtureAdapter({
    records: [
      {
        kind: 'websocket',
        sequence: 1,
        websocket: { direction: 'send', payloadText: 'client-ready' }
      },
      {
        kind: 'websocket',
        sequence: 2,
        websocket: { direction: 'receive', payloadBase64: Buffer.from([0, 255, 1, 2]).toString('base64') }
      }
    ],
    version: fixtureVersion
  }).install(page);

  const replies = [];
  const handlers = [];
  calls[0]({
    onMessage: handler => handlers.push(handler),
    send: message => replies.push(message)
  });
  handlers[0]('client-ready');

  assert.equal(Buffer.isBuffer(replies[0]), true);
  assert.deepEqual([...replies[0]], [0, 255, 1, 2]);
});

test('WebSocket binary payload matching compares bytes instead of UTF-8 replacement text', () => {
  assert.equal(webSocketPayloadMatches(Buffer.from([0xff]), Buffer.from([0xff])), true);
  assert.equal(webSocketPayloadMatches(Buffer.from([0xff]), Buffer.from([0xfe])), false);
  assert.equal(webSocketPayloadMatches(Buffer.from([0xff]), '\ufffd'), false);
});

test('recorder write waits for pending response body capture', async () => {
  const root = createRoot();
  const page = new EventEmitter();
  const recorder = createNotebookTransportRecorder(fixtureMetadata());
  let resolveBody;

  recorder.install(page);
  page.emit(
    'response',
    response(request('GET', 'http://127.0.0.1:8080/api/notebook/note-a'), 200, () => {
      return new Promise(resolve => {
        resolveBody = resolve;
      });
    })
  );
  const writePromise = recorder.write(path.join(root.root, 'fixtures/notebook-transport.json'));
  resolveBody('{"id":"note-a"}');
  const written = await writePromise;

  assert.deepEqual(written.records[0].rest.bodyJson, { id: 'note-a' });
});

test('notebook REST predicate excludes unrelated API traffic', async () => {
  assert.equal(isNotebookRestUrl('http://127.0.0.1:8080/api/notebook'), true);
  assert.equal(isNotebookRestUrl('http://127.0.0.1:8080/api/notebook/note-a'), true);
  assert.equal(isNotebookRestUrl('http://127.0.0.1:8080/api/security/ticket'), false);
  assert.equal(isNotebookRestUrl('http://127.0.0.1:8080/api/configurations/all'), false);
});

test('REST body parsing preserves raw non-JSON and parses JSON-looking bodies for normalization', () => {
  assert.deepEqual(parseRestBody('plain text', { 'content-type': 'text/plain' }), { bodyRaw: 'plain text' });
  assert.deepEqual(parseRestBody('{"noteId":"note-a","stable":true}', { 'content-type': 'application/json' }), {
    bodyJson: { noteId: 'note-a', stable: true }
  });
});

test('recorder redacts sensitive values from malformed JSON REST and WebSocket payloads', async () => {
  const root = createRoot();
  const page = new EventEmitter();
  const socket = new EventEmitter();
  socket.url = () => 'http://127.0.0.1:8080/ws';
  const recorder = createNotebookTransportRecorder(fixtureMetadata());

  recorder.install(page);
  page.emit('request', request('POST', 'http://127.0.0.1:8080/api/notebook', '{"token":"rest-secret'));
  page.emit('websocket', socket);
  socket.emit('framesent', { payload: '{"credential":"socket-secret' });

  const fixture = await recorder.write(path.join(root.root, 'fixtures/notebook-transport.json'));
  const serialized = JSON.stringify(fixture);
  assert.equal(serialized.includes('rest-secret'), false);
  assert.equal(serialized.includes('socket-secret'), false);
});

test('Playwright adapter replays WebSocket fixtures with cursors and no server forwarding by default', async () => {
  const calls = [];
  const page = {
    route: async () => undefined,
    routeWebSocket: async (_pattern, handler) => calls.push(handler)
  };
  await createPlaywrightFixtureAdapter({
    records: [
      wsRecord(1, 'send', '{"op":"GET_NOTE","msgId":"<msgId>"}'),
      wsRecord(2, 'receive', '{"op":"NOTE","noteId":"note-a"}'),
      wsRecord(3, 'send', '{"op":"RUN_PARAGRAPH","paragraphId":"paragraph-a"}'),
      wsRecord(4, 'receive', '{"op":"PARAGRAPH","paragraphId":"paragraph-a"}')
    ],
    version: fixtureVersion
  }).install(page);

  const replies = [];
  const forwarded = [];
  const handlers = [];
  calls[0]({
    connectToServer: () => ({ send: message => forwarded.push(message) }),
    onMessage: handler => handlers.push(handler),
    send: message => replies.push(message)
  });

  handlers[0]('{"op":"GET_NOTE","msgId":"runtime"}');
  handlers[0]('{"op":"RUN_PARAGRAPH","paragraphId":"paragraph-a"}');

  assert.deepEqual(forwarded, []);
  assert.deepEqual(replies, ['{"op":"NOTE","noteId":"note-a"}', '{"op":"PARAGRAPH","paragraphId":"paragraph-a"}']);
  assert.throws(() => handlers[0]('{"op":"EXTRA"}'), /messages exhausted/);
});

test('Playwright adapter emits server-first messages and reports unconsumed messages', async () => {
  const calls = [];
  const page = {
    route: async () => undefined,
    routeWebSocket: async (_pattern, handler) => calls.push(handler)
  };
  const fixtureAdapter = createPlaywrightFixtureAdapter({
    records: [wsRecord(1, 'receive', '{"op":"CONNECTED"}'), wsRecord(2, 'send', '{"op":"GET_NOTE"}')],
    version: fixtureVersion
  });
  await fixtureAdapter.install(page);

  const replies = [];
  const handlers = [];
  calls[0]({
    onMessage: handler => handlers.push(handler),
    send: message => replies.push(message)
  });

  assert.deepEqual(replies, ['{"op":"CONNECTED"}']);
  assert.throws(() => fixtureAdapter.assertComplete(), /1 unconsumed record/);
  handlers[0]('{"op":"GET_NOTE"}');
  assert.doesNotThrow(() => fixtureAdapter.assertComplete());
});

test('Playwright adapter requires every REST response and WebSocket connection to be consumed', async () => {
  const restAdapter = createPlaywrightFixtureAdapter({
    records: [
      {
        kind: 'rest',
        sequence: 1,
        rest: {
          bodyJson: { id: 'note-a' },
          direction: 'response',
          headers: { 'content-type': 'application/json' },
          request: { bodyRaw: '', headers: { accept: 'application/json' }, method: 'GET', url: '/api/notebook/note-a' },
          status: 200
        }
      }
    ],
    version: fixtureVersion
  });
  await restAdapter.install({ route: async () => undefined, routeWebSocket: async () => undefined });
  assert.throws(() => restAdapter.assertComplete(), /1 unconsumed record/);

  const webSocketAdapter = createPlaywrightFixtureAdapter({
    records: [wsRecord(1, 'send', '{"op":"GET_NOTE"}')],
    version: fixtureVersion
  });
  await webSocketAdapter.install({ route: async () => undefined, routeWebSocket: async () => undefined });
  assert.throws(() => webSocketAdapter.assertComplete(), /was never connected/);
});

test('replay rejects REST and WebSocket traffic that violates the captured transport order', async () => {
  const adapter = fixtureModule.createFixtureReplayAdapter({
    records: [
      wsRecord(1, 'send', '{"op":"GET_NOTE"}'),
      {
        kind: 'rest',
        sequence: 2,
        rest: {
          bodyJson: { id: 'note-a' },
          direction: 'response',
          headers: { 'content-type': 'application/json' },
          request: { bodyRaw: '', headers: { accept: 'application/json' }, method: 'GET', url: '/api/notebook/note-a' },
          status: 200
        }
      }
    ],
    version: fixtureVersion
  });

  await assert.rejects(
    () =>
      adapter.route({ fulfill: async () => undefined }, request('GET', 'http://127.0.0.1:8080/api/notebook/note-a')),
    /Transport fixture out of order: expected WebSocket send/
  );
});

test('Playwright adapter preserves a REST request, server WebSocket frame, and REST response ordering', async () => {
  const calls = [];
  const page = {
    route: async (_pattern, handler) => calls.push({ handler, kind: 'route' }),
    routeWebSocket: async (_pattern, handler) => calls.push({ handler, kind: 'websocket' })
  };
  const fixtureAdapter = createPlaywrightFixtureAdapter({
    records: [
      {
        kind: 'rest',
        sequence: 1,
        rest: {
          direction: 'request',
          request: { bodyRaw: '', headers: { accept: 'application/json' }, method: 'GET', url: '/api/notebook/note-a' }
        }
      },
      wsRecord(2, 'receive', '{"op":"NOTE","noteId":"note-a"}'),
      {
        kind: 'rest',
        sequence: 3,
        rest: {
          bodyJson: { id: 'note-a' },
          direction: 'response',
          headers: { 'content-type': 'application/json' },
          request: { bodyRaw: '', headers: { accept: 'application/json' }, method: 'GET', url: '/api/notebook/note-a' },
          status: 200
        }
      }
    ],
    version: fixtureVersion
  });
  await fixtureAdapter.install(page);

  const events = [];
  calls
    .find(call => call.kind === 'websocket')
    .handler({
      onMessage: () => undefined,
      send: message => events.push(`websocket:${message}`)
    });
  await calls
    .find(call => call.kind === 'route')
    .handler(
      { fulfill: async value => events.push(`rest:${value.body}`) },
      request('GET', 'http://127.0.0.1:8080/api/notebook/note-a')
    );

  assert.deepEqual(events, ['websocket:{"op":"NOTE","noteId":"note-a"}', 'rest:{"id":"note-a"}']);
  assert.doesNotThrow(() => fixtureAdapter.assertComplete());
});

test('Playwright adapter waits for a REST response before sending a following WebSocket frame', async () => {
  const calls = [];
  const page = {
    route: async (_pattern, handler) => calls.push({ handler, kind: 'route' }),
    routeWebSocket: async (_pattern, handler) => calls.push({ handler, kind: 'websocket' })
  };
  const fixtureAdapter = createPlaywrightFixtureAdapter({
    records: [
      {
        kind: 'rest',
        sequence: 1,
        rest: {
          direction: 'request',
          request: { bodyRaw: '', headers: { accept: 'application/json' }, method: 'GET', url: '/api/notebook/note-a' }
        }
      },
      {
        kind: 'rest',
        sequence: 2,
        rest: {
          bodyJson: { id: 'note-a' },
          direction: 'response',
          headers: { 'content-type': 'application/json' },
          request: { bodyRaw: '', headers: { accept: 'application/json' }, method: 'GET', url: '/api/notebook/note-a' },
          status: 200
        }
      },
      wsRecord(3, 'receive', '{"op":"AFTER_REST"}')
    ],
    version: fixtureVersion
  });
  await fixtureAdapter.install(page);

  const events = [];
  calls
    .find(call => call.kind === 'websocket')
    .handler({
      onMessage: () => undefined,
      send: message => events.push(`websocket:${message}`)
    });
  let releaseFulfill;
  const routePromise = calls
    .find(call => call.kind === 'route')
    .handler(
      {
        fulfill: async value => {
          events.push(`fulfill:${value.body}`);
          await new Promise(resolve => {
            releaseFulfill = resolve;
          });
          events.push('fulfilled');
        }
      },
      request('GET', 'http://127.0.0.1:8080/api/notebook/note-a')
    );

  await new Promise(resolve => setImmediate(resolve));
  assert.deepEqual(events, ['fulfill:{"id":"note-a"}']);
  releaseFulfill();
  await routePromise;

  assert.deepEqual(events, ['fulfill:{"id":"note-a"}', 'fulfilled', 'websocket:{"op":"AFTER_REST"}']);
  assert.doesNotThrow(() => fixtureAdapter.assertComplete());
});

test('Playwright adapter rejects REST requests whose body does not match the captured request record', async () => {
  const calls = [];
  const page = {
    route: async (_pattern, handler) => calls.push({ handler, kind: 'route' }),
    routeWebSocket: async () => undefined
  };
  const fixtureAdapter = createPlaywrightFixtureAdapter({
    records: [
      {
        kind: 'rest',
        sequence: 1,
        rest: {
          direction: 'request',
          request: {
            bodyJson: { paragraphId: 'paragraph-a' },
            headers: { accept: 'application/json', 'content-type': 'application/json' },
            method: 'POST',
            url: '/api/notebook/note-a/paragraph'
          }
        }
      },
      {
        kind: 'rest',
        sequence: 2,
        rest: {
          bodyJson: { status: 'ok' },
          direction: 'response',
          headers: { 'content-type': 'application/json' },
          request: {
            bodyJson: { paragraphId: 'paragraph-a' },
            headers: { accept: 'application/json', 'content-type': 'application/json' },
            method: 'POST',
            url: '/api/notebook/note-a/paragraph'
          },
          status: 200
        }
      }
    ],
    version: fixtureVersion
  });
  await fixtureAdapter.install(page);

  await assert.rejects(
    () =>
      calls
        .find(call => call.kind === 'route')
        .handler(
          { fulfill: async () => undefined },
          request('POST', 'http://127.0.0.1:8080/api/notebook/note-a/paragraph', '{"paragraphId":"paragraph-b"}', {
            accept: 'application/json',
            'content-type': 'application/json'
          })
        ),
    /REST fixture request mismatch/
  );
});

test('Playwright adapter waits for an interleaved client WebSocket frame before fulfilling REST', async () => {
  const calls = [];
  const page = {
    route: async (_pattern, handler) => calls.push({ handler, kind: 'route' }),
    routeWebSocket: async (_pattern, handler) => calls.push({ handler, kind: 'websocket' })
  };
  const fixtureAdapter = createPlaywrightFixtureAdapter({
    records: [
      {
        kind: 'rest',
        sequence: 1,
        rest: {
          direction: 'request',
          request: { bodyRaw: '', headers: { accept: 'application/json' }, method: 'GET', url: '/api/notebook/note-a' }
        }
      },
      wsRecord(2, 'send', '{"op":"GET_NOTE"}'),
      {
        kind: 'rest',
        sequence: 3,
        rest: {
          bodyJson: { id: 'note-a' },
          direction: 'response',
          headers: { 'content-type': 'application/json' },
          request: { bodyRaw: '', headers: { accept: 'application/json' }, method: 'GET', url: '/api/notebook/note-a' },
          status: 200
        }
      }
    ],
    version: fixtureVersion
  });
  await fixtureAdapter.install(page);

  const handlers = [];
  calls
    .find(call => call.kind === 'websocket')
    .handler({
      onMessage: handler => handlers.push(handler),
      send: () => undefined
    });
  const routePromise = calls
    .find(call => call.kind === 'route')
    .handler({ fulfill: async () => undefined }, request('GET', 'http://127.0.0.1:8080/api/notebook/note-a'));
  handlers[0]('{"op":"GET_NOTE"}');
  await routePromise;

  assert.doesNotThrow(() => fixtureAdapter.assertComplete());
});

test('Playwright adapter rejects a REST request that arrives before an expected client WebSocket frame', async () => {
  const calls = [];
  const page = {
    route: async (_pattern, handler) => calls.push({ handler, kind: 'route' }),
    routeWebSocket: async (_pattern, handler) => calls.push({ handler, kind: 'websocket' })
  };
  const fixtureAdapter = createPlaywrightFixtureAdapter({
    records: [wsRecord(1, 'send', '{"op":"GET_NOTE"}')],
    version: fixtureVersion
  });
  await fixtureAdapter.install(page);

  await assert.rejects(
    calls
      .find(call => call.kind === 'route')
      .handler({ fulfill: async () => undefined }, request('GET', 'http://127.0.0.1:8080/api/notebook/note-a')),
    /expected WebSocket send, got REST GET \/api\/notebook\/note-a/
  );
});

test('fixture validation reports non-object records instead of throwing', () => {
  assert.deepEqual(validateFixture({ metadata: fixtureMetadata(), records: [null], version: fixtureVersion }), [
    'records[0] must be an object'
  ]);
});

test('Playwright adapter forwards WebSocket messages only with explicit passthrough', async () => {
  const calls = [];
  const page = {
    route: async () => undefined,
    routeWebSocket: async (_pattern, handler) => calls.push(handler)
  };
  await createPlaywrightFixtureAdapter(
    { records: [wsRecord(1, 'send', '{"op":"GET_NOTE"}')], version: fixtureVersion },
    { passthrough: true }
  ).install(page);

  const forwarded = [];
  const handlers = [];
  calls[0]({
    connectToServer: () => ({ send: message => forwarded.push(message) }),
    onMessage: handler => handlers.push(handler),
    send: () => undefined
  });
  handlers[0]('{"op":"GET_NOTE"}');

  assert.deepEqual(forwarded, ['{"op":"GET_NOTE"}']);
});

test('transport fixtures no longer expose a custom WebSocket frame parser', () => {
  assert.equal('createWebSocketFrameParser' in fixtureModule, false);
  assert.equal(existsSync(path.resolve('e2e/core-contract/capture-proxy.mjs')), false);
});

test('capture server does not inherit a Hadoop setting from the developer shell', () => {
  const root = createRoot();
  const result = run(['start', '--root', root.root, '--port', String(root.zeppelinPort)], {
    CAPTURE_ZEPPELIN_COMMAND: `test "$USE_HADOOP" = false && node ${stub} ${root.root}`,
    USE_HADOOP: 'true'
  });
  assert.equal(result.status, 0, result.stderr);
  stop(root);
});

function createRoot() {
  return {
    root: mkdtempSync(path.join(os.tmpdir(), 'zeppelin-capture-')),
    zeppelinPort: freePortSync()
  };
}

function wsRecord(sequence, direction, payloadText) {
  return {
    kind: 'websocket',
    sequence,
    websocket: { direction, payloadText }
  };
}

function start(root, options = {}) {
  const result = run(
    ['start', '--root', root.root, '--mode', options.mode ?? 'anonymous', '--port', String(root.zeppelinPort)],
    { CAPTURE_ZEPPELIN_COMMAND: `node ${stub} ${root.root}` }
  );
  assert.equal(result.status, 0, result.stderr);
}

function stop(root) {
  const result = run(['stop', '--root', root.root, '--port', String(root.zeppelinPort)]);
  assert.equal(result.status, 0, result.stderr);
}

function run(args, env = {}) {
  return spawnSync('bash', [script, ...args], {
    cwd: path.resolve('.'),
    encoding: 'utf8',
    env: { ...process.env, ...env }
  });
}

function request(method, url, body = '', headers = { accept: 'application/json' }) {
  return {
    headers: () => headers,
    method: () => method,
    postData: () => body,
    url: () => url
  };
}

function response(sourceRequest, status, body, headers = { 'content-type': 'application/json' }) {
  return {
    headers: () => headers,
    request: () => sourceRequest,
    status: () => status,
    text: async () => (typeof body === 'function' ? body() : body)
  };
}

function listen() {
  return new Promise(resolve => {
    const server = http.createServer();
    server.listen(0, '127.0.0.1', () => resolve(server));
  });
}

function close(server) {
  return new Promise(resolve => server.close(resolve));
}

function freePortSync() {
  const result = spawnSync(
    process.execPath,
    [
      '-e',
      "require('net').createServer().listen(0, '127.0.0.1', function () { console.log(this.address().port); this.close(); })"
    ],
    {
      encoding: 'utf8'
    }
  );
  return Number(result.stdout.trim());
}
