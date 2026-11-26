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

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { OP } from './interfaces/message-operator.interface';
import { Message } from './message';

class ControlledWebSocket extends EventTarget {
  static instances: ControlledWebSocket[] = [];
  static attempts = 0;
  static failuresRemaining = 0;
  static maxConcurrent = 0;

  readyState = 0;
  onopen: ((event: Event) => void) | null = null;
  onclose: ((event: CloseEvent) => void) | null = null;
  onerror: ((event: Event) => void) | null = null;
  onmessage: ((event: MessageEvent) => void) | null = null;
  readonly sent: string[] = [];
  readonly close = vi.fn(() => {
    if (this.readyState < 2) {
      this.readyState = 2;
    }
  });

  constructor(readonly url: string) {
    super();
    ControlledWebSocket.attempts++;
    if (ControlledWebSocket.failuresRemaining > 0) {
      ControlledWebSocket.failuresRemaining--;
      throw new Error('WebSocket constructor failed');
    }
    ControlledWebSocket.instances.push(this);
    ControlledWebSocket.maxConcurrent = Math.max(
      ControlledWebSocket.maxConcurrent,
      ControlledWebSocket.instances.filter(socket => socket.readyState !== 3).length
    );
  }

  send(data: string): void {
    if (this.readyState !== 1) {
      throw new Error('Socket is not open');
    }
    this.sent.push(data);
  }

  open(): void {
    this.readyState = 1;
    this.onopen?.(new Event('open'));
  }

  fail(): void {
    this.onerror?.(new Event('error'));
  }

  finishClose(code = 1006): void {
    this.readyState = 3;
    const event = new CloseEvent('close', { code, wasClean: code === 1000 });
    this.dispatchEvent(event);
    this.onclose?.(event);
  }

  receive(data: unknown): void {
    this.onmessage?.(new MessageEvent('message', { data: JSON.stringify(data) }));
  }
}

const ticket = { principal: 'anonymous', ticket: 'ticket', roles: '[]' };
const url = 'ws://zeppelin.test/ws';
const messages: Message[] = [];
const createMessage = () => {
  const message = new Message();
  messages.push(message);
  return message;
};
const latestSocket = () => ControlledWebSocket.instances[ControlledWebSocket.instances.length - 1];

beforeEach(() => {
  vi.useFakeTimers();
  ControlledWebSocket.instances = [];
  ControlledWebSocket.attempts = 0;
  ControlledWebSocket.failuresRemaining = 0;
  ControlledWebSocket.maxConcurrent = 0;
  vi.stubGlobal('WebSocket', ControlledWebSocket);
  vi.spyOn(console, 'log').mockImplementation(() => {});
});

