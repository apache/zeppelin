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
import { existsSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';

import { fixtureMetadata, request, response, wsRecord } from './fixture-doubles.mjs';
import * as fixtureModule from './notebook-transport-fixture.mjs';
import {
  createNotebookTransportRecorder,
  normalizeFixtureRecord,
  createPlaywrightFixtureAdapter,
  fixtureVersion,
  isNotebookRestUrl,
  parseRestBody,
  validateFixture,
  webSocketPayloadMatches
} from './notebook-transport-fixture.mjs';

// Temporary roots accumulate across repeated suite runs.
const temporaryRoots = [];
process.on('exit', () => {
  for (const root of temporaryRoots) {
    rmSync(root, { force: true, recursive: true });
  }
});

function emitCompletedResponse(page, capturedResponse) {
  page.emit('response', capturedResponse);
  page.emit('requestfinished', capturedResponse.request());
}

test('replay rejects a request whose safe headers drifted from the record', () => {
  // Only accept and content-type survive header filtering; drift in either must fail replay.
  const record = {
    bodyRaw: '',
    headers: { accept: 'application/json' },
    method: 'GET',
    url: '/api/notebook/note-a'
  };
  const adapter = createPlaywrightFixtureAdapter({
    metadata: fixtureMetadata(),
    records: [
      { kind: 'rest', sequence: 1, rest: { direction: 'request', request: record } },
      {
        kind: 'rest',
        sequence: 2,
        rest: {
          bodyJson: { id: 'note-a' },
          direction: 'response',
          headers: { 'content-type': 'application/json' },
          request: record,
          status: 200
        }
      }
    ],
    version: fixtureVersion
  });

  let routeHandler;
  adapter.install({
    route: (_pattern, handler) => {
      routeHandler = handler;
    },
    routeWebSocket: () => undefined
  });

  return assert.rejects(
    () =>
      routeHandler(
        { fulfill: async () => undefined },
        request('GET', 'http://127.0.0.1:8080/api/notebook/note-a', '', { accept: 'text/plain' })
      ),
    /REST fixture request mismatch/
  );
});

test('a structured value is masked whole, not just its opening brace', () => {
  // The old value pattern stopped at the first closing brace, leaking the credential and corrupting JSON.
  const cases = [
    ['password={"a":1,"b":"hunter2"}', 'password=<redacted>'],
    ['token: ["hunter2"]', 'token: <redacted>'],
    ['{"datasource":{"password":{"value":"hunter2"}}}', '{"datasource":{"password":<redacted>']
  ];
  for (const [input, expected] of cases) {
    const masked = normalizeFixtureRecord(input);
    assert.doesNotMatch(masked, /hunter2/, `structured value leaked: ${masked}`);
    assert.equal(masked, expected);
  }
});

test('an uppercase name with nothing to split on is still a name', () => {
  // SECRETKEY is one uppercase run; without the suffix rule it was missed.
  for (const name of ['SECRETKEY', 'AWSSECRETKEY', 'PASSWORDFILE', 'TOKENVALUE', 'APIKEYID', 'PGPASSWORD']) {
    assert.equal(normalizeFixtureRecord(`${name}=abc`), `${name}=<redacted>`, `${name} leaked as text`);
    assert.equal(normalizeFixtureRecord({ [name]: 'abc' })[name], `<${name}>`, `${name} leaked as a key`);
  }
  assert.equal(normalizeFixtureRecord('secretary=alice'), 'secretary=alice');
  assert.equal(normalizeFixtureRecord('tokenizer=bpe'), 'tokenizer=bpe');
});

test('a binary WebSocket frame fails the capture rather than dropping the frame', async () => {
  const page = new EventEmitter();
  const socket = new EventEmitter();
  socket.url = () => 'http://127.0.0.1:8080/ws';
  const recorder = createNotebookTransportRecorder(fixtureMetadata());
  recorder.install(page);
  page.emit('websocket', socket);

  // Playwright does not turn listener throws into capture failures, so the recorder must remember them.
  assert.throws(
    () => socket.emit('framesent', { payload: Buffer.from([1, 2, 3]) }),
    /Binary WebSocket frames cannot be captured/
  );
  await assert.rejects(() => recorder.stop(), /Binary WebSocket frames cannot be captured/);
  await assert.rejects(
    () => recorder.write(path.join(createRoot().root, 'f.json')),
    /Binary WebSocket frames cannot be captured/
  );
});

test('replay normalizes a live url the same way the record was normalized', async () => {
  const adapter = createPlaywrightFixtureAdapter({
    metadata: fixtureMetadata(),
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

  let routeHandler;
  await adapter.install({
    route: (_pattern, handler) => {
      routeHandler = handler;
    },
    routeWebSocket: () => undefined
  });

  const fulfilled = [];
  await routeHandler(
    { fulfill: async value => fulfilled.push(value.body) },
    request('GET', 'http://127.0.0.1:8080/api/notebook/note-a?ticket=runtime-secret&msgId=runtime-id')
  );

  // The record has placeholders and the live URL has real values; both normalize before comparison.
  assert.deepEqual(fulfilled, ['{"id":"note-a"}']);
  adapter.assertComplete();

  assert.equal(
    webSocketPayloadMatches('{"op":"GET_NOTE","msgId":"runtime"}', '{"msgId":"runtime","op":"GET_NOTE"}'),
    true
  );
});

test('a name is judged the same way as an object key and as text', () => {
  // Key and text redaction used to disagree, corrupting captured interpreter settings.
  const masked = [
    'access_token',
    'accessToken',
    'x-api-key',
    'password',
    'PGPASSWORD',
    'ticket',
    // Sensitive word embedded in a larger field name.
    'secretKey',
    'passwordHash',
    'password2',
    'credentialsFile',
    'tokenData',
    'aws_secret_access_key',
    'spark.hadoop.fs.s3a.secret.key',
    'ticketId',
    'private_key',
    'privateKey',
    'PRIVATE_KEY',
    'passphrase'
  ];
  const kept = [
    'max_tokens',
    'tokenizer',
    'tokens',
    'secretary',
    'maxTokens',
    'privately',
    'keyboard',
    'passphraseless'
  ];

  for (const name of masked) {
    assert.equal(
      normalizeFixtureRecord({ [name]: 'abc' })[name],
      `<${name}>`,
      `${name} should be masked as an object key`
    );
    assert.equal(normalizeFixtureRecord(`${name}=abc`), `${name}=<redacted>`, `${name} should be masked as text`);
  }

  for (const name of kept) {
    assert.equal(normalizeFixtureRecord({ [name]: 'abc' })[name], 'abc', `${name} should survive as an object key`);
    assert.equal(normalizeFixtureRecord(`${name}=abc`), `${name}=abc`, `${name} should survive as text`);
  }

  // The numeric exception is for free text; an actual principal field is still identity data.
  assert.equal(normalizeFixtureRecord('principal=1000'), 'principal=1000');
  assert.equal(normalizeFixtureRecord({ principal: 1000 }).principal, '<principal>');
  assert.equal(normalizeFixtureRecord({ principal: '10234' }).principal, '<principal>');
  assert.equal(normalizeFixtureRecord({ principal: 's3cr3t' }).principal, '<principal>');
});

test('a value that opens with an angle bracket is still a value', () => {
  // Values beginning with '<' still need redaction unless they are known placeholders.
  assert.equal(normalizeFixtureRecord('password=<script>alert(1)</script>'), 'password=<redacted>');
  // Known placeholders stay idempotent.
  assert.equal(normalizeFixtureRecord('?ticket=%3Cticket%3E'), '?ticket=%3Cticket%3E');
  assert.equal(normalizeFixtureRecord('password=<redacted>'), 'password=<redacted>');
});

test('recorder captures notebook REST and WebSocket browser events only', async () => {
  const page = new EventEmitter();
  const socket = new EventEmitter();
  socket.url = () => 'http://127.0.0.1:8080/ws';
  const recorder = createNotebookTransportRecorder(fixtureMetadata());

  recorder.install(page);
  const capturedRequest = request('POST', 'http://127.0.0.1:8080/api/notebook/note-a', '{"msgId":"runtime"}');
  page.emit('request', capturedRequest);
  emitCompletedResponse(page, response(capturedRequest, 200, '{"id":"note-a"}'));
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
  page.on('request', capturedRequest => emitCompletedResponse(page, response(capturedRequest, 200, '{}')));
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

test('a recorder refuses a second install and stops listening when it stops', async () => {
  const page = new EventEmitter();
  const recorder = createNotebookTransportRecorder(fixtureMetadata());
  recorder.install(page);

  // Double installation records duplicate frames that validate but cannot replay cleanly.
  assert.throws(() => recorder.install(page), /already installed/);

  const first = request('GET', 'http://127.0.0.1:8080/api/notebook/note-a');
  page.emit('request', first);
  emitCompletedResponse(page, response(first, 200, '{"id":"note-a"}'));
  await recorder.stop();
  const captured = recorder.snapshot().records.length;

  // Traffic after stop() must not extend the fixture's claimed endpoint.
  const late = request('DELETE', 'http://127.0.0.1:8080/api/notebook/note-a');
  page.emit('request', late);
  emitCompletedResponse(page, response(late, 200, '{}'));

  assert.equal(recorder.snapshot().records.length, captured, 'traffic after stop was recorded');
});

test('a json response record carries no leftover empty bodyRaw', async () => {
  const page = new EventEmitter();
  const recorder = createNotebookTransportRecorder(fixtureMetadata());
  recorder.install(page);

  const live = request('GET', 'http://127.0.0.1:8080/api/notebook/note-a');
  page.emit('request', live);
  emitCompletedResponse(page, response(live, 200, '{"id":"note-a"}'));
  await recorder.stop();

  const record = recorder.snapshot().records.find(entry => entry.rest?.direction === 'response');
  assert.deepEqual(record.rest.bodyJson, { id: 'note-a' });
  assert.equal('bodyRaw' in record.rest, false, 'an empty bodyRaw was left beside bodyJson');
});

test('recorder fails the capture when a notebook request never got a response', async () => {
  const page = new EventEmitter();
  const recorder = createNotebookTransportRecorder(fixtureMetadata());
  const failed = request('GET', 'http://127.0.0.1:8080/api/notebook/note-a');
  failed.failure = () => ({ errorText: 'net::ERR_CONNECTION_RESET' });

  recorder.install(page);
  page.emit('request', failed);
  page.emit('requestfailed', failed);

  await assert.rejects(() => recorder.stop(), /never got a response|failed during capture/);
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
  page.on('request', capturedRequest => emitCompletedResponse(page, response(capturedRequest, 200, '{}')));
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
  page.on('request', capturedRequest => emitCompletedResponse(page, response(capturedRequest, 200, '{}')));
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
  const live = request('GET', 'http://127.0.0.1:8080/api/notebook/note-a');

  recorder.install(page);
  page.emit(
    'response',
    response(live, 200, () => {
      return new Promise(resolve => {
        resolveBody = resolve;
      });
    })
  );
  const writePromise = recorder.write(path.join(root.root, 'fixtures/notebook-transport.json'));
  resolveBody('{"id":"note-a"}');
  page.emit('requestfinished', live);
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
  page.on('request', capturedRequest => emitCompletedResponse(page, response(capturedRequest, 200, '{}')));
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
      wsRecord(1, 'send', '{"op":"GET_NOTE","msgId":"<msgId:1>"}'),
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
  assert.throws(() => fixtureAdapter.assertComplete(), /1 unconsumed record\(s\) and 0 unfulfilled/);
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
  assert.throws(() => restAdapter.assertComplete(), /1 unconsumed record\(s\) and 0 unfulfilled/);

  const webSocketAdapter = createPlaywrightFixtureAdapter({
    records: [wsRecord(1, 'send', '{"op":"GET_NOTE"}')],
    version: fixtureVersion
  });
  await webSocketAdapter.install({ route: async () => undefined, routeWebSocket: async () => undefined });
  assert.throws(() => webSocketAdapter.assertComplete(), /was never connected/);
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

function createRoot() {
  const root = mkdtempSync(path.join(os.tmpdir(), 'zeppelin-capture-'));
  temporaryRoots.push(root);
  return { root };
}

test('Playwright adapter fulfills interleaved REST responses without hanging a route', async () => {
  const restRecord = (sequence, direction, url, extra = {}) => ({
    kind: 'rest',
    sequence,
    rest: {
      direction,
      request: { bodyRaw: '', headers: { accept: 'application/json' }, method: 'GET', url },
      ...extra
    }
  });
  const responseExtra = url => ({
    bodyJson: { url },
    headers: { 'content-type': 'application/json' },
    status: 200
  });

  // Concurrent notebook reads record responses in completion order.
  const adapter = createPlaywrightFixtureAdapter({
    records: [
      restRecord(1, 'request', '/api/notebook/note-a'),
      restRecord(2, 'request', '/api/notebook/note-b'),
      restRecord(3, 'response', '/api/notebook/note-b', responseExtra('/api/notebook/note-b')),
      restRecord(4, 'response', '/api/notebook/note-a', responseExtra('/api/notebook/note-a'))
    ],
    version: fixtureVersion
  });

  let routeHandler;
  await adapter.install({
    route: (_pattern, handler) => {
      routeHandler = handler;
    },
    routeWebSocket: () => undefined
  });

  const fulfilled = [];
  const call = url =>
    routeHandler(
      {
        fulfill: async ({ body }) => {
          fulfilled.push(`${url} ${body}`);
        }
      },
      request('GET', `http://127.0.0.1:8080${url}`)
    );

  const settled = await Promise.race([
    Promise.all([call('/api/notebook/note-a'), call('/api/notebook/note-b')]).then(() => 'settled'),
    new Promise(resolve => setTimeout(() => resolve('hung'), 2000))
  ]);

  assert.equal(settled, 'settled');
  assert.deepEqual(fulfilled.sort(), [
    '/api/notebook/note-a {"url":"/api/notebook/note-a"}',
    '/api/notebook/note-b {"url":"/api/notebook/note-b"}'
  ]);
  adapter.assertComplete();
});

test('Playwright adapter rejects a REST route that no remaining response record can match', async () => {
  const adapter = createPlaywrightFixtureAdapter({
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

  let routeHandler;
  await adapter.install({
    route: (_pattern, handler) => {
      routeHandler = handler;
    },
    routeWebSocket: () => undefined
  });

  const call = routeHandler(
    { fulfill: async () => undefined },
    request('GET', 'http://127.0.0.1:8080/api/notebook/other')
  );
  const outcome = await Promise.race([
    call.then(
      () => 'resolved',
      error => error
    ),
    new Promise(resolve => setTimeout(() => resolve('hung'), 2000))
  ]);
  assert.notEqual(outcome, 'hung');
  assert.match(String(outcome), /out of order/);
});

test('capture redacts sensitive values in non-JSON WebSocket frames', () => {
  const recorder = createNotebookTransportRecorder(fixtureMetadata());
  let socketHandler;
  recorder.install({
    on: (event, handler) => {
      if (event === 'websocket') {
        socketHandler = handler;
      }
    }
  });

  socketHandler({
    on: (event, handler) => {
      if (event === 'framesent') {
        handler({ payload: 'password=hunter2 Authorization: Bearer abc ticket=t1' });
      }
    },
    url: () => 'ws://127.0.0.1:8080/ws'
  });

  const captured = JSON.stringify(recorder.snapshot());
  for (const secret of ['hunter2', 'Bearer abc', 't1']) {
    assert.ok(!captured.includes(secret), `non-JSON frame leaked ${secret}: ${captured}`);
  }
});

test('capture redacts sensitive keys in bodies that are not valid JSON', () => {
  const truncated = parseRestBody('{"Authorization":"Bearer abc", "cookie":"JSESSIONID=xyz", "principal":"admin"', {});
  const serialized = JSON.stringify(truncated);
  for (const secret of ['Bearer abc', 'JSESSIONID=xyz', 'admin']) {
    assert.ok(!serialized.includes(secret), `malformed JSON leaked ${secret}: ${serialized}`);
  }

  const spaced = parseRestBody('{"password":"hunter two"', {});
  assert.ok(!JSON.stringify(spaced).includes('hunter two'), `quoted value leaked: ${spaced.bodyRaw}`);
});

test('recorder fails closed when a response body read rejects before the fixture is written', async () => {
  const recorder = createNotebookTransportRecorder(fixtureMetadata());
  let responseHandler;
  recorder.install({
    on: (event, handler) => {
      if (event === 'response') {
        responseHandler = handler;
      }
    }
  });

  const failing = response(request('GET', 'http://127.0.0.1:8080/api/notebook/note-a'), 200, () =>
    Promise.reject(new Error('body unavailable'))
  );
  await responseHandler(failing);
  // A pre-stop body read failure must reject the capture, not become an unhandled rejection.
  await new Promise(resolve => setTimeout(resolve, 10));
  await assert.rejects(() => recorder.stop(), /body unavailable/);
  await assert.rejects(() => recorder.write(path.join(createRoot().root, 'f.json')), /body unavailable/);
});

test('Playwright adapter fulfills a response-only record followed by a client WebSocket frame', async () => {
  const calls = [];
  const page = {
    route: async (_pattern, handler) => calls.push({ handler, kind: 'route' }),
    routeWebSocket: async (_pattern, handler) => calls.push({ handler, kind: 'websocket' })
  };
  // Shorthand: response-only record, then the client frame.
  const adapter = createPlaywrightFixtureAdapter({
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
      },
      wsRecord(2, 'send', '{"op":"GET_NOTE"}')
    ],
    version: fixtureVersion
  });
  await adapter.install(page);

  const handlers = [];
  calls
    .find(call => call.kind === 'websocket')
    .handler({
      onMessage: handler => handlers.push(handler),
      send: () => undefined
    });

  const fulfilled = [];
  const routePromise = calls
    .find(call => call.kind === 'route')
    .handler(
      {
        fulfill: async ({ body }) => {
          fulfilled.push(body);
        }
      },
      request('GET', 'http://127.0.0.1:8080/api/notebook/note-a')
    );

  const settled = await Promise.race([
    routePromise.then(
      () => 'settled',
      error => error
    ),
    new Promise(resolve => setTimeout(() => resolve('hung'), 2000))
  ]);
  assert.equal(settled, 'settled', `response-only record must resolve its route, got ${settled}`);
  assert.deepEqual(fulfilled, ['{"id":"note-a"}']);

  handlers[0]('{"op":"GET_NOTE"}');
  assert.doesNotThrow(() => adapter.assertComplete());
});

test('Playwright adapter rejects a response-only record whose route body does not match', async () => {
  const adapter = createPlaywrightFixtureAdapter({
    records: [
      {
        kind: 'rest',
        sequence: 1,
        rest: {
          bodyJson: { id: 'note-a' },
          direction: 'response',
          headers: { 'content-type': 'application/json' },
          request: {
            bodyRaw: '{"name":"expected"}',
            headers: { accept: 'application/json' },
            method: 'POST',
            url: '/api/notebook/note-a'
          },
          status: 200
        }
      }
    ],
    version: fixtureVersion
  });

  let routeHandler;
  await adapter.install({
    route: (_pattern, handler) => {
      routeHandler = handler;
    },
    routeWebSocket: () => undefined
  });

  await assert.rejects(
    routeHandler(
      { fulfill: async () => undefined },
      request('POST', 'http://127.0.0.1:8080/api/notebook/note-a', '{"name":"other"}')
    ),
    /REST fixture request mismatch/
  );
});

test('Playwright adapter keeps replaying when one route cannot be fulfilled', async () => {
  const restRecord = (sequence, direction, url, extra = {}) => ({
    kind: 'rest',
    sequence,
    rest: {
      direction,
      request: { bodyRaw: '', headers: { accept: 'application/json' }, method: 'GET', url },
      ...extra
    }
  });
  const adapter = createPlaywrightFixtureAdapter({
    records: [
      restRecord(1, 'request', '/api/notebook/note-a'),
      restRecord(2, 'request', '/api/notebook/note-b'),
      restRecord(3, 'response', '/api/notebook/note-a', {
        bodyJson: { id: 'note-a' },
        headers: { 'content-type': 'application/json' },
        status: 200
      }),
      restRecord(4, 'response', '/api/notebook/note-b', {
        bodyJson: { id: 'note-b' },
        headers: { 'content-type': 'application/json' },
        status: 200
      })
    ],
    version: fixtureVersion
  });

  let routeHandler;
  await adapter.install({
    route: (_pattern, handler) => {
      routeHandler = handler;
    },
    routeWebSocket: () => undefined
  });

  // A failed fulfill belongs to that route; later independent records must still replay.
  const first = routeHandler(
    {
      fulfill: async () => {
        throw new Error('route was already handled');
      }
    },
    request('GET', 'http://127.0.0.1:8080/api/notebook/note-a')
  );
  const second = routeHandler(
    { fulfill: async () => undefined },
    request('GET', 'http://127.0.0.1:8080/api/notebook/note-b')
  );

  const outcome = await Promise.race([
    Promise.allSettled([first, second]),
    new Promise(resolve => setTimeout(() => resolve('hung'), 2000))
  ]);
  assert.notEqual(outcome, 'hung', 'a failed fulfill must not leave a route waiting');
  assert.deepEqual(
    outcome.map(result => result.status),
    ['rejected', 'fulfilled']
  );
  assert.match(String(outcome[0].reason), /route was already handled/);
  assert.throws(() => adapter.assertComplete(), /route was already handled/);
});

test('capture redacts a credential carried inside a JSON string value', () => {
  const sanitized = fixtureModule.sanitizeFixture({
    metadata: fixtureMetadata(),
    records: [
      {
        kind: 'rest',
        sequence: 1,
        rest: {
          bodyJson: { note: { url: '/api/notebook/note-a?ticket=abc123&view=stable' } },
          direction: 'response',
          headers: { 'content-type': 'application/json' },
          request: { bodyRaw: '', headers: { accept: 'application/json' }, method: 'GET', url: '/api/notebook/note-a' },
          status: 200
        }
      }
    ],
    version: fixtureVersion
  });

  const url = sanitized.records[0].rest.bodyJson.note.url;
  assert.ok(!url.includes('abc123'), `embedded query secret leaked: ${url}`);
  // Only the credential is masked; the rest of the value is preserved.
  assert.ok(url.includes('view=stable'), `redaction destroyed the surrounding value: ${url}`);
});

test('raw redaction masks the credential without swallowing the rest of the line', () => {
  const parsed = parseRestBody('token=abc user=bob action=run', { 'content-type': 'text/plain' });
  assert.ok(!parsed.bodyRaw.includes('abc'), `token leaked: ${parsed.bodyRaw}`);
  assert.match(parsed.bodyRaw, /user=bob action=run/);
});

test('Playwright adapter tells concurrent same-url requests apart by request body', async () => {
  const postRecord = (sequence, direction, paragraph, extra = {}) => ({
    kind: 'rest',
    sequence,
    rest: {
      direction,
      request: {
        bodyJson: { paragraph },
        headers: { accept: 'application/json' },
        method: 'POST',
        url: '/api/notebook/run'
      },
      ...extra
    }
  });
  const adapter = createPlaywrightFixtureAdapter({
    records: [
      postRecord(1, 'request', 'one'),
      postRecord(2, 'request', 'two'),
      postRecord(3, 'response', 'two', {
        bodyJson: { ran: 'two' },
        headers: { 'content-type': 'application/json' },
        status: 200
      }),
      postRecord(4, 'response', 'one', {
        bodyJson: { ran: 'one' },
        headers: { 'content-type': 'application/json' },
        status: 200
      })
    ],
    version: fixtureVersion
  });

  let routeHandler;
  await adapter.install({
    route: (_pattern, handler) => {
      routeHandler = handler;
    },
    routeWebSocket: () => undefined
  });

  const fulfilled = [];
  const call = body =>
    routeHandler(
      {
        fulfill: async ({ body: responseBody }) => {
          fulfilled.push(`${body} -> ${responseBody}`);
        }
      },
      request('POST', 'http://127.0.0.1:8080/api/notebook/run', body)
    );

  const settled = await Promise.race([
    Promise.all([call('{"paragraph":"two"}'), call('{"paragraph":"one"}')]).then(() => 'settled'),
    new Promise(resolve => setTimeout(() => resolve('hung'), 2000))
  ]);
  assert.equal(settled, 'settled');
  // Match same-URL routes by body, not arrival order.
  assert.deepEqual(fulfilled.sort(), ['{"paragraph":"one"} -> {"ran":"one"}', '{"paragraph":"two"} -> {"ran":"two"}']);
  adapter.assertComplete();
});

test('capture redacts a credential in a payload that has no field name to key on', () => {
  // Raw payloads and URL queries have no field key, so text redaction is the only layer.
  const leaks = [
    '%sh export PASSWORD=LEAK5',
    'token=LEAK3&user=bob',
    'curl -H "Authorization: Bearer LEAK4"',
    'Authorization=Bearer LEAK7',
    'jdbc:hive2://host:1/db;password=LEAK6',
    '/api/notebook/x?access_token=LEAK1',
    '/x?x-api-key=LEAK2',
    'token="LEAK8 LEAK9"',
    'password="LEAK10 with spaces"',
    'ticket=LEAK11, note=x'
  ];

  for (const leak of leaks) {
    const sanitized = fixtureModule.sanitizeFixture({
      metadata: fixtureMetadata(),
      records: [
        {
          kind: 'rest',
          sequence: 1,
          rest: {
            bodyRaw: leak,
            direction: 'response',
            headers: { 'content-type': 'text/plain' },
            request: { bodyRaw: '', headers: { accept: 'application/json' }, method: 'GET', url: '/api/notebook/a' },
            status: 200
          }
        },
        { kind: 'websocket', sequence: 2, websocket: { direction: 'send', payloadText: leak } }
      ],
      version: fixtureVersion
    });

    assert.doesNotMatch(sanitized.records[0].rest.bodyRaw, /LEAK\d/, `body leaked: ${leak}`);
    assert.doesNotMatch(sanitized.records[1].websocket.payloadText, /LEAK\d/, `frame leaked: ${leak}`);
  }

  // URL query credentials are redacted wherever the URL appears.
  const withUrl = fixtureModule.sanitizeFixture({
    metadata: fixtureMetadata(),
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
            url: '/api/notebook/note-a?ticket=LEAK12'
          },
          status: 200
        }
      }
    ],
    version: fixtureVersion
  });
  assert.doesNotMatch(withUrl.records[0].rest.request.url, /LEAK\d/);

  const preserved = fixtureModule.sanitizeFixture({
    metadata: fixtureMetadata(),
    records: [
      {
        kind: 'rest',
        sequence: 1,
        rest: {
          bodyRaw: 'please buy a ticket: today and user=bob action=run',
          direction: 'response',
          headers: { 'content-type': 'text/plain' },
          request: { bodyRaw: '', headers: { accept: 'application/json' }, method: 'GET', url: '/api/notebook/a' },
          status: 200
        }
      }
    ],
    version: fixtureVersion
  });
  assert.equal(preserved.records[0].rest.bodyRaw, 'please buy a ticket: today and user=bob action=run');
});

test('a json field keeps its text, because the field name is what redaction acts on', () => {
  // JSON string values can be note text, so value-wide text scanning would corrupt paragraphs.
  // The helper captures from an isolated empty server so the note text is test-owned.
  const sanitized = fixtureModule.sanitizeFixture({
    metadata: fixtureMetadata(),
    records: [
      {
        kind: 'rest',
        sequence: 1,
        rest: {
          bodyJson: {
            password: 'masked-by-name',
            text: 'ticketCount = df.count()\nconst cookieBanner = document.getElementById("x")'
          },
          direction: 'response',
          headers: { 'content-type': 'application/json' },
          request: { bodyRaw: '', headers: { accept: 'application/json' }, method: 'GET', url: '/api/notebook/a' },
          status: 200
        }
      }
    ],
    version: fixtureVersion
  });

  const body = sanitized.records[0].rest.bodyJson;
  assert.equal(body.password, '<password>');
  assert.equal(body.text, 'ticketCount = df.count()\nconst cookieBanner = document.getElementById("x")');
});

test('Playwright adapter fails a route that arrives after the fixture has already failed', async () => {
  const restRecord = (sequence, direction, paragraph, extra = {}) => ({
    kind: 'rest',
    sequence,
    rest: {
      direction,
      request: {
        bodyJson: { paragraph },
        headers: { accept: 'application/json' },
        method: 'POST',
        url: '/api/notebook/run'
      },
      ...extra
    }
  });
  const adapter = createPlaywrightFixtureAdapter({
    records: [
      restRecord(1, 'request', 'one'),
      restRecord(2, 'response', 'one', {
        bodyJson: { ran: 'one' },
        headers: { 'content-type': 'application/json' },
        status: 200
      })
    ],
    version: fixtureVersion
  });

  let routeHandler;
  await adapter.install({
    route: (_pattern, handler) => {
      routeHandler = handler;
    },
    routeWebSocket: () => undefined
  });

  const call = paragraph =>
    routeHandler(
      { fulfill: async () => undefined },
      request('POST', 'http://127.0.0.1:8080/api/notebook/run', JSON.stringify({ paragraph }))
    );

  // Once the first route proves fixture drift, later routes must fail fast.
  await assert.rejects(() => call('unexpected'), /REST fixture request mismatch/);
  const outcome = await Promise.race([
    call('one').then(
      () => 'resolved',
      error => String(error)
    ),
    new Promise(resolve => setTimeout(() => resolve('hung'), 2000))
  ]);
  assert.notEqual(outcome, 'hung', 'a route arriving after a fixture error must not hang');
  assert.match(outcome, /REST fixture request mismatch/);
  assert.throws(() => adapter.assertComplete(), /REST fixture request mismatch/);
});

test('Playwright adapter rejects a response record whose request shape drifted before installation', () => {
  const restRecord = (sequence, direction, paragraph, extra = {}) => ({
    kind: 'rest',
    sequence,
    rest: {
      direction,
      request: {
        bodyJson: { paragraph },
        headers: { accept: 'application/json' },
        method: 'POST',
        url: '/api/notebook/run'
      },
      ...extra
    }
  });
  // A response whose request shape disagrees with its request record is stale fixture drift.
  assert.throws(
    () =>
      createPlaywrightFixtureAdapter({
        records: [
          restRecord(1, 'request', 'one'),
          restRecord(2, 'response', 'one-edited', {
            bodyJson: { ran: 'drifted' },
            headers: { 'content-type': 'application/json' },
            status: 200
          })
        ],
        version: fixtureVersion
      }),
    /request has no response/
  );
});

test('a captured fixture replays the request it was captured from', async () => {
  // Normalization runs on both stored fixtures and live requests; it must be idempotent.
  const noteTexts = [
    'docker run -e PASSWORD=a -e TOKEN=b img',
    'tokens = text.split()',
    'tokenizer = AutoTokenizer.from_pretrained("bert")',
    'export TOKEN=abc',
    'principal = 1000'
  ];

  for (const text of noteTexts) {
    const page = new EventEmitter();
    const recorder = createNotebookTransportRecorder(fixtureMetadata());
    recorder.install(page);

    const body = JSON.stringify({ paragraphs: [{ text }] });
    const live = request('PUT', 'http://127.0.0.1:8080/api/notebook/note-a', body);
    page.emit('request', live);
    emitCompletedResponse(page, response(live, 200, '{"status":"OK"}'));
    await recorder.stop();

    const adapter = createPlaywrightFixtureAdapter(recorder.snapshot());
    let routeHandler;
    await adapter.install({
      route: (_pattern, handler) => {
        routeHandler = handler;
      },
      routeWebSocket: () => undefined
    });

    const fulfilled = [];
    await routeHandler(
      {
        fulfill: async value => fulfilled.push(value.body)
      },
      request('PUT', 'http://127.0.0.1:8080/api/notebook/note-a', body)
    );
    assert.deepEqual(fulfilled, ['{"status":"OK"}'], `replay failed for note text: ${text}`);
    adapter.assertComplete();
  }
});

test('normalization is idempotent so a fixture and a live request agree', () => {
  const samples = [
    'docker run -e PASSWORD=a -e TOKEN=b img',
    'tokens = text.split()',
    'tokens = <redacted>',
    'password=a token=b',
    '/api/notebook/x?access_token=abc&view=stable',
    '/api/notebook/x?ticket=%3Cticket%3E',
    'curl -H "Authorization: Bearer abc"',
    '{"access_token":"abc","expires":3600}',
    'password: secret',
    'please buy a ticket: today',
    'principal = 1000'
  ];

  for (const sample of samples) {
    const once = normalizeFixtureRecord(sample);
    const twice = normalizeFixtureRecord(once);
    assert.equal(twice, once, `normalization is not idempotent for ${JSON.stringify(sample)}`);
  }
});

test('normalization stays linear on long dotted identifiers', () => {
  // Guard against regex backtracking on minified input.
  // Include : and = so samples exercise the name scan instead of its early return.
  const samples = [
    'org.apache.shiro.authc.credential.PasswordMatcher.doCredentialsMatch '.repeat(600),
    'a.token.'.repeat(5000),
    'org.apache.shiro.authc.credential.PasswordMatcher.doCredentialsMatch: x '.repeat(600),
    'a.token.: '.repeat(4000),
    '{"spark.hadoop.fs.s3a.access.name":"value"},'.repeat(900),
    'accessToken = "abc" and '.repeat(2000)
  ];

  for (const sample of samples) {
    const started = process.hrtime.bigint();
    normalizeFixtureRecord(sample);
    const elapsedMs = Number(process.hrtime.bigint() - started) / 1e6;
    assert.ok(elapsedMs < 1000, `normalization took ${elapsedMs.toFixed(0)}ms for ${sample.length} chars`);
  }
});

test('redaction masks every credential on a line and leaves the rest readable', () => {
  const line = normalizeFixtureRecord('docker run -e PASSWORD=first -e TOKEN=second img');
  assert.doesNotMatch(line, /first|second/);
  assert.equal(line, 'docker run -e PASSWORD=<redacted> -e TOKEN=<redacted> img');
});

test('redaction rewrites only the credential and keeps the text around it', () => {
  // Assert the exact output so redaction cannot delete surrounding text.
  const cases = [
    ['Authorization: Bearer abc', 'Authorization: <redacted>'],
    ['authorization=Bearer x', 'authorization=<redacted>'],
    ['Cookie: theme=dark', 'Cookie: <redacted>'],
    ['ticket=abc, note=x', 'ticket=<redacted>, note=x'],
    ['token=first second', 'token=<redacted> second'],
    ['PGPASSWORD=hunter2 psql -h db', 'PGPASSWORD=<redacted> psql -h db'],
    ['curl -H "X-Api-Key: abc"', 'curl -H "X-Api-Key: <redacted>"'],
    ['val accessToken = "eyJhbGciOi"', 'val accessToken = "<redacted>"'],
    ['postgres://user:pass@host/db', 'postgres://user:<redacted>@host/db'],
    ["if password == 'hunter2':", "if password == '<redacted>':"],
    ['token === "abc"', 'token === "<redacted>"']
  ];
  for (const [input, expected] of cases) {
    assert.equal(normalizeFixtureRecord(input), expected, `redaction changed more than the value of ${input}`);
  }

  const untouched = [
    'see org.apache.shiro.authz.Authorization.check(user) for details',
    'Cookie.parse(header)',
    'tokenizer = AutoTokenizer.from_pretrained("bert")',
    'tokens = text.split()',
    'max_tokens=100, temperature=0.2',
    'secretary=alice',
    'please buy a ticket: today',
    // A comparison against a variable is code, not a credential.
    'if token == expected: pass',
    'https://host/path'
  ];
  for (const input of untouched) {
    assert.equal(normalizeFixtureRecord(input), input, `redaction damaged note text: ${input}`);
  }
});

test('a numeric value is kept only where the name is also an everyday word', () => {
  // `principal` is an authentication field and an accounting term; the rest are not.
  assert.equal(normalizeFixtureRecord('principal = 1000'), 'principal = 1000');
  assert.equal(normalizeFixtureRecord('principal = s3cr3t'), 'principal = <redacted>');
  for (const input of ['password=123456', 'token=482913', 'ticket=1234']) {
    assert.doesNotMatch(normalizeFixtureRecord(input), /\d/, `${input} kept a numeric credential`);
  }
});

test('validateFixture rejects an unsupported version, lost ordering and a missing payload', () => {
  const restRecord = (sequence = 1) => ({
    kind: 'rest',
    sequence,
    rest: {
      bodyJson: { id: 'note-a' },
      direction: 'response',
      headers: { 'content-type': 'application/json' },
      request: { bodyRaw: '', headers: { accept: 'application/json' }, method: 'GET', url: '/api/notebook/note-a' },
      status: 200
    }
  });

  assert.deepEqual(validateFixture({ metadata: fixtureMetadata(), records: [restRecord()], version: 999 }), [
    'Unsupported fixture version 999'
  ]);

  assert.deepEqual(
    validateFixture({
      metadata: fixtureMetadata(),
      records: [restRecord(2), restRecord(1)],
      version: fixtureVersion
    }),
    ['records[1].sequence must increase without reordering']
  );

  assert.deepEqual(
    validateFixture({
      metadata: fixtureMetadata(),
      records: [{ kind: 'websocket', sequence: 1, websocket: { direction: 'receive' } }],
      version: fixtureVersion
    }),
    ['records[0].websocket payloadText or payloadBase64 is required to preserve message shape']
  );
});

test("a value is only left alone when the whole value is that field's placeholder", () => {
  // Prefix-style placeholder matching could hide a real credential.
  assert.equal(normalizeFixtureRecord('password=<redacted>REAL_SECRET'), 'password=<redacted>');
  assert.equal(normalizeFixtureRecord('password=%3Cprivate%3E'), 'password=<redacted>');
  assert.equal(normalizeFixtureRecord('https://u:<redacted>evil@h/db'), 'https://u:<redacted>@h/db');
  assert.equal(normalizeFixtureRecord('?apiKey=%3CapiKey%3EMORE&view=stable'), '?apiKey=<redacted>&view=stable');
  // URL placeholders may be percent-encoded; both forms must stay idempotent.
  assert.equal(normalizeFixtureRecord('?apiKey=%3CapiKey%3E&view=stable'), '?apiKey=%3CapiKey%3E&view=stable');
  assert.equal(normalizeFixtureRecord('password=<redacted>'), 'password=<redacted>');
});

test('a quoted credential is masked whole whatever the name around the sensitive word', () => {
  // Quoted values must use the same credential-name rules as object fields.
  // Names such as passwordHash and aws_secret_access_key must still mask the entire quoted value.
  assert.equal(normalizeFixtureRecord('secretKey="first second"'), 'secretKey="<redacted>"');
  assert.equal(normalizeFixtureRecord('aws_secret_access_key="first second"'), 'aws_secret_access_key="<redacted>"');
  assert.equal(normalizeFixtureRecord("passwordHash='a b c'"), "passwordHash='<redacted>'");
  assert.equal(normalizeFixtureRecord('tokenizer="first second"'), 'tokenizer="first second"');
});

test('a json websocket frame keeps its note text and replays its own capture', () => {
  // JSON WebSocket frames are redacted by key before stringification;
  // scanning the result as text corrupts note content and can break replay.
  const frames = [
    '{"op":"RUN_PARAGRAPH","data":{"paragraph":"ticketCount = df.count()"}}',
    '{"op":"RUN_PARAGRAPH","data":{"paragraph":"SELECT * FROM t WHERE ticket_id = 42"}}',
    '{"op":"RUN_PARAGRAPH","data":{"paragraph":"const cookieBanner = document.getElementById(\\"x\\")"}}'
  ];

  for (const frame of frames) {
    const sanitized = fixtureModule.sanitizeFixture({
      metadata: fixtureMetadata(),
      records: [{ kind: 'websocket', sequence: 1, websocket: { direction: 'send', payloadText: frame } }],
      version: fixtureVersion
    });
    const stored = sanitized.records[0].websocket.payloadText;

    assert.doesNotThrow(() => JSON.parse(stored), `fixture holds invalid json for ${frame}`);
    assert.equal(JSON.parse(stored).data.paragraph, JSON.parse(frame).data.paragraph, 'note text was rewritten');
    assert.equal(webSocketPayloadMatches(stored, frame), true, 'the frame no longer replays its own capture');
  }

  // Keyed JSON and raw frames exercise different redaction paths.
  const masked = fixtureModule.sanitizeFixture({
    metadata: fixtureMetadata(),
    records: [
      { kind: 'websocket', sequence: 1, websocket: { direction: 'send', payloadText: '{"ticket":"abc"}' } },
      { kind: 'websocket', sequence: 2, websocket: { direction: 'send', payloadText: 'token=abc' } }
    ],
    version: fixtureVersion
  });
  assert.equal(masked.records[0].websocket.payloadText, '{"ticket":"<ticket>"}');
  assert.equal(masked.records[1].websocket.payloadText, 'token=<redacted>');
});

test('a string inside an array keeps its text, like a string under a key', () => {
  assert.deepEqual(normalizeFixtureRecord({ tags: ['ticket = 1', 'password = 2'] }), {
    tags: ['ticket = 1', 'password = 2']
  });
  assert.deepEqual(normalizeFixtureRecord({ ticket: ['a', 'b'] }), { ticket: '<ticket>' });
});

test('a masked name holding a container is treated by why the name is masked', () => {
  // A note-authored field named time is content; masking it as volatile metadata would change fixture shape.
  assert.deepEqual(normalizeFixtureRecord({ settings: { forms: { time: { name: 'time', defaultValue: '9' } } } }), {
    settings: { forms: { time: { name: 'time', defaultValue: '9' } } }
  });
  assert.deepEqual(normalizeFixtureRecord({ credentials: { apiKey: 'k', note: 'keep' } }), {
    credentials: '<credentials>'
  });
  assert.deepEqual(normalizeFixtureRecord({ authenticationInfo: { user: { name: 'alice' } } }), {
    authenticationInfo: { user: '<user>' }
  });
  assert.deepEqual(normalizeFixtureRecord({ time: '2026-09-08' }), { time: '<time>' });
});

test('the acting principal is masked by field name and left alone in text', () => {
  // Zeppelin stores the acting principal on paragraph payloads; authenticated captures must redact it.
  const paragraph = normalizeFixtureRecord({
    id: 'paragraph_1788827011252_1181785990',
    text: '%md hello',
    user: 'alice',
    dateCreated: 'Sep 8, 2026 1:23:31 PM'
  });
  assert.equal(paragraph.user, '<user>');
  assert.equal(paragraph.dateCreated, '<dateCreated>');
  // The paragraph id is the subject of other records, so it stays.
  assert.equal(paragraph.id, 'paragraph_1788827011252_1181785990');
  assert.equal(paragraph.text, '%md hello');

  // COLLABORATIVE_MODE_STATUS exposes active users and roles from auth-mode captures.
  assert.deepEqual(
    normalizeFixtureRecord({ op: 'COLLABORATIVE_MODE_STATUS', data: { status: true, users: ['user1', 'user2'] } }),
    { op: 'COLLABORATIVE_MODE_STATUS', data: { status: true, users: ['<users>', '<users>'] } }
  );
  assert.deepEqual(normalizeFixtureRecord({ roles: '["role1","role2"]' }), { roles: '<roles>' });

  // Permission entries are masked individually so fixture shape and counts remain useful.
  assert.deepEqual(
    normalizeFixtureRecord({
      body: { owners: ['alice'], readers: ['alice', 'bob'], runners: [], writers: [] }
    }),
    {
      body: {
        owners: ['<owners>'],
        readers: ['<readers>', '<readers>'],
        runners: [],
        writers: []
      }
    }
  );

  // `user=` is ordinary in a url and in note text, so the text rules leave it alone.
  assert.equal(normalizeFixtureRecord('user=bob action=run'), 'user=bob action=run');
  assert.equal(normalizeFixtureRecord('owners=alice,bob'), 'owners=alice,bob');
  assert.deepEqual(normalizeFixtureRecord({ url: '/api/notebook/x?user=bob' }), {
    url: '/api/notebook/x?user=bob'
  });
});

test('a lowercase name written without separators is caught by its ending', () => {
  // dbpassword only matches through the suffix rule.
  for (const name of ['dbpassword', 'mytoken', 'apitoken', 'userpasswd']) {
    assert.equal(normalizeFixtureRecord({ [name]: 'abc' })[name], `<${name}>`, `${name} was not masked`);
  }
  for (const name of ['tokenizer', 'secretary', 'passwordless']) {
    assert.equal(normalizeFixtureRecord({ [name]: 'abc' })[name], 'abc', `${name} was over-masked`);
  }
});

test('a WebSocket frame the page sends has to match the next recorded one', async () => {
  // Unexpected WebSocket frames must fail replay immediately.
  const adapter = createPlaywrightFixtureAdapter({
    metadata: fixtureMetadata(),
    records: [wsRecord(1, 'send', '{"op":"GET_NOTE","noteId":"note-a"}')],
    version: fixtureVersion
  });

  let socketHandler;
  await adapter.install({
    route: async () => undefined,
    routeWebSocket: async (_pattern, handler) => {
      socketHandler = handler;
    }
  });

  let messageHandler;
  socketHandler({ onMessage: handler => (messageHandler = handler), send: () => undefined, close: () => undefined });

  assert.throws(
    () => messageHandler('{"op":"RUN_PARAGRAPH","noteId":"note-a"}'),
    /WebSocket fixture send mismatch/,
    'an unexpected frame was accepted'
  );
});

test('a second WebSocket connection is refused', async () => {
  const adapter = createPlaywrightFixtureAdapter({
    metadata: fixtureMetadata(),
    records: [wsRecord(1, 'send', '{"op":"GET_NOTE"}')],
    version: fixtureVersion
  });

  let socketHandler;
  await adapter.install({
    route: async () => undefined,
    routeWebSocket: async (_pattern, handler) => {
      socketHandler = handler;
    }
  });

  const socket = () => ({ onMessage: () => undefined, send: () => undefined, close: () => undefined });
  socketHandler(socket());

  assert.throws(() => socketHandler(socket()), /one WebSocket connection per fixture/);
});

test('validateFixture rejects a payload key that holds the wrong kind of value', () => {
  // Wrong payload types lose data: null becomes "null" and invalid base64 decodes to salvaged bytes.
  const frame = websocket => ({
    metadata: fixtureMetadata(),
    records: [{ kind: 'websocket', sequence: 1, websocket }],
    version: fixtureVersion
  });

  for (const payloadText of [null, 42, {}, []]) {
    assert.deepEqual(validateFixture(frame({ direction: 'send', payloadText })), [
      'records[0].websocket.payloadText must be a string'
    ]);
  }
  for (const payloadBase64 of ['!!!not-base64!!!', 42, 'a b c', 'YQ==extra']) {
    assert.deepEqual(validateFixture(frame({ direction: 'send', payloadBase64 })), [
      'records[0].websocket.payloadBase64 must be base64'
    ]);
  }
  for (const raw of ['', 'a', 'ab', 'abc', 'hello world']) {
    const payloadBase64 = Buffer.from(raw).toString('base64');
    assert.deepEqual(validateFixture(frame({ direction: 'send', payloadBase64 })), [], `rejected ${payloadBase64}`);
    assert.deepEqual(
      validateFixture(frame({ direction: 'send', payloadBase64: payloadBase64.replace(/=+$/, '') })),
      [],
      `rejected unpadded ${payloadBase64}`
    );
  }

  const response = rest => ({
    metadata: fixtureMetadata(),
    records: [
      {
        kind: 'rest',
        sequence: 1,
        rest: {
          direction: 'response',
          headers: { 'content-type': 'application/json' },
          request: { bodyRaw: '', headers: { accept: 'application/json' }, method: 'GET', url: '/api/notebook/a' },
          status: 200,
          ...rest
        }
      }
    ],
    version: fixtureVersion
  });
  assert.deepEqual(validateFixture(response({ bodyRaw: 42 })), [
    'records[0].rest.bodyJson or bodyRaw is required to preserve response shape'
  ]);
  assert.deepEqual(validateFixture(response({ bodyRaw: '' })), []);
  assert.deepEqual(validateFixture(response({ bodyJson: null })), []);
});

test('a request that is not the recorded one fails against a request record', async () => {
  // Request records pin the call shape before any response is delivered.
  const restRequest = (sequence, url) => ({
    kind: 'rest',
    sequence,
    rest: {
      direction: 'request',
      request: { bodyRaw: '', headers: { accept: 'application/json' }, method: 'GET', url }
    }
  });
  const restResponse = (sequence, url) => ({
    kind: 'rest',
    sequence,
    rest: {
      bodyJson: { id: url },
      direction: 'response',
      headers: { 'content-type': 'application/json' },
      request: { bodyRaw: '', headers: { accept: 'application/json' }, method: 'GET', url },
      status: 200
    }
  });

  const adapter = createPlaywrightFixtureAdapter({
    metadata: fixtureMetadata(),
    records: [
      restRequest(1, '/api/notebook/note-a'),
      restResponse(2, '/api/notebook/note-a'),
      restRequest(3, '/api/notebook/note-b'),
      restResponse(4, '/api/notebook/note-b')
    ],
    version: fixtureVersion
  });

  let routeHandler;
  await adapter.install({
    route: async (_pattern, handler) => {
      routeHandler = handler;
    },
    routeWebSocket: async () => undefined
  });

  // The mismatch is known immediately; the route must reject instead of timing out.
  const live = request('GET', 'http://127.0.0.1:8080/api/notebook/note-b');
  const outcome = await Promise.race([
    routeHandler({ fulfill: async () => undefined, request: () => live }, live).then(
      () => 'fulfilled',
      error => error.message
    ),
    new Promise(resolve => setTimeout(() => resolve('hung'), 2000))
  ]);

  assert.notEqual(outcome, 'hung', 'the route neither answered nor failed');
  assert.match(String(outcome), /REST fixture request mismatch/);
});

test('validateFixture rejects a repeated sequence, not only a decreasing one', () => {
  // Duplicate sequences also lose ordering.
  const record = sequence => ({
    kind: 'websocket',
    sequence,
    websocket: { direction: 'send', payloadText: '{"op":"GET_NOTE"}' }
  });

  assert.deepEqual(
    validateFixture({ metadata: fixtureMetadata(), records: [record(1), record(1)], version: fixtureVersion }),
    ['records[1].sequence must increase without reordering']
  );
});

test('a malformed json frame matches an identical copy of itself', () => {
  // Malformed JSON is stored and compared by raw payload rules.
  assert.equal(webSocketPayloadMatches('{oops', '{oops'), true);
  assert.equal(webSocketPayloadMatches('{oops', '{other'), false);
  assert.equal(webSocketPayloadMatches('{oops', '{"a":1}'), false);
});

test('a non-json websocket frame replays the capture it was redacted from', () => {
  // Live frames are redacted before comparison with the stored frame.
  assert.equal(normalizeFixtureRecord('token=abc'), 'token=<redacted>');
  assert.equal(webSocketPayloadMatches('token=<redacted>', 'token=abc'), true);
  assert.equal(webSocketPayloadMatches('token=<redacted>', 'other=abc'), false);
});

test('Playwright adapter fails a route no remaining record can answer', async () => {
  const adapter = createPlaywrightFixtureAdapter({
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

  let routeHandler;
  await adapter.install({
    route: (_pattern, handler) => {
      routeHandler = handler;
    },
    routeWebSocket: () => undefined
  });

  // After note-a is consumed, no remaining record can answer another note-a request.
  await routeHandler({ fulfill: async () => undefined }, request('GET', 'http://127.0.0.1:8080/api/notebook/note-a'));
  await assert.rejects(
    () => routeHandler({ fulfill: async () => undefined }, request('GET', 'http://127.0.0.1:8080/api/notebook/note-a')),
    /no remaining response for GET \/api\/notebook\/note-a/
  );
});

test('a captured url whose query holds a credential still replays', async () => {
  // Redact the live URL the same way the recorder redacted the stored URL.
  const page = new EventEmitter();
  const recorder = createNotebookTransportRecorder(fixtureMetadata());
  recorder.install(page);

  const url = 'http://127.0.0.1:8080/api/notebook/search?q=token=abc';
  const live = request('GET', url);
  page.emit('request', live);
  emitCompletedResponse(page, response(live, 200, '{"body":[]}'));
  await recorder.stop();

  const fixture = recorder.snapshot();
  assert.doesNotMatch(JSON.stringify(fixture), /token=abc/, 'the stored url still carries the credential');

  const adapter = createPlaywrightFixtureAdapter(fixture);
  let routeHandler;
  await adapter.install({
    route: (_pattern, handler) => {
      routeHandler = handler;
    },
    routeWebSocket: () => undefined
  });

  const fulfilled = [];
  await routeHandler({ fulfill: async value => fulfilled.push(value.body) }, request('GET', url));
  assert.deepEqual(fulfilled, ['{"body":[]}']);
  adapter.assertComplete();
});

test('Playwright adapter lets an early request wait for a later record', async () => {
  const calls = [];
  const page = {
    route: async (_pattern, handler) => calls.push({ handler, kind: 'route' }),
    routeWebSocket: async (_pattern, handler) => calls.push({ handler, kind: 'websocket' })
  };
  const restResponse = (sequence, url) => ({
    kind: 'rest',
    sequence,
    rest: {
      bodyJson: { id: url },
      direction: 'response',
      headers: { 'content-type': 'application/json' },
      request: { bodyRaw: '', headers: { accept: 'application/json' }, method: 'GET', url },
      status: 200
    }
  });
  // note-b waits because its response appears after the expected client frame.
  const adapter = createPlaywrightFixtureAdapter({
    records: [
      restResponse(1, '/api/notebook/note-a'),
      wsRecord(2, 'send', '{"op":"GET_NOTE"}'),
      restResponse(3, '/api/notebook/note-b')
    ],
    version: fixtureVersion
  });
  await adapter.install(page);

  const handlers = [];
  calls
    .find(call => call.kind === 'websocket')
    .handler({ onMessage: handler => handlers.push(handler), send: () => undefined });

  const routeHandler = calls.find(call => call.kind === 'route').handler;
  const fulfilled = [];
  const call = url =>
    routeHandler({ fulfill: async value => fulfilled.push(value.body) }, request('GET', `http://127.0.0.1:8080${url}`));

  const first = call('/api/notebook/note-a');
  const second = call('/api/notebook/note-b');
  await first;
  handlers[0]('{"op":"GET_NOTE"}');

  const settled = await Promise.race([
    second.then(
      () => 'settled',
      error => `rejected: ${error.message}`
    ),
    new Promise(resolve => setTimeout(() => resolve('hung'), 2000))
  ]);
  assert.equal(settled, 'settled');
  assert.deepEqual(fulfilled.sort(), ['{"id":"/api/notebook/note-a"}', '{"id":"/api/notebook/note-b"}']);
  adapter.assertComplete();
});

test('the fixture example in the README passes its own validator', () => {
  // Keep the README example executable so doc drift is caught by the suite.
  const readme = readFileSync(path.resolve('e2e/core-contract/README.md'), 'utf8');
  const block = readme.match(/```json\n([\s\S]*?)\n```/);
  assert.ok(block, 'the README no longer contains a json fixture example');

  const example = JSON.parse(block[1]);
  assert.deepEqual(validateFixture(example), []);
});

test('stop and write reject a request still awaiting response headers', async () => {
  const recorder = createNotebookTransportRecorder(fixtureMetadata());
  const page = new EventEmitter();
  recorder.install(page);
  page.emit('request', request('GET', 'http://localhost/api/notebook/pending'));
  await assert.rejects(() => recorder.stop(), /outstanding notebook request/);
  const output = path.join(createRoot().root, 'incomplete.json');
  await assert.rejects(() => recorder.write(output), /outstanding notebook request/);
  assert.equal(existsSync(output), false);
});

test('replay preserves distinct and repeated envelope msgId correlations', async () => {
  const records = [
    wsRecord(1, 'send', '{"op":"INSERT_PARAGRAPH","msgId":"capture-a"}'),
    wsRecord(2, 'send', '{"op":"INSERT_PARAGRAPH","msgId":"capture-b"}'),
    wsRecord(3, 'receive', '{"op":"PARAGRAPH_ADDED","msgId":"capture-b"}'),
    wsRecord(4, 'receive', '{"op":"PARAGRAPH_ADDED","msgId":"capture-a"}'),
    wsRecord(5, 'send', '{"op":"INSERT_PARAGRAPH","msgId":"capture-a"}'),
    wsRecord(6, 'receive', '{"op":"PARAGRAPH_ADDED","msgId":"capture-a"}')
  ];
  assert.equal(
    JSON.parse(fixtureModule.sanitizeFixture({ records }).records[0].websocket.payloadText).msgId,
    '<msgId:1>'
  );
  const adapter = createPlaywrightFixtureAdapter({ version: fixtureVersion, records });
  let connect;
  let send;
  const replies = [];
  await adapter.install({
    route: async () => {},
    routeWebSocket: async (_url, handler) => {
      connect = handler;
    }
  });
  connect({
    onMessage: handler => {
      send = handler;
    },
    send: message => replies.push(JSON.parse(message))
  });
  send('{"op":"INSERT_PARAGRAPH","msgId":"runtime-a"}');
  send('{"op":"INSERT_PARAGRAPH","msgId":"runtime-b"}');
  send('{"op":"INSERT_PARAGRAPH","msgId":"runtime-a"}');
  assert.deepEqual(
    replies.map(reply => reply.msgId),
    ['runtime-b', 'runtime-a', 'runtime-a']
  );
  adapter.assertComplete();
});

test('legacy erased envelope msgId fails closed', () => {
  assert.throws(
    () =>
      createPlaywrightFixtureAdapter({
        version: fixtureVersion,
        records: [wsRecord(1, 'send', '{"op":"GET_NOTE","msgId":"<msgId>"}')]
      }),
    /ambiguous.*msgId/
  );
});

test('replay rejects runtime ID aliasing and changed IDs on repeated sends', async () => {
  for (const [firstId, secondId, secondRuntimeId] of [
    ['captured-a', 'captured-b', 'runtime-a'],
    ['captured-a', 'captured-a', 'runtime-b']
  ]) {
    const adapter = createPlaywrightFixtureAdapter({
      version: fixtureVersion,
      records: [
        wsRecord(1, 'send', JSON.stringify({ op: 'INSERT_PARAGRAPH', msgId: firstId })),
        wsRecord(2, 'send', JSON.stringify({ op: 'INSERT_PARAGRAPH', msgId: secondId }))
      ]
    });
    let connect;
    let send;
    await adapter.install({
      route: async () => {},
      routeWebSocket: async (_url, handler) => {
        connect = handler;
      }
    });
    connect({
      onMessage: handler => {
        send = handler;
      },
      send: () => {}
    });
    send('{"op":"INSERT_PARAGRAPH","msgId":"runtime-a"}');
    assert.throws(
      () => send(JSON.stringify({ op: 'INSERT_PARAGRAPH', msgId: secondRuntimeId })),
      /msgId correlation mismatch/
    );
    assert.throws(() => adapter.assertComplete(), /msgId correlation mismatch/);
  }
});

test('remote operation IDs remain distinct from local IDs during replay', async () => {
  const adapter = createPlaywrightFixtureAdapter({
    version: fixtureVersion,
    records: [
      wsRecord(1, 'receive', '{"op":"PARAGRAPH_ADDED","msgId":"remote-operation"}'),
      wsRecord(2, 'send', '{"op":"INSERT_PARAGRAPH","msgId":"local-operation"}'),
      wsRecord(3, 'receive', '{"op":"PARAGRAPH_ADDED","msgId":"remote-operation"}'),
      wsRecord(4, 'receive', '{"op":"PARAGRAPH_ADDED","msgId":"local-operation"}')
    ]
  });
  let connect;
  let send;
  const replies = [];
  await adapter.install({
    route: async () => {},
    routeWebSocket: async (_url, handler) => {
      connect = handler;
    }
  });
  connect({
    onMessage: handler => {
      send = handler;
    },
    send: message => replies.push(JSON.parse(message))
  });
  send('{"op":"INSERT_PARAGRAPH","msgId":"live-local"}');
  assert.deepEqual(
    replies.map(reply => reply.msgId),
    ['<msgId:1>', '<msgId:1>', 'live-local']
  );
  adapter.assertComplete();
});

test('fixture validation rejects unmatched REST request records', () => {
  assert.match(
    validateFixture({
      version: fixtureVersion,
      metadata: fixtureMetadata(),
      records: [
        {
          kind: 'rest',
          sequence: 1,
          rest: {
            direction: 'request',
            request: { method: 'GET', url: '/api/notebook/missing', headers: {}, bodyRaw: '' }
          }
        }
      ]
    }).join('\n'),
    /request.*no response/
  );
});

test('runtime IDs cannot collide with remote IDs and other payload drift still fails', async () => {
  for (const message of [
    '{"op":"INSERT_PARAGRAPH","msgId":"<msgId:1>"}',
    '{"op":"DELETE_PARAGRAPH","msgId":"runtime"}'
  ]) {
    const adapter = createPlaywrightFixtureAdapter({
      version: fixtureVersion,
      records: [
        wsRecord(1, 'receive', '{"op":"PARAGRAPH_ADDED","msgId":"remote"}'),
        wsRecord(2, 'send', '{"op":"INSERT_PARAGRAPH","msgId":"local"}')
      ]
    });
    let connect;
    let send;
    await adapter.install({
      route: async () => {},
      routeWebSocket: async (_url, handler) => {
        connect = handler;
      }
    });
    connect({
      onMessage: handler => {
        send = handler;
      },
      send: () => {}
    });
    assert.throws(() => send(message), /correlation mismatch|send mismatch/);
    assert.throws(() => adapter.assertComplete(), /correlation mismatch|send mismatch/);
  }
});

for (const reconnect of [false, true]) {
  for (const operation of ['stop', 'write']) {
    test(`${operation} rejects a second notebook WebSocket ${reconnect ? 'after reconnect' : 'while connected'}`, async () => {
      const recorder = createNotebookTransportRecorder(fixtureMetadata());
      const page = new EventEmitter();
      recorder.install(page);
      const socket = new EventEmitter();
      socket.url = () => 'ws://localhost/ws';
      page.emit('websocket', socket);
      socket.emit('framesent', { payload: '{"op":"GET_NOTE"}' });
      if (reconnect) {
        socket.emit('close');
      }
      const secondSocket = new EventEmitter();
      secondSocket.url = () => 'ws://localhost/ws';
      page.emit('websocket', secondSocket);
      secondSocket.emit('framereceived', { payload: '{"op":"NOTE"}' });

      const output = path.join(createRoot().root, 'multiple-sockets.json');
      if (operation === 'stop') {
        await assert.rejects(() => recorder.stop(), /one WebSocket connection per fixture/);
      }
      await assert.rejects(() => recorder.write(output), /one WebSocket connection per fixture/);
      assert.equal(existsSync(output), false);
    });
  }
}

test('fixture validation rejects both WebSocket payload representations', () => {
  for (const [payloadText, payloadBase64] of [
    ['text', 'YmluYXJ5'],
    ['', '']
  ]) {
    assert.deepEqual(
      validateFixture({
        version: fixtureVersion,
        metadata: fixtureMetadata(),
        records: [{ kind: 'websocket', sequence: 1, websocket: { direction: 'send', payloadText, payloadBase64 } }]
      }),
      ['records[0].websocket must contain exactly one of payloadText or payloadBase64']
    );
  }
});

test('Playwright adapter routes only the exact notebook WebSocket pathname', async () => {
  const adapter = createPlaywrightFixtureAdapter({
    records: [wsRecord(1, 'receive', 'reply')],
    version: fixtureVersion
  });
  let matcher;
  await adapter.install({
    route: () => undefined,
    routeWebSocket: pattern => {
      matcher = pattern;
    }
  });
  const matches = value => (matcher instanceof RegExp ? matcher.test(value) : matcher(new URL(value)));
  assert.equal(matches('ws://fixture.test/ws'), true);
  assert.equal(matches('ws://fixture.test/ws?token=value'), true);
  assert.equal(matches('ws://fixture.test/chat/ws'), false);
  assert.equal(matches('ws://fixture.test/chat?next=/ws'), false);
});

test('Playwright adapter replays reversed arrivals within a recorded REST request batch', async () => {
  const req = url => ({ bodyRaw: '', headers: { accept: 'application/json' }, method: 'GET', url });
  const a = '/api/notebook/note-a';
  const b = '/api/notebook/note-b';
  const adapter = createPlaywrightFixtureAdapter({
    version: fixtureVersion,
    records: [
      { kind: 'rest', sequence: 1, rest: { direction: 'request', request: req(a) } },
      { kind: 'rest', sequence: 2, rest: { direction: 'request', request: req(b) } },
      ...[a, b].map((url, index) => ({
        kind: 'rest',
        sequence: index + 3,
        rest: {
          direction: 'response',
          request: req(url),
          bodyJson: { url },
          headers: { 'content-type': 'application/json' },
          status: 200
        }
      }))
    ]
  });
  let routeHandler;
  await adapter.install({
    route: (_pattern, handler) => {
      routeHandler = handler;
    },
    routeWebSocket: () => undefined
  });
  const delivered = [];
  const call = url =>
    routeHandler(
      {
        fulfill: async () => {
          delivered.push(url);
        }
      },
      request('GET', `http://fixture.test${url}`)
    );
  await Promise.all([call(b), call(a)]);
  assert.deepEqual(delivered, [a, b]);
  adapter.assertComplete();
});

for (const fails of [false, true]) {
  test(`Playwright completion waits for final REST delivery and retains failure=${fails}`, async () => {
    const adapter = createPlaywrightFixtureAdapter({
      version: fixtureVersion,
      records: [
        {
          kind: 'rest',
          sequence: 1,
          rest: {
            direction: 'response',
            request: {
              bodyRaw: '',
              headers: { accept: 'application/json' },
              method: 'GET',
              url: '/api/notebook/note-a'
            },
            bodyJson: { id: 'note-a' },
            headers: { 'content-type': 'application/json' },
            status: 200
          }
        }
      ]
    });
    let routeHandler;
    await adapter.install({
      route: (_pattern, handler) => {
        routeHandler = handler;
      },
      routeWebSocket: () => undefined
    });
    let settle;
    const delivery = new Promise((resolve, reject) => {
      settle = () => (fails ? reject(new Error('delivery failed')) : resolve());
    });
    const routed = routeHandler({ fulfill: () => delivery }, request('GET', 'http://fixture.test/api/notebook/note-a'));
    try {
      assert.throws(() => adapter.assertComplete(), /unfulfilled REST/);
    } finally {
      settle();
      await routed.catch(() => undefined);
    }
    if (fails) {
      await assert.rejects(routed, /delivery failed/);
      assert.throws(() => adapter.assertComplete(), /delivery failed/);
    } else {
      await routed;
      adapter.assertComplete();
    }
  });
}

test('Playwright request batches reject a later sequential request across a response boundary', async () => {
  const records = ['note-a', 'note-b'].flatMap((id, index) => {
    const recorded = {
      bodyRaw: '',
      headers: { accept: 'application/json' },
      method: 'GET',
      url: `/api/notebook/${id}`
    };
    return [
      { kind: 'rest', sequence: index * 2 + 1, rest: { direction: 'request', request: recorded } },
      {
        kind: 'rest',
        sequence: index * 2 + 2,
        rest: {
          direction: 'response',
          request: recorded,
          bodyJson: { id },
          headers: { 'content-type': 'application/json' },
          status: 200
        }
      }
    ];
  });
  const adapter = createPlaywrightFixtureAdapter({ records, version: fixtureVersion });
  let routeHandler;
  await adapter.install({
    route: (_pattern, handler) => {
      routeHandler = handler;
    },
    routeWebSocket: () => undefined
  });
  const delivered = [];
  await assert.rejects(
    routeHandler(
      { fulfill: async value => delivered.push(value) },
      request('GET', 'http://fixture.test/api/notebook/note-b')
    ),
    /REST fixture request mismatch/
  );
  assert.deepEqual(delivered, []);
  assert.throws(() => adapter.assertComplete(), /REST fixture request mismatch/);
});

test('recorder orders a completed REST body after intervening WebSocket messages', async () => {
  const page = new EventEmitter();
  const socket = new EventEmitter();
  socket.url = () => 'ws://fixture.test/ws';
  const recorder = createNotebookTransportRecorder(fixtureMetadata());
  recorder.install(page);
  page.emit('websocket', socket);
  const live = request('GET', 'http://fixture.test/api/notebook/note-a');
  let completeBody;
  const capturedResponse = response(live, 200, '');
  capturedResponse.text = () =>
    new Promise(resolve => {
      completeBody = resolve;
    });
  page.emit('request', live);
  page.emit('response', capturedResponse);
  socket.emit('framereceived', { payload: 'before-body' });
  socket.emit('framesent', { payload: 'ack' });
  assert.equal(
    recorder.snapshot().records.some(record => record.rest?.direction === 'response'),
    false
  );
  const stopping = recorder.stop();
  completeBody('{"id":"note-a"}');
  page.emit('requestfinished', live);
  await stopping;
  const fixture = recorder.snapshot();
  assert.deepEqual(validateFixture(fixture), []);
  assert.deepEqual(
    fixture.records.map(record => (record.kind === 'rest' ? record.rest.direction : record.websocket.payloadText)),
    ['request', 'before-body', 'ack', 'response']
  );
  assert.deepEqual(fixture.records.at(-1).rest.bodyJson, { id: 'note-a' });
});

test('body read latency does not move a finished REST response past later WebSocket frames', async () => {
  const page = new EventEmitter();
  const socket = new EventEmitter();
  socket.url = () => 'ws://fixture.test/ws';
  const recorder = createNotebookTransportRecorder(fixtureMetadata());
  recorder.install(page);
  page.emit('websocket', socket);
  const live = request('GET', 'http://fixture.test/api/notebook/note-a');
  let completeRead;
  const capturedResponse = response(live, 200, '');
  capturedResponse.text = () =>
    new Promise(resolve => {
      completeRead = resolve;
    });
  page.emit('request', live);
  page.emit('response', capturedResponse);
  page.emit('requestfinished', live);
  socket.emit('framesent', { payload: 'body-read' });
  socket.emit('framereceived', { payload: 'after-body' });
  const stopping = recorder.stop();
  completeRead('{"id":"note-a"}');
  await stopping;
  const fixture = recorder.snapshot();
  assert.deepEqual(validateFixture(fixture), []);
  assert.deepEqual(
    fixture.records.map(record => (record.kind === 'rest' ? record.rest.direction : record.websocket.payloadText)),
    ['request', 'response', 'body-read', 'after-body']
  );
  assert.deepEqual(fixture.records[1].rest.bodyJson, { id: 'note-a' });
  assert.equal(page.listenerCount('requestfinished'), 0);
});

test('a response without a download completion event cannot be written as a complete fixture', async () => {
  const page = new EventEmitter();
  const recorder = createNotebookTransportRecorder(fixtureMetadata());
  recorder.install(page);
  const live = request('GET', 'http://fixture.test/api/notebook/note-a');
  page.emit('request', live);
  page.emit('response', response(live, 200, '{"id":"note-a"}'));
  await assert.rejects(() => recorder.stop(), /outstanding notebook request/);
  assert.equal(page.listenerCount('requestfinished'), 0);
  await assert.rejects(
    () => recorder.write(path.join(createRoot().root, 'incomplete.json')),
    /outstanding notebook request/
  );
});
