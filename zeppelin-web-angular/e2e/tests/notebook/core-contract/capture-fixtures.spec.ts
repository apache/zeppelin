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

import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { createServer } from 'node:http';
import type { AddressInfo } from 'node:net';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';

import { expect, test } from '@playwright/test';
import { WebSocketServer, type WebSocket as ServerWebSocket } from 'ws';

import {
  createNotebookTransportRecorder,
  createPlaywrightFixtureAdapter,
  validateFixture
} from '../../../core-contract/notebook-transport-fixture.mjs';
import { fixtureMetadata } from '../../../core-contract/fixture-doubles.mjs';
import { LoginTestUtil } from '../../../models/login-page.util';
import {
  addPageAnnotationBeforeEach,
  createTestNotebook,
  navigateToNotebookWithFallback,
  PAGES,
  performLoginIfRequired,
  waitForZeppelinReady
} from '../../../utils';

// Exercise real Playwright transport objects; fixture logic is covered by the Node suite.

const notebookRequest = {
  bodyRaw: '',
  headers: { accept: 'application/json' },
  method: 'GET',
  url: '/api/notebook/note-a'
};

const replayFixture = () => ({
  metadata: fixtureMetadata(),
  records: [
    { kind: 'rest', sequence: 1, rest: { direction: 'request', request: notebookRequest } },
    {
      kind: 'rest',
      sequence: 2,
      rest: {
        bodyJson: { id: 'note-a' },
        direction: 'response',
        headers: { 'content-type': 'application/json' },
        request: notebookRequest,
        status: 200
      }
    },
    {
      kind: 'websocket',
      sequence: 3,
      websocket: { direction: 'send', payloadText: '{"op":"GET_NOTE","msgId":"<msgId:1>"}' }
    },
    {
      kind: 'websocket',
      sequence: 4,
      websocket: { direction: 'receive', payloadText: '{"op":"NOTE","noteId":"note-a","msgId":"<msgId:1>"}' }
    }
  ],
  version: 1
});

// Serve a test page without a Zeppelin server.
const servePage = (page: import('@playwright/test').Page) =>
  page.route('http://fixture.test/', route =>
    route.fulfill({ body: '<html><body>fixture</body></html>', contentType: 'text/html' })
  );

