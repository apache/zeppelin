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

import { expect, test } from '@playwright/test';

import {
  createFixtureReplayAdapter,
  createNotebookTransportRecorder,
  createPlaywrightFixtureAdapter,
  fixtureVersion,
  isNotebookRestUrl,
  normalizeFixtureRecord,
  visitNormalizedFixtureRecords,
  validateFixture,
  webSocketPayloadMatches
} from '../../../core-contract/notebook-transport-fixture.mjs';
import {
  addPageAnnotationBeforeEach,
  createTestNotebook,
  navigateToNotebookWithFallback,
  PAGES,
  performLoginIfRequired,
  waitForZeppelinReady
} from '../../../utils';

type TestHandler = (value: unknown) => unknown;
type AdapterCall = [kind: string, pattern: unknown, handler: TestHandler];
const fixtureMetadata = () => ({
  coveredOperations: ['GET_NOTE'],
  knownExclusions: [],
  owner: 'zeppelin-web-angular',
  scenario: 'Notebook transport fixture test'
});

test.describe('Notebook core transport fixture replay', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK);

  test('accepts discriminated versioned REST and WebSocket records without changing order or shape', () => {
    const fixture = sampleFixture();
    const replayed: unknown[] = [];

    visitNormalizedFixtureRecords(fixture, record => replayed.push(record));

    expect(replayed).toEqual([
      {
        kind: 'websocket',
        sequence: 1,
        websocket: { direction: 'send', payloadText: '{"op":"GET_NOTE","msgId":"<msgId>"}' }
      },
      {
        kind: 'rest',
        rest: {
          bodyJson: { id: 'note-a', paragraphs: [{ id: 'paragraph-a' }] },
          direction: 'response',
          headers: { 'content-type': 'application/json' },
          request: { bodyRaw: '', headers: { accept: 'application/json' }, method: 'GET', url: '/api/notebook/note-a' },
          status: 200
        },
        sequence: 2
      }
    ]);
  });

  test('rejects unsupported versions, ordering loss, and missing discriminated fields', () => {
    expect(validateFixture({ metadata: fixtureMetadata(), version: 999, records: [validRestRecord()] })).toContain(
      'Unsupported fixture version 999'
    );
    expect(
      validateFixture({
        metadata: fixtureMetadata(),
        version: fixtureVersion,
        records: [validRestRecord({ sequence: 2 }), validRestRecord({ sequence: 1 })]
      })
    ).toContain('records[1].sequence must increase without reordering');

    const errors = validateFixture({
      metadata: fixtureMetadata(),
      version: fixtureVersion,
      records: [{ sequence: 1, kind: 'websocket', websocket: { direction: 'receive' } }]
    }).join('\n');

    expect(errors).toContain('websocket payloadText or payloadBase64 is required');
  });

  test('redacts sensitive fields without replacing stable ids or operation fields', () => {
    expect(
      normalizeFixtureRecord({
        id: 'stable-id',
        operation: 'LIST_REVISION_HISTORY',
        ticket: 'secret',
        principal: 'anonymous',
        data: { noteId: '2A94M5J1Z', revisionId: 'rev-1', stable: 'kept' }
      })
    ).toEqual({
      id: 'stable-id',
      operation: 'LIST_REVISION_HISTORY',
      ticket: '<ticket>',
      principal: '<principal>',
      data: { noteId: '2A94M5J1Z', revisionId: 'rev-1', stable: 'kept' }
    });
  });

  test('provides a Playwright route adapter for REST responses', async () => {
    const adapter = createFixtureReplayAdapter({
      records: [
        validRestRecord({
          sequence: 1,
          rest: restResponse('/api/notebook/note-a', { id: 'note-a', paragraphs: [{ id: 'paragraph-a' }] })
        })
      ],
      version: fixtureVersion
    });
    const fulfilled: unknown[] = [];
    await adapter.route(
      {
        fulfill: async value => fulfilled.push(value)
      },
      {
        headers: () => ({ accept: 'application/json' }),
        method: () => 'GET',
        postData: () => null,
        url: () => 'http://localhost:8080/api/notebook/note-a'
      }
    );

    expect(fulfilled).toEqual([
      {
        body: '{"id":"note-a","paragraphs":[{"id":"paragraph-a"}]}',
        contentType: 'application/json',
        headers: { 'content-type': 'application/json' },
        status: 200
      }
    ]);
    expect(() => adapter.assertComplete()).not.toThrow();
  });

  test('captures browser-level REST and WebSocket events with pre-write redaction', async () => {
    const handlers = new Map<string, TestHandler[]>();
    const page = {
      on: (eventName: string, handler: TestHandler) =>
        handlers.set(eventName, [...(handlers.get(eventName) ?? []), handler])
    };
    const socketHandlers = new Map<string, TestHandler[]>();
    const socket = {
      on: (eventName: string, handler: TestHandler) =>
        socketHandlers.set(eventName, [...(socketHandlers.get(eventName) ?? []), handler]),
      url: () => 'http://localhost:8080/ws'
    };
    const recorder = createNotebookTransportRecorder(fixtureMetadata());

    recorder.install(page);
    handlers.get('request')?.[0]?.(
      request('POST', 'http://localhost:8080/api/notebook', '{"ticket":"secret","id":"stable-id"}')
    );
    handlers.get('response')?.[0]?.(
      response(request('GET', 'http://localhost:8080/api/notebook/stable-id'), 200, '{"id":"stable-id"}')
    );
    handlers.get('websocket')?.[0]?.(socket);
    socketHandlers.get('framesent')?.[0]?.({ payload: '{"op":"GET_NOTE","msgId":"runtime"}' });
    socketHandlers.get('framereceived')?.[0]?.({ payload: '{"op":"NOTE","noteId":"stable-id"}' });

    await recorder.stop();
    const fixture = recorder.snapshot();

    expect(validateFixture(fixture)).toEqual([]);
    expect(fixture.records.map(record => record.rest?.direction ?? record.websocket?.direction)).toEqual([
      'request',
      'response',
      'send',
      'receive'
    ]);
    expect(fixture.records[0].rest.request.bodyJson).toEqual({ id: 'stable-id', ticket: '<ticket>' });
    expect(fixture.records[2].websocket.payloadText).toBe('{"op":"GET_NOTE","msgId":"<msgId>"}');
  });

  test('redacts WebSocket JSON payloads and normalizes REST query values before writing', async ({}, testInfo) => {
    const handlers = new Map<string, TestHandler[]>();
    const page = {
      on: (eventName: string, handler: TestHandler) =>
        handlers.set(eventName, [...(handlers.get(eventName) ?? []), handler])
    };
    const socketHandlers = new Map<string, TestHandler[]>();
    const socket = {
      on: (eventName: string, handler: TestHandler) =>
        socketHandlers.set(eventName, [...(socketHandlers.get(eventName) ?? []), handler]),
      url: () => 'http://localhost:8080/ws'
    };
    const recorder = createNotebookTransportRecorder(fixtureMetadata());
    const fixturePath = testInfo.outputPath('notebook-transport.json');

    recorder.install(page);
    handlers.get('request')?.[0]?.(
      request(
        'GET',
        'http://localhost:8080/api/notebook/note-a?ticket=secret-ticket&token=secret-token&msgId=runtime&view=stable'
      )
    );
    handlers.get('websocket')?.[0]?.(socket);
    socketHandlers.get('framesent')?.[0]?.({
      payload:
        '{"op":"GET_NOTE","id":"stable-id","noteId":"note-a","ticket":"secret-ticket","principal":"alice","msgId":"runtime"}'
    });

    const fixture = await recorder.write(fixturePath);

    expect(JSON.stringify(fixture)).not.toContain('secret-ticket');
    expect(JSON.stringify(fixture)).not.toContain('secret-token');
    expect(JSON.stringify(fixture)).not.toContain('alice');
    expect(fixture.records[0].rest.request.url).toBe(
      '/api/notebook/note-a?ticket=%3Cticket%3E&token=%3Ctoken%3E&msgId=%3CmsgId%3E&view=stable'
    );
    expect(fixture.records[1].websocket.payloadText).toContain('"op":"GET_NOTE"');
    expect(fixture.records[1].websocket.payloadText).toContain('"id":"stable-id"');
    expect(fixture.records[1].websocket.payloadText).toContain('"noteId":"note-a"');
  });

  test('matches replayed query placeholders and JSON payloads deterministically', async () => {
    const fulfilled: unknown[] = [];
    const adapter = createFixtureReplayAdapter({
      records: [
        validRestRecord({
          sequence: 1,
          rest: restResponse('/api/notebook/note-a?ticket=%3Cticket%3E&msgId=%3CmsgId%3E', { id: 'note-a' })
        })
      ],
      version: fixtureVersion
    });

    await adapter.route(
      { fulfill: async value => fulfilled.push(value) },
      request('GET', 'http://localhost:8080/api/notebook/note-a?ticket=runtime-secret&msgId=runtime-id')
    );

    expect(fulfilled).toHaveLength(1);
    expect(webSocketPayloadMatches('{"op":"GET_NOTE","msgId":"<msgId>"}', '{"msgId":"runtime","op":"GET_NOTE"}')).toBe(
      true
    );
  });

  test('waits for pending response body reads before writing', async ({}, testInfo) => {
    const handlers = new Map<string, TestHandler[]>();
    const page = {
      on: (eventName: string, handler: TestHandler) =>
        handlers.set(eventName, [...(handlers.get(eventName) ?? []), handler])
    };
    const recorder = createNotebookTransportRecorder(fixtureMetadata());
    let resolveBody: (value: string) => void = () => undefined;

    recorder.install(page);
    handlers.get('response')?.[0]?.(
      response(request('GET', 'http://localhost:8080/api/notebook/note-a'), 200, () => {
        return new Promise<string>(resolve => {
          resolveBody = resolve;
        });
      })
    );
    const writePromise = recorder.write(testInfo.outputPath('notebook-transport.json'));
    resolveBody('{"id":"note-a"}');
    const fixture = await writePromise;

    expect(fixture.records[0].rest.bodyJson).toEqual({ id: 'note-a' });
  });

  test('limits REST capture and replay to notebook APIs', async () => {
    expect(isNotebookRestUrl('http://localhost:8080/api/notebook')).toBe(true);
    expect(isNotebookRestUrl('http://localhost:8080/api/notebook/note-a')).toBe(true);
    expect(isNotebookRestUrl('http://localhost:8080/api/security/ticket')).toBe(false);

    const adapter = createFixtureReplayAdapter({
      records: [validRestRecord({ sequence: 1, rest: restResponse('/api/notebook/note-a', { id: 'note-a' }) })],
      version: fixtureVersion
    });
    const continued: unknown[] = [];

    await adapter.route(
      { continue: async () => continued.push('continue') },
      request('GET', 'http://localhost/api/security/ticket')
    );

    expect(continued).toEqual(['continue']);
  });

  test('consumes repeated REST responses in captured sequence and fails on exhausted or out-of-order requests', async () => {
    const adapter = createFixtureReplayAdapter({
      records: [
        validRestRecord({ sequence: 1, rest: restResponse('/api/notebook/a', { id: 'a' }) }),
        validRestRecord({ sequence: 2, rest: restResponse('/api/notebook/a', { id: 'a-2' }) }),
        validRestRecord({ sequence: 3, rest: restResponse('/api/notebook/b', { id: 'b' }) })
      ],
      version: fixtureVersion
    });
    const bodies: unknown[] = [];
    const route = { fulfill: async value => bodies.push(value.body) };

    await adapter.route(route, request('GET', 'http://localhost:8080/api/notebook/a'));
    await adapter.route(route, request('GET', 'http://localhost:8080/api/notebook/a'));
    await expect(adapter.route(route, request('GET', 'http://localhost:8080/api/notebook/a'))).rejects.toThrow(
      'expected GET /api/notebook/b'
    );

    expect(bodies).toEqual(['{"id":"a"}', '{"id":"a-2"}']);
  });

  test('installs fixture-only REST and WebSocket routes through the Playwright page adapter API', async () => {
    const calls: AdapterCall[] = [];
    const page = {
      route: async (pattern: unknown, handler: TestHandler) => calls.push(['route', pattern, handler]),
      routeWebSocket: async (pattern: unknown, handler: TestHandler) => calls.push(['routeWebSocket', pattern, handler])
    };

    const adapter = createPlaywrightFixtureAdapter(sampleFixtureWithTwoWebSocketRoundTrips());
    await adapter.install(page);

    expect(calls[0][0]).toBe('route');
    expect(calls[1][0]).toBe('routeWebSocket');
    const wsSends: unknown[] = [];
    const serverSends: unknown[] = [];
    const clientMessageHandlers: ((message: string) => void)[] = [];
    calls[1][2]({
      connectToServer: () => ({ send: message => serverSends.push(message) }),
      onMessage: handler => clientMessageHandlers.push(handler),
      send: message => wsSends.push(message)
    });
    clientMessageHandlers[0]('{"op":"GET_NOTE","msgId":"runtime-1"}');
    clientMessageHandlers[0]('{"op":"RUN_PARAGRAPH","paragraphId":"paragraph-b"}');

    expect(serverSends).toEqual([]);
    expect(wsSends).toEqual(['{"op":"NOTE","noteId":"note-a"}', '{"op":"PARAGRAPH","paragraphId":"paragraph-b"}']);
    expect(() => adapter.assertComplete()).not.toThrow();
    expect(() => clientMessageHandlers[0]('{"op":"EXTRA"}')).toThrow('WebSocket fixture messages exhausted');
  });

  test('only forwards WebSocket client messages when passthrough is explicit', async () => {
    const calls: AdapterCall[] = [];
    const serverSends: unknown[] = [];
    const page = {
      route: async () => undefined,
      routeWebSocket: async (_pattern: unknown, handler: TestHandler) =>
        calls.push(['routeWebSocket', _pattern, handler])
    };

    const adapter = createPlaywrightFixtureAdapter(sampleFixtureWithReceive(), { passthrough: true });
    await adapter.install(page);

    const clientMessageHandlers: ((message: string) => void)[] = [];
    calls[0][2]({
      connectToServer: () => ({ send: message => serverSends.push(message) }),
      onMessage: handler => clientMessageHandlers.push(handler),
      send: () => undefined
    });
    clientMessageHandlers[0]('{"op":"GET_NOTE","msgId":"runtime-1"}');

    expect(serverSends).toEqual(['{"op":"GET_NOTE","msgId":"runtime-1"}']);
    expect(() => adapter.assertComplete()).not.toThrow();
  });

  test('fails on WebSocket send payload mismatch instead of replaying a stale receive', async () => {
    const calls: AdapterCall[] = [];
    const page = {
      route: async () => undefined,
      routeWebSocket: async (_pattern: unknown, handler: TestHandler) =>
        calls.push(['routeWebSocket', _pattern, handler])
    };

    await createPlaywrightFixtureAdapter(sampleFixtureWithReceive()).install(page);

    const clientMessageHandlers: ((message: string) => void)[] = [];
    calls[0][2]({
      connectToServer: () => {
        throw new Error('should not connect in fixture-only mode');
      },
      onMessage: handler => clientMessageHandlers.push(handler),
      send: () => undefined
    });

    expect(() => clientMessageHandlers[0]('{"op":"RUN_PARAGRAPH"}')).toThrow('WebSocket fixture send mismatch');
  });

  test('records notebook REST and WebSocket traffic from a real Zeppelin page', { tag: '@live' }, async ({ page }) => {
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
    const { noteId } = await createTestNotebook(page);
    const recorderPage = await page.context().newPage();
    const recorder = createNotebookTransportRecorder(fixtureMetadata());

    recorder.install(recorderPage);
    await navigateToNotebookWithFallback(recorderPage, noteId);
    await recorderPage.evaluate(async id => {
      await fetch(`/api/notebook/${id}`, { headers: { accept: 'application/json' } });
    }, noteId);
    await expect
      .poll(async () => recorder.snapshot().records.some(record => record.kind === 'websocket'), { timeout: 15000 })
      .toBe(true);
    await recorder.stop();
    const fixture = recorder.snapshot();
    await recorderPage.close();

    expect(validateFixture(fixture)).toEqual([]);
    expect(fixture.records.some(record => record.kind === 'rest')).toBe(true);
    expect(fixture.records.some(record => record.kind === 'websocket')).toBe(true);
  });
});