afterEach(() => {
  for (const message of messages.splice(0)) {
    message.destroy();
  }
  for (const socket of ControlledWebSocket.instances) {
    if (socket.readyState !== 3) {
      socket.finishClose(1000);
    }
  }
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('Message connection lifecycle', () => {
  it('requires connection intent, stores offline bootstrap, and makes repeated bootstrap idempotent', () => {
    const message = createMessage();
    message.setOnline(false);
    message.setOnline(true);
    vi.advanceTimersByTime(60000);
    expect(ControlledWebSocket.attempts).toBe(0);

    message.setOnline(false);
    message.bootstrap(ticket, url);
    message.bootstrap({ ...ticket, ticket: 'new-ticket' }, url);
    vi.advanceTimersByTime(60000);
    expect(ControlledWebSocket.attempts).toBe(0);

    message.setOnline(true);
    const subject = message.getWsInstance();
    message.setOnline(true);
    message.connect();
    message.bootstrap({ ...ticket, ticket: 'new-ticket' }, url);
    expect(ControlledWebSocket.attempts).toBe(1);
    expect(message.getWsInstance()).toBe(subject);
    expect(latestSocket().url).toBe(url);

    latestSocket().open();
    message.connect();
    message.bootstrap({ ...ticket, ticket: 'new-ticket' }, url);
    message.ping();
    expect(ControlledWebSocket.attempts).toBe(1);
    expect(JSON.parse(latestSocket().sent[0])).toMatchObject({
      op: OP.PING,
      principal: ticket.principal,
      ticket: 'new-ticket'
    });
  });

  it('uses refreshed credentials for a command queued while initially connecting', () => {
    const message = createMessage();
    message.bootstrap(ticket, url);
    message.listNodes();
    message.bootstrap({ ...ticket, ticket: 'refreshed-ticket' }, url);
    latestSocket().open();
    expect(latestSocket().sent.map(data => JSON.parse(data))).toEqual([
      expect.objectContaining({ op: OP.LIST_NOTES, principal: ticket.principal, ticket: 'refreshed-ticket' })
    ]);
    expect(ControlledWebSocket.attempts).toBe(1);
  });

  it.each(['connect', 'bootstrap'] as const)(
    'uses the original connection identity after setters run before %s',
    configuration => {
      const message = createMessage();
      message.bootstrap(ticket, url);
      const previous = latestSocket();
      previous.open();
      const nextTicket = { ...ticket, principal: 'next-user', ticket: 'next-ticket' };
      const nextUrl = 'ws://next-zeppelin.test/ws';
      message.setTicket(nextTicket);
      message.setWsUrl(nextUrl);
      if (configuration === 'connect') {
        message.connect();
      } else {
        message.bootstrap(nextTicket, nextUrl);
      }
      expect(ControlledWebSocket.attempts).toBe(1);
      previous.finishClose(1000);
      const replacement = latestSocket();
      expect(replacement.url).toBe(nextUrl);
      replacement.open();
      message.ping();
      expect(replacement.sent.map(data => JSON.parse(data))).toEqual([
        expect.objectContaining({ op: OP.PING, principal: 'next-user', ticket: 'next-ticket' })
      ]);
      expect(ControlledWebSocket.attempts).toBe(2);
      expect(ControlledWebSocket.maxConcurrent).toBe(1);
    }
  );

  it.each(['offline', 'retry'] as const)('cancels the old identity backlog and retry while %s', state => {
    const message = createMessage();
    message.setOnline(false);
    message.bootstrap(ticket, url);
    if (state === 'retry') {
      message.setOnline(true);
      latestSocket().finishClose();
    }
    message.commitParagraph('p1', undefined, 'old-user edit', {}, {}, 'n1');
    message.setTicket({ ...ticket, principal: 'next-user', ticket: 'next-ticket' });
    message.connect();
    message.listNodes();
    message.setOnline(true);
    latestSocket().open();
    vi.advanceTimersByTime(2000);
    expect(latestSocket().sent.map(data => JSON.parse(data))).toEqual([
      expect.objectContaining({ op: OP.LIST_NOTES, principal: 'next-user', ticket: 'next-ticket' })
    ]);
    expect(ControlledWebSocket.attempts).toBe(state === 'offline' ? 1 : 2);
    expect(ControlledWebSocket.maxConcurrent).toBe(1);
  });

  it('does not flush a previous principal backlog when its connecting socket opens after a setter change', () => {
    const message = createMessage();
    message.bootstrap(ticket, url);
    const previous = latestSocket();
    message.commitParagraph('p1', undefined, 'old-user edit', {}, {}, 'n1');
    message.setTicket({ ...ticket, principal: 'next-user', ticket: 'next-ticket' });
    previous.open();
    expect(previous.sent).toEqual([]);
    expect(message.connectedStatus).toBe(false);
    previous.finishClose(1000);
    latestSocket().open();
    message.ping();
    expect(latestSocket().sent.map(data => JSON.parse(data))).toEqual([
      expect.objectContaining({ op: OP.PING, principal: 'next-user', ticket: 'next-ticket' })
    ]);
    expect(ControlledWebSocket.maxConcurrent).toBe(1);
  });

  it('moves a command to the new principal instead of sending it on the old open socket', () => {
    const message = createMessage();
    message.bootstrap(ticket, url);
    const previous = latestSocket();
    previous.open();
    message.setTicket({ ...ticket, principal: 'next-user', ticket: 'next-ticket' });
    message.listNodes();
    expect(previous.sent).toEqual([]);
    previous.finishClose(1000);
    latestSocket().open();
    expect(latestSocket().sent.map(data => JSON.parse(data))).toEqual([
      expect.objectContaining({ op: OP.LIST_NOTES, principal: 'next-user', ticket: 'next-ticket' })
    ]);
    expect(ControlledWebSocket.attempts).toBe(2);
    expect(ControlledWebSocket.maxConcurrent).toBe(1);
  });

  it('keeps sending in order on an open socket while its public status is changed', () => {
    const message = createMessage();
    message.bootstrap(ticket, url);
    latestSocket().open();
    message.connectedStatus = false;
    message.listNodes();
    message.connectedStatus = true;
    message.ping();
    expect(latestSocket().sent.map(data => JSON.parse(data).op)).toEqual([OP.LIST_NOTES, OP.PING]);
    expect(ControlledWebSocket.attempts).toBe(1);
  });

  it('keeps connecting commands queued with current credentials even if public status is set true', () => {
    const message = createMessage();
    message.bootstrap(ticket, url);
    message.connectedStatus = true;
    message.listNodes();
    message.setTicket({ ...ticket, ticket: 'refreshed-ticket' });
    message.connect();
    expect(latestSocket().sent).toEqual([]);
    latestSocket().open();
    message.ping();
    expect(latestSocket().sent.map(data => JSON.parse(data))).toEqual([
      expect.objectContaining({ op: OP.LIST_NOTES, ticket: 'refreshed-ticket' }),
      expect.objectContaining({ op: OP.PING, ticket: 'refreshed-ticket' })
    ]);
    expect(ControlledWebSocket.attempts).toBe(1);
  });

  it.each(['connecting', 'open', 'retry', 'offline', 'closing'] as const)(
    'replaces the server-bound principal while %s without overlapping sockets',
    state => {
      const message = createMessage();
      message.bootstrap(ticket, url);
      const previous = latestSocket();
      if (state === 'open' || state === 'offline' || state === 'closing') {
        previous.open();
      }
      if (state === 'retry') {
        previous.finishClose();
      } else if (state === 'offline') {
        message.setOnline(false);
        message.listNodes();
      } else if (state === 'closing') {
        message.close();
      }

      message.bootstrap({ ...ticket, principal: 'next-user', ticket: 'next-ticket' }, url);
      expect(ControlledWebSocket.attempts).toBe(state === 'retry' ? 2 : 1);
      if (previous.readyState !== 3) {
        previous.finishClose(1000);
      }
      expect(ControlledWebSocket.attempts).toBe(state === 'offline' ? 1 : 2);
      if (state === 'offline') {
        message.setOnline(true);
      }
      const replacement = latestSocket();
      expect(replacement).not.toBe(previous);
      replacement.open();
      message.ping();
      expect(replacement.sent.map(data => JSON.parse(data))).toEqual([
        expect.objectContaining({ op: OP.PING, principal: 'next-user', ticket: 'next-ticket' })
      ]);
      expect(ControlledWebSocket.attempts).toBe(2);
      expect(ControlledWebSocket.maxConcurrent).toBe(1);
    }
  );

  it('uses a changed endpoint even when a disconnected-status subscriber requests connection', () => {
    const message = createMessage();
    message.bootstrap(ticket, url);
    const previous = latestSocket();
    previous.open();
    message.connectedStatus$.subscribe(connected => {
      if (!connected) {
        message.connect();
      }
    });

    message.bootstrap(ticket, 'ws://other-zeppelin.test/ws');
    previous.finishClose(1000);
    expect(latestSocket().url).toBe('ws://other-zeppelin.test/ws');
    expect(ControlledWebSocket.attempts).toBe(2);
    expect(ControlledWebSocket.maxConcurrent).toBe(1);
  });

  it.each(['close', 'destroy'] as const)('honors reentrant %s while reconciling a changed connection', action => {
    const message = createMessage();
    message.bootstrap(ticket, url);
    const previous = latestSocket();
    previous.open();
    const subscription = message.connectedStatus$.subscribe(connected => {
      if (!connected) {
        subscription.unsubscribe();
        message[action]();
      }
    });
    message.setWsUrl('ws://next-zeppelin.test/ws');
    message.connect();
    previous.finishClose(1000);
    vi.advanceTimersByTime(60000);
    expect(ControlledWebSocket.attempts).toBe(1);
    expect(message.connectedStatus).toBe(false);
    expect(() => message.listNodes()).toThrow('Bootstrap first');
  });

  it('uses the latest reentrant identity rather than an earlier replacement snapshot', () => {
    const message = createMessage();
    message.bootstrap(ticket, url);
    const previous = latestSocket();
    previous.open();
    const subscription = message.connectedStatus$.subscribe(connected => {
      if (!connected) {
        subscription.unsubscribe();
        message.setTicket({ ...ticket, principal: 'third-user', ticket: 'third-ticket' });
        message.setWsUrl('ws://third-zeppelin.test/ws');
        message.connect();
      }
    });
    message.setTicket({ ...ticket, principal: 'next-user', ticket: 'next-ticket' });
    message.setWsUrl('ws://next-zeppelin.test/ws');
    message.connect();
    previous.finishClose(1000);
    latestSocket().open();
    message.ping();
    expect(ControlledWebSocket.instances.map(socket => socket.url)).toEqual([url, 'ws://third-zeppelin.test/ws']);
    expect(latestSocket().sent.map(data => JSON.parse(data))).toEqual([
      expect.objectContaining({ op: OP.PING, principal: 'third-user', ticket: 'third-ticket' })
    ]);
    expect(ControlledWebSocket.maxConcurrent).toBe(1);
  });

  it('preserves offline and connecting send order before the host rehydrates the note', () => {
    const message = createMessage();
    message.bootstrap(ticket, url);
    const previous = latestSocket();
    previous.open();
    message.setOnline(false);
    message.listNodes();
    const first = message.commitParagraph('p1', undefined, 'offline edit', {}, {}, 'n1');
    message.setOnline(true);
    previous.finishClose(1000);
    const second = message.commitParagraph('p1', undefined, 'newer edit', {}, {}, 'n1');
    message.bootstrap({ ...ticket, ticket: 'refreshed-ticket' }, url);
    message.connectedStatus$.subscribe(connected => {
      if (connected) {
        message.getNote('n1');
      }
    });
    latestSocket().open();

    const frames = latestSocket().sent.map(data => JSON.parse(data));
    expect(frames.map(frame => frame.op)).toEqual([
      OP.LIST_NOTES,
      OP.COMMIT_PARAGRAPH,
      OP.COMMIT_PARAGRAPH,
      OP.GET_NOTE
    ]);
    expect(frames.slice(1, 3).map(frame => [frame.msgId, frame.data.paragraph])).toEqual([
      [first, 'offline edit'],
      [second, 'newer edit']
    ]);
    expect(frames.map(frame => frame.ticket)).toEqual(Array(4).fill('refreshed-ticket'));
    expect(ControlledWebSocket.maxConcurrent).toBe(1);
  });

  it('discards unsent work when an explicit close cancels the connection request', () => {
    const message = createMessage();
    message.setOnline(false);
    message.bootstrap(ticket, url);
    message.commitParagraph('p1', undefined, 'cancelled edit', {}, {}, 'n1');
    message.close();
    expect(() => message.listNodes()).toThrow('Bootstrap first');
    message.connect();
    message.setOnline(true);
    latestSocket().open();
    message.ping();
    expect(latestSocket().sent.map(data => JSON.parse(data).op)).toEqual([OP.PING]);
  });

  it('uses one exponential retry from one second to the thirty-second cap and resets after open', () => {
    const message = createMessage();
    message.bootstrap(ticket, url);

    for (const delay of [1000, 2000, 4000, 8000, 16000, 30000, 30000]) {
      const attempts = ControlledWebSocket.attempts;
      latestSocket().fail();
      latestSocket().fail();
      latestSocket().finishClose();
      message.connect();
      message.bootstrap(ticket, url);
      vi.advanceTimersByTime(delay - 1);
      expect(ControlledWebSocket.attempts).toBe(attempts);
      vi.advanceTimersByTime(1);
      expect(ControlledWebSocket.attempts).toBe(attempts + 1);
    }

    latestSocket().open();
    const attempts = ControlledWebSocket.attempts;
    latestSocket().finishClose();
    vi.advanceTimersByTime(999);
    expect(ControlledWebSocket.attempts).toBe(attempts);
    vi.advanceTimersByTime(1);
    expect(ControlledWebSocket.attempts).toBe(attempts + 1);
    expect(ControlledWebSocket.maxConcurrent).toBe(1);
  });

  it('reports valid open, receives messages, and only pings an open connection', () => {
    const message = createMessage();
    const status = vi.fn();
    const opened = vi.fn();
    const closed = vi.fn();
    const received = vi.fn();
    message.connectedStatus$.subscribe(status);
    message.opened().subscribe(opened);
    message.closed().subscribe(closed);
    message.received().subscribe(received);
    message.bootstrap(ticket, url);
    const socket = latestSocket();
    socket.open();
    socket.open();
    socket.receive({ op: OP.NOTE, data: { note: { id: 'note-1' } } });
    vi.advanceTimersByTime(10000);

    expect(message.connectedStatus).toBe(true);
    expect(status).toHaveBeenLastCalledWith(true);
    expect(opened).toHaveBeenCalledTimes(1);
    expect(received).toHaveBeenCalledWith({ op: OP.NOTE, data: { note: { id: 'note-1' } } });
    expect(socket.sent.map(data => JSON.parse(data).op)).toEqual([OP.PING]);

    socket.finishClose();
    expect(message.connectedStatus).toBe(false);
    expect(status).toHaveBeenLastCalledWith(false);
    expect(closed).toHaveBeenCalledOnce();
    expect(closed.mock.calls[0][0].code).toBe(1006);
    vi.advanceTimersByTime(20000);
    expect(socket.sent).toHaveLength(1);
    expect(latestSocket().sent).toEqual([]);
  });

  it('waits for physical close even after retry is due and ignores callbacks from the old socket', () => {
    const message = createMessage();
    const opened = vi.fn();
    const closed = vi.fn();
    const received = vi.fn();
    message.opened().subscribe(opened);
    message.closed().subscribe(closed);
    message.received().subscribe(received);
    message.bootstrap(ticket, url);
    const old = latestSocket();
    old.open();
    const staleOpen = old.onopen;
    const staleClose = old.onclose;
    const staleError = old.onerror;
    const staleReceive = old.onmessage;
    old.fail();
    expect(old.readyState).toBe(2);
    expect(message.connectedStatus).toBe(false);
    expect(message.getWsInstance()).toBeNull();

    vi.advanceTimersByTime(60000);
    message.connect();
    message.bootstrap(ticket, url);
    expect(ControlledWebSocket.attempts).toBe(1);
    old.finishClose();
    expect(ControlledWebSocket.attempts).toBe(2);
    const replacement = latestSocket();
    replacement.open();

    staleOpen?.(new Event('open'));
    staleError?.(new Event('error'));
    staleClose?.(new CloseEvent('close', { code: 1006 }));
    old.finishClose();
    staleReceive?.(new MessageEvent('message', { data: JSON.stringify({ op: OP.NOTE, data: {} }) }));
    vi.advanceTimersByTime(5000);
    expect(message.connectedStatus).toBe(true);
    expect(opened).toHaveBeenCalledTimes(2);
    expect(closed).not.toHaveBeenCalled();
    expect(received).not.toHaveBeenCalled();
    expect(ControlledWebSocket.attempts).toBe(2);
    expect(ControlledWebSocket.maxConcurrent).toBe(1);
  });

  it('does not reset retry backoff or status for a retired open callback', () => {
    const message = createMessage();
    const opened = vi.fn();
    message.opened().subscribe(opened);
    message.bootstrap(ticket, url);
    const first = latestSocket();
    const staleOpen = first.onopen;
    first.fail();
    first.finishClose();
    vi.advanceTimersByTime(1000);
    const second = latestSocket();
    const retiredOpen = second.onopen;
    second.fail();
    second.finishClose();

    staleOpen?.(new Event('open'));
    retiredOpen?.(new Event('open'));
    expect(message.connectedStatus).toBe(false);
    expect(opened).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1999);
    expect(ControlledWebSocket.attempts).toBe(2);
    vi.advanceTimersByTime(1);
    expect(ControlledWebSocket.attempts).toBe(3);
    latestSocket().open();
    expect(message.connectedStatus).toBe(true);
    expect(opened).toHaveBeenCalledOnce();
    expect(ControlledWebSocket.maxConcurrent).toBe(1);
  });

  it('pauses connecting and open sockets offline, then resumes each intent only once', () => {
    const message = createMessage();
    message.bootstrap(ticket, url);
    const connecting = latestSocket();
    message.setOnline(false);
    expect(connecting.close).toHaveBeenCalledOnce();
    expect(connecting.readyState).toBe(2);
    expect(message.getWsInstance()).toBeNull();
    message.setOnline(true);
    message.setOnline(true);
    vi.advanceTimersByTime(60000);
    expect(ControlledWebSocket.attempts).toBe(1);

    connecting.finishClose();
    expect(ControlledWebSocket.attempts).toBe(2);
    const open = latestSocket();
    open.open();
    message.setOnline(false);
    expect(message.connectedStatus).toBe(false);
    open.finishClose();
    vi.advanceTimersByTime(60000);
    expect(ControlledWebSocket.attempts).toBe(2);
    expect(open.sent).toEqual([]);

    message.setOnline(true);
    message.setOnline(true);
    expect(ControlledWebSocket.attempts).toBe(3);
    expect(ControlledWebSocket.maxConcurrent).toBe(1);
  });

  it('cancels a pending retry offline and immediately resumes once when online', () => {
    const message = createMessage();
    message.bootstrap(ticket, url);
    latestSocket().finishClose();
    vi.advanceTimersByTime(500);
    message.setOnline(false);
    vi.advanceTimersByTime(60000);
    expect(ControlledWebSocket.attempts).toBe(1);
    message.setOnline(true);
    message.setOnline(true);
    expect(ControlledWebSocket.attempts).toBe(2);
    vi.advanceTimersByTime(5000);
    expect(ControlledWebSocket.attempts).toBe(2);
  });

  it('physically closes, cancels intent, and permits only a new explicit connect to reopen', () => {
    const message = createMessage();
    message.bootstrap(ticket, url);
    const socket = latestSocket();
    socket.open();
    message.close();
    message.close();
    expect(socket.close).toHaveBeenCalledOnce();
    expect(message.connectedStatus).toBe(false);
    expect(message.getWsInstance()).toBeNull();
    message.setOnline(false);
    message.setOnline(true);
    socket.finishClose();
    vi.advanceTimersByTime(60000);
    expect(ControlledWebSocket.attempts).toBe(1);

    message.connect();
    expect(ControlledWebSocket.attempts).toBe(2);
    latestSocket().finishClose();
    message.close();
    vi.advanceTimersByTime(60000);
    expect(ControlledWebSocket.attempts).toBe(2);
  });

  it('waits for an explicitly closed socket before honoring a new bootstrap', () => {
    const message = createMessage();
    message.bootstrap(ticket, url);
    const socket = latestSocket();
    socket.open();
    message.close();
    message.bootstrap(ticket, url);
    message.connect();
    expect(ControlledWebSocket.attempts).toBe(1);
    socket.finishClose(1000);
    expect(ControlledWebSocket.attempts).toBe(2);
    expect(ControlledWebSocket.maxConcurrent).toBe(1);
  });

  it('cancels a connecting socket despite late open and error callbacks', () => {
    const message = createMessage();
    const opened = vi.fn();
    const received = vi.fn();
    message.opened().subscribe(opened);
    message.received().subscribe(received);
    message.bootstrap(ticket, url);
    const socket = latestSocket();
    const staleOpen = socket.onopen;
    const staleError = socket.onerror;
    const staleReceive = socket.onmessage;

    message.close();
    expect(socket.close).toHaveBeenCalledOnce();
    staleOpen?.(new Event('open'));
    staleError?.(new Event('error'));
    staleReceive?.(new MessageEvent('message', { data: JSON.stringify({ op: OP.NOTE, data: {} }) }));
    message.setOnline(false);
    message.setOnline(true);
    socket.finishClose();
    vi.advanceTimersByTime(120000);

    expect(message.connectedStatus).toBe(false);
    expect(message.getWsInstance()).toBeNull();
    expect(opened).not.toHaveBeenCalled();
    expect(received).not.toHaveBeenCalled();
    expect(socket.sent).toEqual([]);
    expect(ControlledWebSocket.attempts).toBe(1);
    expect(vi.getTimerCount()).toBe(0);
  });

  it('does not deliver a message when its interceptor closes the connection', () => {
    class ClosingMessage extends Message {
      interceptReceived(data: Parameters<Message['interceptReceived']>[0]) {
        this.close();
        return data;
      }
    }
    const message = new ClosingMessage();
    messages.push(message);
    const received = vi.fn();
    message.received().subscribe(received);
    message.bootstrap(ticket, url);
    const socket = latestSocket();
    socket.open();
    socket.receive({ op: OP.NOTE, data: {} });
    socket.finishClose();
    vi.advanceTimersByTime(120000);

    expect(received).not.toHaveBeenCalled();
    expect(message.connectedStatus).toBe(false);
    expect(ControlledWebSocket.attempts).toBe(1);
    expect(vi.getTimerCount()).toBe(0);
  });

  it('does not automatically retry a normal server close', () => {
    const message = createMessage();
    message.bootstrap(ticket, url);
    latestSocket().open();
    latestSocket().finishClose(1000);
    vi.advanceTimersByTime(120000);
    expect(message.connectedStatus).toBe(false);
    expect(ControlledWebSocket.attempts).toBe(1);
    message.setOnline(false);
    message.setOnline(true);
    expect(ControlledWebSocket.attempts).toBe(1);
  });

  it('handles synchronous constructor failure without an unhandled error or duplicate retry', () => {
    ControlledWebSocket.failuresRemaining = 2;
    const message = createMessage();
    expect(() => message.bootstrap(ticket, url)).not.toThrow();
    expect(ControlledWebSocket.attempts).toBe(1);
    message.connect();
    vi.advanceTimersByTime(999);
    expect(ControlledWebSocket.attempts).toBe(1);
    vi.advanceTimersByTime(1);
    expect(ControlledWebSocket.attempts).toBe(2);
    vi.advanceTimersByTime(1999);
    expect(ControlledWebSocket.attempts).toBe(2);
    vi.advanceTimersByTime(1);
    expect(ControlledWebSocket.attempts).toBe(3);
    latestSocket().open();
    expect(message.connectedStatus).toBe(true);
    expect(ControlledWebSocket.maxConcurrent).toBe(1);
  });

  it('allows destruction by an open-status subscriber without resurrecting ping or open events', () => {
    const message = createMessage();
    const opened = vi.fn();
    message.opened().subscribe(opened);
    message.connectedStatus$.subscribe(connected => {
      if (connected) {
        message.destroy();
      }
    });
    message.bootstrap(ticket, url);
    const socket = latestSocket();
    socket.open();
    socket.finishClose();
    vi.advanceTimersByTime(120000);
    expect(message.connectedStatus).toBe(false);
    expect(opened).not.toHaveBeenCalled();
    expect(socket.sent).toEqual([]);
    expect(ControlledWebSocket.attempts).toBe(1);
    expect(vi.getTimerCount()).toBe(0);
  });

  it('dispatches heartbeat through the current Message override', () => {
    const message = createMessage();
    message.bootstrap(ticket, url);
    latestSocket().open();
    message.ping = () => message.listNodes();
    vi.advanceTimersByTime(10000);
    expect(latestSocket().sent.map(data => JSON.parse(data).op)).toEqual([OP.LIST_NOTES]);
    message.close();
    vi.advanceTimersByTime(10000);
    expect(latestSocket().sent.map(data => JSON.parse(data).op)).toEqual([OP.LIST_NOTES]);
  });

  it('completes facade streams once in order even when disconnect triggers another destroy', () => {
    const message = createMessage();
    const events: string[] = [];
    message.connectedStatus$.subscribe({
      next: connected => {
        if (!connected) {
          events.push('disconnected');
          message.destroy();
        }
      },
      complete: () => events.push('status complete')
    });
    message.opened().subscribe({ complete: () => events.push('open complete') });
    message.closed().subscribe({ complete: () => events.push('close complete') });
    message.sent().subscribe({ complete: () => events.push('sent complete') });
    message.received().subscribe({ complete: () => events.push('received complete') });
    message.bootstrap(ticket, url);
    latestSocket().open();
    message.destroy();
    latestSocket().finishClose(1000);
    message.destroy();
    expect(events).toEqual([
      'disconnected',
      'status complete',
      'open complete',
      'close complete',
      'sent complete',
      'received complete'
    ]);
  });

  it.each(['connecting', 'open', 'retry', 'offline', 'closing'] as const)('makes destroy terminal while %s', state => {
    const message = createMessage();
    const completed = vi.fn();
    const received = vi.fn();
    message.connectedStatus$.subscribe({ complete: completed });
    message.received().subscribe(received);
    message.bootstrap(ticket, url);
    const socket = latestSocket();
    if (state === 'open' || state === 'offline' || state === 'closing') {
      socket.open();
    } else if (state === 'retry') {
      socket.finishClose();
    }
    if (state === 'offline') {
      message.setOnline(false);
    } else if (state === 'closing') {
      message.close();
    }

    message.destroy();
    message.destroy();
    expect(message.connectedStatus).toBe(false);
    expect(message.getWsInstance()).toBeNull();
    expect(completed).toHaveBeenCalledOnce();
    expect(() => message.connect()).not.toThrow();
    message.bootstrap(ticket, url);
    message.setOnline(false);
    message.setOnline(true);
    socket.finishClose();
    socket.onopen?.(new Event('open'));
    socket.fail();
    socket.receive({ op: OP.NOTE, data: {} });
    vi.advanceTimersByTime(120000);
    expect(received).not.toHaveBeenCalled();
    expect(socket.sent).toEqual([]);
    expect(ControlledWebSocket.attempts).toBe(1);
    expect(vi.getTimerCount()).toBe(0);
  });
});