test.describe('Notebook core transport fixture replay in a browser', () => {
  for (const notebookPath of ['/ws', '/ws?session=notebook']) {
    test(`replays only the exact notebook WebSocket pathname at ${notebookPath}`, async ({ page }) => {
      const adapter = createPlaywrightFixtureAdapter({
        metadata: fixtureMetadata(),
        records: [
          {
            kind: 'websocket',
            sequence: 1,
            websocket: { direction: 'send', payloadText: 'GET_NOTE' }
          },
          {
            kind: 'websocket',
            sequence: 2,
            websocket: { direction: 'receive', payloadText: 'NOTEBOOK_REPLY' }
          }
        ],
        version: 1
      });
      const exchange = (path: string) =>
        page.evaluate(
          socketPath =>
            new Promise<string>((resolve, reject) => {
              const socket = new WebSocket(`ws://fixture.test${socketPath}`);
              socket.onopen = () => socket.send('GET_NOTE');
              socket.onerror = () => reject(new Error(`WebSocket failed: ${socketPath}`));
              socket.onmessage = event => {
                socket.close();
                resolve(event.data as string);
              };
            }),
          path
        );

      await test.step('Given an unrelated WebSocket handler registered before notebook replay', async () => {
        await servePage(page);
        await page.routeWebSocket('**/*', socket => {
          socket.onMessage(() => socket.send('UNRELATED_REPLY'));
        });
        await adapter.install(page);
        await page.goto('http://fixture.test/');
      });

      await test.step('When unrelated paths contain ws, then they leave the notebook fixture unconsumed', async () => {
        expect(await exchange('/chat/ws')).toBe('UNRELATED_REPLY');
        expect(() => adapter.assertComplete()).toThrow('WebSocket fixture was never connected');
        expect(await exchange('/chat?next=/ws')).toBe('UNRELATED_REPLY');
        expect(() => adapter.assertComplete()).toThrow('WebSocket fixture was never connected');
      });

      await test.step('When the exact notebook pathname connects, then it receives and completes the fixture', async () => {
        expect(await exchange(notebookPath)).toBe('NOTEBOOK_REPLY');
        adapter.assertComplete();
      });
    });
  }

  test('replays a captured REST response and WebSocket exchange to a real page', async ({ page }) => {
    await servePage(page);
    const adapter = createPlaywrightFixtureAdapter(replayFixture());
    await adapter.install(page);
    await page.goto('http://fixture.test/');

    const body = await page.evaluate(async () =>
      (await fetch('/api/notebook/note-a', { headers: { accept: 'application/json' } })).json()
    );
    expect(body).toEqual({ id: 'note-a' });

    const received = await page.evaluate(
      () =>
        new Promise<string>(resolve => {
          const socket = new WebSocket('ws://fixture.test/ws');
          socket.onopen = () => socket.send(JSON.stringify({ msgId: 'm1', op: 'GET_NOTE' }));
          socket.onmessage = event => resolve(event.data as string);
        })
    );
    expect(JSON.parse(received)).toEqual({ msgId: 'm1', noteId: 'note-a', op: 'NOTE' });

    adapter.assertComplete();
  });

  test('replays a capture taken from a real server, including a request that set no Accept', async ({ page }) => {
    // Playwright may omit Accept during capture but expose */* during replay routing.
    const server = createServer((request, response) => {
      if (request.url === '/') {
        response.writeHead(200, { 'content-type': 'text/html' });
        response.end('<html><body>capture</body></html>');
        return;
      }
      response.writeHead(200, { 'content-type': 'application/json' });
      response.end(JSON.stringify({ id: 'note-a' }));
    });
    await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
    const port = (server.address() as AddressInfo).port;
    const base = `http://127.0.0.1:${port}`;
    const traffic = async () => {
      const deleted = await (await fetch('/api/notebook/note-a', { method: 'DELETE' })).json();
      const fetched = await (
        await fetch('/api/notebook/note-b', { method: 'GET', headers: { accept: 'application/json' } })
      ).json();
      return [deleted, fetched];
    };

    try {
      const recorder = createNotebookTransportRecorder(fixtureMetadata());
      recorder.install(page);
      await page.goto(base);
      expect(await page.evaluate(traffic)).toEqual([{ id: 'note-a' }, { id: 'note-a' }]);
      await recorder.stop();
      const fixture = recorder.snapshot();
      expect(validateFixture(fixture)).toEqual([]);

      const replayPage = await page.context().newPage();
      const adapter = createPlaywrightFixtureAdapter(fixture);
      await adapter.install(replayPage);
      await replayPage.goto(base);
      expect(await replayPage.evaluate(traffic)).toEqual([{ id: 'note-a' }, { id: 'note-a' }]);

      adapter.assertComplete();
      await replayPage.close();
    } finally {
      await new Promise<void>(resolve => {
        server.close(() => resolve());
      });
    }
  });

  for (const first of ['websocket', 'body'] as const) {
    test(`capture and replay preserve ${first}-first response completion`, async ({ page }) => {
      let socket: ServerWebSocket;
      let completeBody: (() => void) | undefined;
      const server = createServer((request, response) => {
        if (request.url === '/api/notebook/note-a') {
          response.writeHead(200, { 'content-type': 'application/json' });
          completeBody = () => response.end(JSON.stringify({ id: 'note-a' }));
          if (first === 'websocket') {
            response.flushHeaders();
            socket.send('before-body');
          } else {
            completeBody();
          }
          return;
        }
        response.writeHead(200, { 'content-type': 'text/html' });
        response.end('<html><body>capture</body></html>');
      });
      const sockets = new WebSocketServer({ server, path: '/ws' });
      sockets.on('connection', connection => {
        socket = connection;
        connection.on('message', message => {
          if (message.toString() === 'ack') {
            completeBody?.();
          } else if (message.toString() === 'body-read') {
            connection.send('after-body');
          }
        });
      });
      const traffic = async (order: 'websocket' | 'body') => {
        const observed: string[] = [];
        const connection = new WebSocket(`${location.origin.replace('http:', 'ws:')}/ws`);
        const message = new Promise<void>(resolve => {
          connection.onmessage = () => {
            observed.push('websocket');
            if (order === 'websocket') connection.send('ack');
            resolve();
          };
        });
        await new Promise<void>(resolve => {
          connection.onopen = () => resolve();
        });
        const response = fetch('/api/notebook/note-a')
          .then(value => value.json())
          .then(body => {
            observed.push(body.id);
            if (order === 'body') connection.send('body-read');
          });
        await Promise.all([response, message]);
        connection.close();
        return observed;
      };
      const recorder = createNotebookTransportRecorder(fixtureMetadata());
      try {
        await test.step('Given a real server with causally ordered HTTP and WebSocket responses', async () => {
          await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
          recorder.install(page);
          await page.goto(`http://127.0.0.1:${(server.address() as AddressInfo).port}`);
        });

        const live = await test.step('When the browser captures the complete exchange', async () => {
          const observed = await page.evaluate(traffic, first);
          await recorder.stop();
          return observed;
        });

        const fixture = recorder.snapshot();
        const replayPage = await page.context().newPage();
        try {
          await test.step('Then replay preserves the same body and message processing order', async () => {
            expect(live).toEqual(first === 'websocket' ? ['websocket', 'note-a'] : ['note-a', 'websocket']);
            expect(validateFixture(fixture)).toEqual([]);
            const adapter = createPlaywrightFixtureAdapter(fixture);
            await adapter.install(replayPage);
            await replayPage.goto(page.url());
            expect(await replayPage.evaluate(traffic, first)).toEqual(live);
            adapter.assertComplete();
          });
        } finally {
          await replayPage.close();
        }
      } finally {
        for (const connection of sockets.clients) connection.terminate();
        sockets.close();
        server.closeAllConnections();
        await new Promise<void>(resolve => server.close(() => resolve()));
      }
    });
  }

  test('leaves traffic outside the notebook API to the routes already registered', async ({ page }) => {
    await servePage(page);
    // An earlier handler is reached by fallback(), but bypassed by continue().
    let served = 0;
    await page.route('**/api/security/ticket', route => {
      served += 1;
      return route.fulfill({ body: '{"ticket":"t"}', contentType: 'application/json' });
    });

    const adapter = createPlaywrightFixtureAdapter(replayFixture());
    await adapter.install(page);
    await page.goto('http://fixture.test/');

    const status = await page.evaluate(async () => (await fetch('/api/security/ticket')).status);
    expect(status).toBe(200);
    expect(served).toBe(1);
  });
});