const sampleFixture = () => ({
  records: [
    {
      kind: 'websocket',
      sequence: 1,
      websocket: { direction: 'send', payloadText: '{"op":"GET_NOTE","msgId":"client-1"}' }
    },
    validRestRecord({ sequence: 2 })
  ],
  version: fixtureVersion
});

const sampleFixtureWithReceive = () => ({
  records: [
    {
      kind: 'websocket',
      sequence: 1,
      websocket: { direction: 'send', payloadText: '{"op":"GET_NOTE","msgId":"<msgId>"}' }
    },
    {
      kind: 'websocket',
      sequence: 2,
      websocket: { direction: 'receive', payloadText: '{"op":"NOTE"}' }
    }
  ],
  version: fixtureVersion
});

const sampleFixtureWithTwoWebSocketRoundTrips = () => ({
  records: [
    {
      kind: 'websocket',
      sequence: 1,
      websocket: { direction: 'send', payloadText: '{"op":"GET_NOTE","msgId":"<msgId>"}' }
    },
    {
      kind: 'websocket',
      sequence: 2,
      websocket: { direction: 'receive', payloadText: '{"op":"NOTE","noteId":"note-a"}' }
    },
    {
      kind: 'websocket',
      sequence: 3,
      websocket: {
        direction: 'send',
        payloadText: '{"op":"RUN_PARAGRAPH","paragraphId":"paragraph-b"}'
      }
    },
    {
      kind: 'websocket',
      sequence: 4,
      websocket: {
        direction: 'receive',
        payloadText: '{"op":"PARAGRAPH","paragraphId":"paragraph-b"}'
      }
    }
  ],
  version: fixtureVersion
});

const validRestRecord = (overrides = {}) => ({
  kind: 'rest',
  rest: restResponse('/api/notebook/note-a', { id: 'note-a', paragraphs: [{ id: 'paragraph-a' }] }),
  sequence: 1,
  ...overrides
});

const restResponse = (url: string, bodyJson: unknown) => ({
  bodyJson,
  direction: 'response',
  headers: { 'content-type': 'application/json' },
  request: { bodyRaw: '', headers: { accept: 'application/json' }, method: 'GET', url },
  status: 200
});

const request = (method: string, url: string, body = '', headers = { accept: 'application/json' }) => ({
  headers: () => headers,
  method: () => method,
  postData: () => body,
  url: () => url
});

const response = (
  sourceRequest: ReturnType<typeof request>,
  status: number,
  body: string | (() => Promise<string>),
  headers = { 'content-type': 'application/json' }
) => ({
  headers: () => headers,
  request: () => sourceRequest,
  status: () => status,
  text: async () => (typeof body === 'function' ? body() : body)
});
