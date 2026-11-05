// @vitest-environment node

/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { once } from 'node:events';
import { setTimeout as delay } from 'node:timers/promises';

import { config, firstValueFrom, fromEvent } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { WebSocket, WebSocketServer, type RawData } from 'ws';

import { OP } from './interfaces/message-operator.interface';
import { Message } from './message';

interface ClientConnection {
  socket: WebSocket;
  startedAt: number;
  closed: Promise<unknown>;
}

const clients: ClientConnection[] = [];
const messages: Message[] = [];
const servers = new Set<WebSocketServer>();
const peers: WebSocket[] = [];
const serverErrors: unknown[] = [];
const unhandledErrors: unknown[] = [];
let maxConcurrent = 0;
let previousUnhandledError: typeof config.onUnhandledError;

// Only observe construction and physical closure; all handshakes, frames, errors,
// and close events go through the real ws implementation and a TCP listener.
class ObservedWebSocket extends WebSocket {
  constructor(url: string) {
    super(url);
    clients.push({
      socket: this,
      startedAt: performance.now(),
      closed: firstValueFrom(fromEvent(this, 'close'))
    });
    maxConcurrent = Math.max(
      maxConcurrent,
      clients.filter(client => client.socket.readyState !== WebSocket.CLOSED).length
    );
  }
}

const startServer = async (port = 0) => {
  const server = new WebSocketServer({ host: '127.0.0.1', port, path: '/ws' });
  servers.add(server);
  server.on('error', error => serverErrors.push(error));
  server.on('connection', peer => {
    peers.push(peer);
    peer.on('error', error => serverErrors.push(error));
  });
  await once(server, 'listening');
  const address = server.address();
  if (typeof address === 'string' || address === null) {
    throw new Error('Expected a TCP WebSocket listener');
  }
  return { server, port: address.port, url: `ws://127.0.0.1:${address.port}/ws` };
};

const stopServer = async (server: WebSocketServer) => {
  const closed = once(server, 'close');
  server.close();
  await closed;
  servers.delete(server);
};

const createMessage = (url: string) => {
  const message = new Message();
  messages.push(message);
  message.bootstrap({ principal: 'anonymous', ticket: 'integration-ticket', roles: '[]' }, url);
  return message;
};

const expectRoundTrip = async (message: Message, peer: WebSocket, noteId: string) => {
  const requests: string[] = [];
  const received: unknown[] = [];
  const onRequest = (data: RawData) => requests.push(data.toString());
  const subscription = message.receive(OP.NOTE).subscribe(data => received.push(data));
  peer.on('message', onRequest);
  try {
    message.ping();
    await expect.poll(() => requests.length, { timeout: 1000 }).toBe(1);
    expect(JSON.parse(requests[0])).toMatchObject({
      op: OP.PING,
      principal: 'anonymous',
      ticket: 'integration-ticket'
    });
    const data = { note: { id: noteId } };
    peer.send(JSON.stringify({ op: OP.NOTE, data }));
    await expect.poll(() => received, { timeout: 1000 }).toEqual([data]);
  } finally {
    subscription.unsubscribe();
    peer.off('message', onRequest);
  }
};

const destroyAndWait = async (message: Message) => {
  message.destroy();
  await expect
    .poll(() => clients.every(client => client.socket.readyState === WebSocket.CLOSED), { timeout: 1000 })
    .toBe(true);
  await Promise.all(clients.map(client => client.closed));
  expect(message.connectedStatus).toBe(false);
  expect(message.getWsInstance()).toBeNull();
};

beforeEach(() => {
  clients.length = 0;
  messages.length = 0;
  peers.length = 0;
  serverErrors.length = 0;
  unhandledErrors.length = 0;
  maxConcurrent = 0;
  previousUnhandledError = config.onUnhandledError;
  config.onUnhandledError = error => unhandledErrors.push(error);
  vi.stubGlobal('WebSocket', ObservedWebSocket);
  vi.spyOn(console, 'log').mockImplementation(() => {});
});