test.describe('Notebook core transport capture auth wiring', () => {
  test('the login helper reads the shiro.ini of the server being captured', async () => {
    // Use the capture server's shiro.ini rather than the repository configuration.
    const root = mkdtempSync(join(tmpdir(), 'capture-auth-'));
    const shiro = join(root, 'conf', 'shiro.ini');
    mkdirSync(dirname(shiro), { recursive: true });
    writeFileSync(shiro, '[users]\ncapture_user = capture_pass, admin\n');

    const previous = process.env.ZEPPELIN_E2E_SHIRO_INI;
    try {
      process.env.ZEPPELIN_E2E_SHIRO_INI = shiro;
      LoginTestUtil.resetCache();

      expect(await LoginTestUtil.isShiroEnabled()).toBe(true);
      const credentials = await LoginTestUtil.getTestCredentials();
      expect(credentials.capture_user).toEqual({
        username: 'capture_user',
        password: 'capture_pass',
        roles: ['admin']
      });
    } finally {
      if (previous === undefined) {
        delete process.env.ZEPPELIN_E2E_SHIRO_INI;
      } else {
        process.env.ZEPPELIN_E2E_SHIRO_INI = previous;
      }
      LoginTestUtil.resetCache();
      rmSync(root, { force: true, recursive: true });
    }
  });
});

test.describe('Notebook core transport capture', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK);

  test('records notebook REST and WebSocket traffic from a real Zeppelin page', { tag: '@live' }, async ({ page }) => {
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
    const { noteId } = await createTestNotebook(page);
    try {
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
    } finally {
      // Global API cleanup is disabled under CI=true, so always delete the note here.
      // Use the original page to keep cleanup traffic out of an unfinished capture.
      try {
        const deleted = await page.evaluate(async id => {
          const response = await fetch(`/api/notebook/${id}`, { method: 'DELETE' });
          return response.ok;
        }, noteId);
        if (!deleted) {
          console.warn(`capture note ${noteId} was not deleted; remove it manually`);
        }
      } catch (error) {
        // Preserve the capture error if cleanup also fails.
        console.warn(`capture note ${noteId} could not be deleted: ${error}`);
      }
    }
  });
});