afterEach(async () => {
  try {
    for (const message of messages) {
      message.destroy();
    }
    // Failed assertions must not leave handles behind. Successful cases already
    // await the Message-owned shutdown in destroyAndWait, without termination.
    for (const { socket } of clients) {
      if (socket.readyState !== WebSocket.CLOSED) {
        socket.terminate();
      }
    }
    for (const peer of peers) {
      if (peer.readyState !== WebSocket.CLOSED) {
        peer.terminate();
      }
    }
    await Promise.all(clients.map(client => client.closed));
    await Promise.all([...servers].map(stopServer));
    // RxJS reports unhandled observer errors on a later timer turn.
    await delay(25);
    // eslint-disable-next-line vitest/no-standalone-expect -- Teardown must catch asynchronous RxJS errors in every scenario.
    expect(unhandledErrors).toEqual([]);
    // eslint-disable-next-line vitest/no-standalone-expect -- Server-side teardown errors must fail the scenario too.
    expect(serverErrors).toEqual([]);
  } finally {
    config.onUnhandledError = previousUnhandledError;
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  }
});

describe('Message with real WebSockets', () => {
  it.each([
    {
      name: 'an explicit abnormal close frame',
      code: 1011,
      disconnect: (peer: WebSocket) => peer.close(1011, 'retry')
    },
    { name: 'an abruptly terminated connection', code: 1006, disconnect: (peer: WebSocket) => peer.terminate() }
  ])(
    'recovers from $name with one delayed replacement',
    async ({ code, disconnect }) => {
      const { url } = await startServer();
      const message = createMessage(url);
      const closeCodes: number[] = [];
      message.closed().subscribe(event => closeCodes.push(event.code));
      await expect.poll(() => message.connectedStatus, { timeout: 1000 }).toBe(true);
      expect(peers).toHaveLength(1);

      disconnect(peers[0]);
      await clients[0].closed;
      expect(message.connectedStatus).toBe(false);
      expect(closeCodes).toEqual([code]);
      await delay(250);
      expect(clients).toHaveLength(1);

      await expect.poll(() => message.connectedStatus, { timeout: 2000 }).toBe(true);
      expect(clients).toHaveLength(2);
      expect(peers).toHaveLength(2);
      expect(clients[1].startedAt - clients[0].startedAt).toBeGreaterThanOrEqual(900);
      expect(clients.filter(client => client.socket.readyState === WebSocket.OPEN)).toHaveLength(1);
      expect(maxConcurrent).toBe(1);
      await expectRoundTrip(message, peers[1], 'recovered-after-server-close');

      await destroyAndWait(message);
      await delay(1100);
      expect(clients).toHaveLength(2);
    },
    10000
  );

  it('backs off a refused endpoint, recovers when it returns, and shuts down without unhandled errors', async () => {
    // Allocate an ephemeral local endpoint, then really stop listening. Both
    // failed attempts must therefore perform an actual refused TCP connection.
    const endpoint = await startServer();
    await stopServer(endpoint.server);
    const message = createMessage(endpoint.url);
    await clients[0].closed;
    expect(message.connectedStatus).toBe(false);
    await delay(250);
    expect(clients).toHaveLength(1);

    await expect.poll(() => clients.length, { timeout: 1500 }).toBe(2);
    await clients[1].closed;
    expect(message.connectedStatus).toBe(false);
    const returned = await startServer(endpoint.port);
    expect(returned.url).toBe(endpoint.url);
    await delay(250);
    expect(clients).toHaveLength(2);

    await expect.poll(() => message.connectedStatus, { timeout: 2500 }).toBe(true);
    expect(clients).toHaveLength(3);
    expect(peers).toHaveLength(1);
    expect(clients[1].startedAt - clients[0].startedAt).toBeGreaterThanOrEqual(900);
    expect(clients[2].startedAt - clients[1].startedAt).toBeGreaterThanOrEqual(1900);
    expect(clients.filter(client => client.socket.readyState === WebSocket.OPEN)).toHaveLength(1);
    expect(maxConcurrent).toBe(1);
    await expectRoundTrip(message, peers[0], 'recovered-after-refusal');

    await destroyAndWait(message);
    message.setOnline(false);
    message.setOnline(true);
    message.connect();
    await delay(1100);
    expect(clients).toHaveLength(3);
  }, 10000);

  it.each(['bootstrap', 'setters and connect', 'setters and bootstrap'] as const)(
    'registers a fresh principal-bound socket with %s so user-targeted replies reach it',
    async configuration => {
      const { server, url } = await startServer();
      const byUser = new Map<string, WebSocket>();
      const frames: Array<{ principal: string; op: OP; ticket: string }> = [];
      server.on('connection', peer => {
        let principal: string | undefined;
        peer.on('message', data => {
          const envelope: { principal: string; op: OP; ticket: string } = JSON.parse(data.toString());
          frames.push(envelope);
          if (principal === undefined) {
            principal = envelope.principal;
            byUser.set(principal, peer);
          }
        });
        peer.on('close', () => {
          if (principal !== undefined && byUser.get(principal) === peer) {
            byUser.delete(principal);
          }
        });
      });
      const message = new Message();
      messages.push(message);
      message.bootstrap({ principal: 'A', ticket: 'A-ticket', roles: '[]' }, url);
      await expect.poll(() => message.connectedStatus, { timeout: 1000 }).toBe(true);
      message.ping();
      await expect.poll(() => byUser.has('A'), { timeout: 1000 }).toBe(true);

      const ticket = { principal: 'B', ticket: 'B-ticket', roles: '[]' };
      if (configuration !== 'bootstrap') {
        message.setTicket(ticket);
        message.setWsUrl(url);
      }
      if (configuration === 'setters and connect') {
        message.connect();
      } else {
        message.bootstrap(ticket, url);
      }
      await clients[0].closed;
      await expect.poll(() => message.connectedStatus, { timeout: 1000 }).toBe(true);
      message.ping();
      await expect.poll(() => byUser.has('B'), { timeout: 1000 }).toBe(true);
      expect(frames).toEqual([
        expect.objectContaining({ op: OP.PING, principal: 'A', ticket: 'A-ticket' }),
        expect.objectContaining({ op: OP.PING, principal: 'B', ticket: 'B-ticket' })
      ]);
      const received: unknown[] = [];
      const subscription = message.receive(OP.NOTE).subscribe(data => received.push(data));
      try {
        byUser.get('B')!.send(JSON.stringify({ op: OP.NOTE, data: { note: { id: 'B-only' } } }));
        await expect.poll(() => received, { timeout: 1000 }).toEqual([{ note: { id: 'B-only' } }]);
        expect(byUser.has('A')).toBe(false);
        expect(clients).toHaveLength(2);
        expect(maxConcurrent).toBe(1);
        await destroyAndWait(message);
      } finally {
        subscription.unsubscribe();
      }
    }
  );

  it('moves requests to a newly configured endpoint only after the old physical socket closes', async () => {
    const first = await startServer();
    const second = await startServer();
    const firstFrames: Array<{ op: OP }> = [];
    const secondFrames: Array<{ op: OP }> = [];
    first.server.on('connection', peer => {
      peer.on('message', data => firstFrames.push(JSON.parse(data.toString())));
    });
    second.server.on('connection', peer => {
      peer.on('message', data => secondFrames.push(JSON.parse(data.toString())));
    });
    const message = createMessage(first.url);
    await expect.poll(() => message.connectedStatus, { timeout: 1000 }).toBe(true);
    message.ping();
    await expect.poll(() => firstFrames.length, { timeout: 1000 }).toBe(1);
    expect(firstFrames.map(frame => frame.op)).toEqual([OP.PING]);

    message.setWsUrl(second.url);
    message.connect();
    await clients[0].closed;
    await expect.poll(() => message.connectedStatus, { timeout: 1000 }).toBe(true);
    message.listNodes();
    await expect.poll(() => secondFrames.length, { timeout: 1000 }).toBe(1);
    expect(secondFrames.map(frame => frame.op)).toEqual([OP.LIST_NOTES]);
    expect(firstFrames.map(frame => frame.op)).toEqual([OP.PING]);
    expect(clients).toHaveLength(2);
    expect(clients[0].socket.readyState).toBe(WebSocket.CLOSED);
    expect(clients[1].socket.url).toBe(second.url);
    expect(clients.filter(client => client.socket.readyState === WebSocket.OPEN)).toHaveLength(1);
    expect(maxConcurrent).toBe(1);
    await expectRoundTrip(message, peers[1], 'new-endpoint');
    expect(firstFrames.map(frame => frame.op)).toEqual([OP.PING]);
    expect(secondFrames.map(frame => frame.op)).toEqual([OP.LIST_NOTES, OP.PING]);
    await destroyAndWait(message);
  });

  it('delivers commands in order on the open socket despite a writable disconnected status', async () => {
    const { server, url } = await startServer();
    const frames: Array<{ op: OP }> = [];
    server.on('connection', peer => {
      peer.on('message', data => frames.push(JSON.parse(data.toString())));
    });
    const message = createMessage(url);
    await expect.poll(() => message.connectedStatus, { timeout: 1000 }).toBe(true);
    expect(clients[0].socket.readyState).toBe(WebSocket.OPEN);

    message.connectedStatus = false;
    message.listNodes();
    message.connectedStatus = true;
    message.ping();

    await expect.poll(() => frames.length, { timeout: 1000 }).toBe(2);
    expect(frames.map(frame => frame.op)).toEqual([OP.LIST_NOTES, OP.PING]);
    expect(clients).toHaveLength(1);
    expect(clients[0].socket.readyState).toBe(WebSocket.OPEN);
    expect(maxConcurrent).toBe(1);
    await destroyAndWait(message);
  });

  it('delivers queued offline and connecting commands once in their original order', async () => {
    const { server, url } = await startServer();
    const received: Array<{ op: OP; msgId: string; data?: { paragraph?: string } }> = [];
    server.on('connection', peer => {
      peer.on('message', data => received.push(JSON.parse(data.toString())));
    });
    const message = createMessage(url);
    await expect.poll(() => message.connectedStatus, { timeout: 1000 }).toBe(true);
    message.setOnline(false);
    await clients[0].closed;
    message.listNodes();
    const first = message.commitParagraph('p1', undefined, 'offline edit', {}, {}, 'n1');
    message.setOnline(true);
    expect(message.connectedStatus).toBe(false);
    const second = message.commitParagraph('p1', undefined, 'newer edit', {}, {}, 'n1');
    await expect.poll(() => received.length, { timeout: 1000 }).toBe(3);
    expect(received.map(frame => frame.op)).toEqual([OP.LIST_NOTES, OP.COMMIT_PARAGRAPH, OP.COMMIT_PARAGRAPH]);
    expect(received.slice(1).map(frame => [frame.msgId, frame.data?.paragraph])).toEqual([
      [first, 'offline edit'],
      [second, 'newer edit']
    ]);
    expect(clients).toHaveLength(2);
    expect(maxConcurrent).toBe(1);
    await destroyAndWait(message);
  });

  it.each(['close', 'destroy'] as const)(
    'handles %s during a real pending connection without resurrection',
    async action => {
      const endpoint = await startServer();
      await stopServer(endpoint.server);
      const message = createMessage(endpoint.url);
      expect(clients[0].socket.readyState).toBe(WebSocket.CONNECTING);

      message[action]();
      await clients[0].closed;
      expect(message.connectedStatus).toBe(false);
      expect(message.getWsInstance()).toBeNull();
      message.setOnline(false);
      message.setOnline(true);
      await delay(1100);
      expect(clients).toHaveLength(1);
      await destroyAndWait(message);
    },
    5000
  );
});
