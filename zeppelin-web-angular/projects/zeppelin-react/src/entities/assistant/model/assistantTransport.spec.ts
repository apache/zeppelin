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

import type { AssistantSendMessage, AssistantSocketEvent } from '@zeppelin/sdk';

import {
  AssistantHttpError,
  createAssistantTransport,
  mapSocketEvent,
  createRunTracking,
  scopeTransport,
  socketRun,
  toVisibleMessages
} from './assistantTransport';
import {
  AssistantConnectionError,
  AssistantStreamError,
  type AssistantRunEvent,
  type AssistantRunState
} from './assistantContract';
import { joinPages } from './messageHistory';

/** A host socket double: records sends and lets a test emit server events. */
const fakeSocket = () => {
  const listeners = new Set<(event: AssistantSocketEvent) => void>();
  const closeListeners = new Set<() => void>();
  const sent: AssistantSendMessage[] = [];
  return {
    sent,
    listenerCount: () => listeners.size,
    emit: (event: AssistantSocketEvent) => listeners.forEach(listener => listener(event)),
    send: vi.fn((message: AssistantSendMessage) => {
      sent.push(message);
    }),
    subscribe: vi.fn((listener: (event: AssistantSocketEvent) => void) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    }),
    close: () => closeListeners.forEach(listener => listener()),
    closeListenerCount: () => closeListeners.size,
    subscribeClose: vi.fn((listener: () => void) => {
      closeListeners.add(listener);
      return () => closeListeners.delete(listener);
    })
  };
};

const forNote = (noteId: string, onAuthError?: (status: number, location: string | null) => void) =>
  createAssistantTransport('https://example.test/zeppelin/api', noteId, fakeSocket(), onAuthError);

const iterate = <T>(iterable: AsyncIterable<T>): AsyncIterator<T> => iterable[Symbol.asyncIterator]();

const collect = async (events: AsyncIterable<AssistantRunEvent>): Promise<AssistantRunEvent[]> => {
  const collected: AssistantRunEvent[] = [];
  for await (const event of events) {
    collected.push(event);
  }
  return collected;
};

const message = (content = 'hi'): AssistantSendMessage => ({ noteId: 'n', conversationId: 'c1', content });

describe('assistant transport', () => {
  // One note's sends share their run tracking, as the transport does.
  let tracking = createRunTracking();
  beforeEach(() => {
    tracking = createRunTracking();
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('lists the most recently active conversation first and undated ones last', async () => {
    const body = [
      { id: 'old', createdAt: '2026-10-01T00:00:00Z', updatedAt: '2026-10-01T00:00:00Z' },
      { id: 'undated' },
      { id: 'new', createdAt: '2026-09-30T00:00:00Z', updatedAt: '2026-10-03T00:00:00Z' },
      { id: 'created-only', createdAt: '2026-10-02T00:00:00Z' }
    ];
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ status: 'OK', body }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' }
        })
      )
    );
    const conversations = await forNote('note-1').listConversations();
    expect(conversations.map(conversation => conversation.id)).toEqual(['new', 'created-only', 'old', 'undated']);
    // The list shows the latest activity, or the creation time before any.
    expect(conversations.map(conversation => conversation.updatedAt)).toEqual([
      '2026-10-03T00:00:00Z',
      '2026-10-02T00:00:00Z',
      '2026-10-01T00:00:00Z',
      undefined
    ]);
  });

  it('uses the per-note conversation endpoints and unwraps Zeppelin JSON responses', async () => {
    const json = (body: unknown) =>
      new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        json({
          status: 'OK',
          body: [{ id: 'conv-1', title: 'First', noteId: 'note/one', ownerId: 'alice', canSendMessage: false }]
        })
      )
      .mockResolvedValueOnce(json({ status: 'OK', body: { id: 'conv-2', title: 'Load the data', ownerId: 'bob' } }))
      .mockResolvedValueOnce(
        json({
          status: 'OK',
          body: {
            messages: [
              { id: 'message-2', role: 'assistant', content: 'hi there' },
              { id: 'message-1', role: 'user', content: 'hello' }
            ],
            cursor: null
          }
        })
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);
    const transport = forNote('note/one');

    await expect(transport.listConversations()).resolves.toEqual([
      { id: 'conv-1', title: 'First', ownerId: 'alice', canSendMessage: false }
    ]);
    await expect(transport.createConversation({ title: '  Load   the data  ' })).resolves.toEqual({
      id: 'conv-2',
      title: 'Load the data',
      ownerId: 'bob'
    });
    // The server pages latest first; the panel wants oldest first.
    await expect(transport.getMessages('conv/1')).resolves.toEqual({
      messages: [
        { id: 'message-1', role: 'user', content: 'hello' },
        { id: 'message-2', role: 'assistant', content: 'hi there' }
      ],
      earlierCursor: null
    });
    await expect(transport.deleteConversation('conv/1')).resolves.toBeUndefined();

    const base = 'https://example.test/zeppelin/api/notes/note%2Fone/conversations';
    expect(fetchMock.mock.calls.map(([url, init]) => [url, init.method])).toEqual([
      [base, 'GET'],
      [base, 'POST'],
      [`${base}/conv%2F1/messages?limit=50`, 'GET'],
      [`${base}/conv%2F1`, 'DELETE']
    ]);
    expect(fetchMock.mock.calls[1][1]).toMatchObject({
      credentials: 'include',
      headers: expect.objectContaining({ 'X-Requested-With': 'XMLHttpRequest', 'Content-Type': 'application/json' }),
      // The first question names the conversation, with whitespace collapsed.
      body: JSON.stringify({ title: 'Load the data' })
    });
  });

  it('words a 404 on the conversation list as a server without the assistant', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(null, { status: 404 }))
    );
    const transport = forNote('n');

    await expect(transport.listConversations()).rejects.toThrow('The assistant is not available on this server');
    await expect(transport.createConversation({})).rejects.toThrow('The assistant is not available on this server');
    await expect(transport.getMessages('c')).rejects.toThrow('This conversation or note no longer exists.');
  });

  it('hands 401 and 405 to the host, but not other failures', async () => {
    const onAuthError = vi.fn();
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(new Response(null, { status: 401, headers: { Location: '/login' } }))
        .mockResolvedValueOnce(new Response(null, { status: 405 }))
        .mockResolvedValueOnce(new Response(null, { status: 500 }))
    );
    const transport = forNote('n', onAuthError);

    await expect(transport.listConversations()).rejects.toMatchObject({ status: 401, location: '/login' });
    await expect(transport.createConversation({})).rejects.toMatchObject({ status: 405 });
    await expect(transport.getMessages('c')).rejects.toBeInstanceOf(AssistantHttpError);

    expect(onAuthError.mock.calls).toEqual([
      [401, '/login'],
      [405, null]
    ]);
  });

  it('sends over the socket and yields only this conversation until a terminal event', async () => {
    const socket = fakeSocket();
    const transport = createAssistantTransport('https://example.test/api', 'n', socket);
    const run = iterate(transport.openRun('c1', { prompt: 'improve' }, new AbortController().signal));
    const first = run.next();
    await vi.waitFor(() => expect(socket.sent).toHaveLength(1));
    expect(socket.sent[0]).toEqual({ noteId: 'n', conversationId: 'c1', content: 'improve' });

    socket.emit({ conversationId: 'other', type: 'message.delta', payload: { messageId: 'x', delta: 'no' } });
    socket.emit({ conversationId: 'c1', type: 'run.started', payload: { runId: 'run_1' } });
    socket.emit({
      conversationId: 'c1',
      type: 'tool_call.started',
      payload: { toolCallId: 't1', name: 'list_paragraphs' }
    });
    socket.emit({
      conversationId: 'c1',
      type: 'tool_call.done',
      payload: { toolCallId: 't1', result: { value: '[]' } }
    });
    // A newer server may send types this client does not know.
    socket.emit({ conversationId: 'c1', type: 'usage.extra', payload: {} } as unknown as AssistantSocketEvent);
    socket.emit({ conversationId: 'c1', type: 'message.delta', payload: { messageId: 'm1', delta: '한글' } });
    socket.emit({ conversationId: 'c1', type: 'run.completed', payload: { runId: 'run_1' } });
    socket.emit({ conversationId: 'c1', type: 'message.delta', payload: { messageId: 'm2', delta: 'late' } });

    const events = [(await first).value];
    for (let next = await run.next(); !next.done; next = await run.next()) events.push(next.value);
    expect(events).toEqual([
      { type: 'run.started', runId: 'run_1' },
      { type: 'tool_call.started', toolCallId: 't1', name: 'list_paragraphs' },
      { type: 'tool_call.done', toolCallId: 't1', name: undefined },
      { type: 'message.delta', messageId: 'm1', delta: '한글' },
      { type: 'run.completed', runId: 'run_1' }
    ]);
    expect(socket.listenerCount()).toBe(0);
  });

  it('ends on a run failure that arrives before run.started', async () => {
    const socket = fakeSocket();
    const pending = collect(socketRun(socket, message(), new AbortController().signal, tracking));
    socket.emit({
      conversationId: 'c1',
      type: 'run.failed',
      payload: { runId: 'run_x', error: { status: 403 } }
    });
    await expect(pending).resolves.toEqual([
      {
        type: 'run.failed',
        runId: 'run_x',
        message: 'You do not have permission to send messages to this conversation.'
      }
    ]);
  });

  it('blocks resending an aborted request until its delayed start is tracked', async () => {
    const socket = fakeSocket();
    const leave = new AbortController();
    const first = iterate(socketRun(socket, message('first'), leave.signal, tracking));
    const response = first.next();
    leave.abort();
    await expect(response).rejects.toMatchObject({ name: 'AbortError' });
    await expect(
      collect(socketRun(socket, message('second'), new AbortController().signal, tracking))
    ).resolves.toEqual([
      { type: 'run.failed', message: 'The previous request is still being confirmed. Try again shortly.' }
    ]);
    expect(socket.sent).toHaveLength(1);
    socket.emit({ conversationId: 'c1', type: 'run.started', payload: { runId: 'old' } });
    expect(socket.listenerCount()).toBe(1);
    expect(socket.closeListenerCount()).toBe(1);
    const second = collect(socketRun(socket, message('second'), new AbortController().signal, tracking));
    socket.emit({
      conversationId: 'c1',
      type: 'message.delta',
      payload: { messageId: 'old-answer', delta: 'old answer' }
    });
    socket.emit({ conversationId: 'c1', type: 'run.completed', payload: { runId: 'old' } });
    socket.emit({ conversationId: 'c1', type: 'run.failed', payload: { runId: 'new', error: { status: 409 } } });
    await expect(second).resolves.toEqual([
      { type: 'run.failed', runId: 'new', message: 'This conversation is still answering. Try again when it finishes.' }
    ]);
  });

  it('tracks a queued start even when the consumer aborts before reading it', async () => {
    const socket = fakeSocket();
    const controller = new AbortController();
    const first = iterate(socketRun(socket, message(), controller.signal, tracking));
    const response = first.next();
    socket.emit({ conversationId: 'c1', type: 'run.started', payload: { runId: 'queued' } });
    controller.abort();
    await expect(response).rejects.toMatchObject({ name: 'AbortError' });
    expect(tracking.abandoned.has('queued')).toBe(true);
    expect(socket.listenerCount()).toBe(1);
    socket.emit({ conversationId: 'c1', type: 'run.completed', payload: { runId: 'queued' } });
    expect(socket.listenerCount()).toBe(0);
  });

  it('shares pending requests across transports on the same host connection', async () => {
    const connectionKey = {};
    const socket = { ...fakeSocket(), connectionKey };
    const old = createAssistantTransport('/api', 'note', socket);
    const leave = new AbortController();
    const response = iterate(old.openRun('c1', { prompt: 'first' }, leave.signal)).next();
    leave.abort();
    await expect(response).rejects.toMatchObject({ name: 'AbortError' });
    const next = createAssistantTransport('/api', 'note', { ...socket });
    await expect(collect(next.openRun('c1', { prompt: 'second' }, new AbortController().signal))).resolves.toEqual([
      { type: 'run.failed', message: 'The previous request is still being confirmed. Try again shortly.' }
    ]);
    expect(socket.sent).toHaveLength(1);
    socket.emit({ conversationId: 'c1', type: 'run.failed', payload: { runId: 'old', error: { status: 403 } } });
    expect(socket.listenerCount()).toBe(0);
    const sent = collect(next.openRun('c1', { prompt: 'second' }, new AbortController().signal));
    socket.emit({ conversationId: 'c1', type: 'run.failed', payload: { runId: 'new', error: { status: 409 } } });
    await expect(sent).resolves.toHaveLength(1);
    expect(socket.sent).toHaveLength(2);
  });

  it('releases unconfirmed sends when the connection closes', async () => {
    const socket = fakeSocket();
    const controller = new AbortController();
    const response = iterate(socketRun(socket, message(), controller.signal, tracking)).next();
    controller.abort();
    await expect(response).rejects.toMatchObject({ name: 'AbortError' });
    socket.close();
    expect(socket.listenerCount()).toBe(0);
    expect(socket.closeListenerCount()).toBe(0);
    const reconnect = collect(socketRun(socket, message(), new AbortController().signal, tracking));
    socket.emit({ conversationId: 'c1', type: 'run.failed', payload: { runId: 'new', error: { status: 403 } } });
    await expect(reconnect).resolves.toHaveLength(1);
  });

  it('ignores the rest of an abandoned run when the next message is sent', async () => {
    const socket = fakeSocket();
    const leave = new AbortController();
    const abandoned = iterate(socketRun(socket, message('first'), leave.signal, tracking));
    const firstEvent = abandoned.next();
    socket.emit({ conversationId: 'c1', type: 'run.started', payload: { runId: 'run_old' } });
    expect((await firstEvent).value).toEqual({ type: 'run.started', runId: 'run_old' });
    leave.abort();
    await expect(abandoned.next()).rejects.toThrow();

    // Sent while the abandoned run still streams: the server rejects it, and the old run's tail is not this run's.
    const rejected = collect(socketRun(socket, message('second'), new AbortController().signal, tracking));
    socket.emit({ conversationId: 'c1', type: 'message.delta', payload: { messageId: 'old', delta: 'tail' } });
    socket.emit({ conversationId: 'c1', type: 'run.completed', payload: { runId: 'run_old' } });
    socket.emit({
      conversationId: 'c1',
      type: 'run.failed',
      payload: { runId: 'run_new', error: { status: 409 } }
    });
    await expect(rejected).resolves.toEqual([
      {
        type: 'run.failed',
        runId: 'run_new',
        message: 'This conversation is still answering. Try again when it finishes.'
      }
    ]);

    // Once the old run ended, the next message runs normally.
    const next = collect(socketRun(socket, message('third'), new AbortController().signal, tracking));
    socket.emit({ conversationId: 'c1', type: 'run.started', payload: { runId: 'run_3' } });
    socket.emit({ conversationId: 'c1', type: 'message.delta', payload: { messageId: 'm3', delta: 'ok' } });
    socket.emit({ conversationId: 'c1', type: 'run.completed', payload: { runId: 'run_3' } });
    await expect(next).resolves.toEqual([
      { type: 'run.started', runId: 'run_3' },
      { type: 'message.delta', messageId: 'm3', delta: 'ok' },
      { type: 'run.completed', runId: 'run_3' }
    ]);
  });

  it('words run.failed by its HTTP status and falls back for the rest', () => {
    const failed = (error: unknown) =>
      mapSocketEvent({ conversationId: 'c', type: 'run.failed', payload: { runId: 'r', error } });
    expect(failed({ status: 409 })).toEqual({
      type: 'run.failed',
      runId: 'r',
      message: 'This conversation is still answering. Try again when it finishes.'
    });
    expect(failed({ status: 400 })).toMatchObject({
      message: 'The assistant could not accept this message. Edit it and try again.'
    });
    expect(failed({ status: 503 })).toMatchObject({ message: expect.stringContaining('not configured') });
    expect(failed({ status: 500 })).toMatchObject({
      message: 'The assistant hit an error while answering. Try again.'
    });
    expect(failed(undefined)).toMatchObject({ message: 'The assistant hit an error while answering. Try again.' });
    expect(failed(null)).toMatchObject({ message: 'The assistant hit an error while answering. Try again.' });
    expect(failed(403)).toMatchObject({ message: 'The assistant hit an error while answering. Try again.' });
    expect(failed({ status: '403' })).toMatchObject({
      message: 'The assistant hit an error while answering. Try again.'
    });
  });

  it('loads an earlier page with the cursor and joins a turn split across pages', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          status: 'OK',
          body: { messages: [{ id: 'm3', role: 'assistant', content: 'part one.' }], cursor: 'm3' }
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    );
    vi.stubGlobal('fetch', fetchMock);
    const page = await forNote('n').getMessages('c', 'm9');
    expect(fetchMock.mock.calls[0][0]).toBe(
      'https://example.test/zeppelin/api/notes/n/conversations/c/messages?limit=50&cursor=m9'
    );
    expect(page).toEqual({ messages: [{ id: 'm3', role: 'assistant', content: 'part one.' }], earlierCursor: 'm3' });
    expect(
      joinPages(page.messages, [
        { id: 'm5', role: 'assistant', content: 'part two.' },
        { id: 'm6', role: 'user', content: 'next' }
      ])
    ).toEqual([
      { id: 'm5', role: 'assistant', content: 'part one.\n\npart two.' },
      { id: 'm6', role: 'user', content: 'next' }
    ]);
  });

  it('explains a missing assistant configuration instead of the bare status', () => {
    expect(new AssistantHttpError(503, null).message).toBe(
      'The assistant is not configured on this server. Ask an administrator to enable it.'
    );
    expect(new AssistantHttpError(500, null).message).toBe('Assistant request failed with HTTP 500');
  });

  it('keeps only the ACK listener when aborted before the run starts', async () => {
    const socket = fakeSocket();
    const controller = new AbortController();
    const run = iterate(socketRun(socket, message(), controller.signal, tracking));
    const pending = run.next();
    await vi.waitFor(() => expect(socket.listenerCount()).toBe(2));

    controller.abort();

    await expect(pending).rejects.toMatchObject({ name: 'AbortError' });
    expect(socket.listenerCount()).toBe(1);
    socket.emit({ conversationId: 'c1', type: 'run.started', payload: { runId: 'late' } });
    expect(socket.listenerCount()).toBe(1);
    socket.emit({ conversationId: 'c1', type: 'run.completed', payload: { runId: 'late' } });
    expect(socket.listenerCount()).toBe(0);
  });

  it('ends a run with a connection error when the notebook socket closes', async () => {
    const socket = fakeSocket();
    const pending = collect(socketRun(socket, message(), new AbortController().signal, tracking));
    socket.emit({ conversationId: 'c1', type: 'run.started', payload: { runId: 'r1' } });
    socket.close();
    await expect(pending).rejects.toBeInstanceOf(AssistantConnectionError);
    expect(socket.closeListenerCount()).toBe(0);
  });

  it('reports a retryable error when the run goes quiet past the idle timeout', async () => {
    vi.useFakeTimers();
    const socket = fakeSocket();
    const pending = collect(socketRun(socket, message(), new AbortController().signal, tracking, 1000));
    const outcome = pending.then(
      () => null,
      (error: unknown) => error
    );
    await vi.advanceTimersByTimeAsync(1000);
    expect(await outcome).toBeInstanceOf(AssistantStreamError);
    expect(socket.listenerCount()).toBe(1);
    socket.close();
    expect(socket.listenerCount()).toBe(0);
  });

  it('fails the run on an event with a malformed payload', async () => {
    const socket = fakeSocket();
    const pending = collect(socketRun(socket, message(), new AbortController().signal, tracking));
    socket.emit({ conversationId: 'c1', type: 'message.delta', payload: { messageId: 'm1' } });
    // Told in words for the user; the field stays on the error for debugging.
    await expect(pending).rejects.toThrow('The assistant sent a reply this panel could not read. Try again.');
    await expect(pending).rejects.toMatchObject({ detail: 'Assistant event field "delta" is required' });
  });

  it('ignores event types the server does not define', () => {
    expect(
      mapSocketEvent({
        conversationId: 'c',
        type: 'proposal.created',
        payload: { kind: 'insert', text: 'x' }
      } as unknown as AssistantSocketEvent)
    ).toBeUndefined();
    expect(
      mapSocketEvent({
        conversationId: 'c',
        type: 'ui.reveal',
        payload: { paragraphId: 'p1' }
      } as unknown as AssistantSocketEvent)
    ).toBeUndefined();
  });

  it('drops late responses and aborts runs once a scoped transport is deactivated', async () => {
    let resolveList: (conversations: []) => void = () => undefined;
    let runSignal: AbortSignal | undefined;
    const inner = {
      listConversations: vi.fn(() => new Promise<[]>(resolve => (resolveList = resolve))),
      createConversation: vi.fn(),
      deleteConversation: vi.fn(),
      getMessages: vi.fn(),
      openRun: async function* (_id: string, _body: unknown, signal: AbortSignal): AsyncIterable<AssistantRunEvent> {
        runSignal = signal;
        yield { type: 'run.started' };
        await new Promise(() => undefined);
      }
    };
    const scoped = scopeTransport(inner);
    const list = scoped.transport.listConversations();
    const run = iterate(scoped.transport.openRun('t', { prompt: 'hi' }, new AbortController().signal));
    await run.next();
    expect(inner.listConversations).toHaveBeenCalledWith();

    scoped.setActive(false);
    resolveList([]);

    await expect(list).rejects.toMatchObject({ name: 'AbortError' });
    expect(runSignal?.aborted).toBe(true);
    await expect(scoped.transport.getMessages('t')).rejects.toMatchObject({ name: 'AbortError' });
  });

  it('retires pending creations and active runs when the host session aborts', async () => {
    const session = new AbortController();
    let resolveCreate: (value: { id: string }) => void = () => undefined;
    let runSignal: AbortSignal | undefined;
    const inner = {
      listConversations: vi.fn().mockResolvedValue([]),
      createConversation: vi.fn(
        () =>
          new Promise<{ id: string }>(resolve => {
            resolveCreate = resolve;
          })
      ),
      deleteConversation: vi.fn(),
      getMessages: vi.fn(),
      openRun: async function* (_id: string, _body: unknown, signal: AbortSignal): AsyncIterable<AssistantRunEvent> {
        runSignal = signal;
        yield { type: 'run.started' };
        await new Promise<void>(resolve => signal.addEventListener('abort', () => resolve(), { once: true }));
        yield { type: 'run.completed' };
      }
    };
    const scoped = scopeTransport(inner, session.signal);
    scoped.setActive(false);
    scoped.setActive(true);
    const creation = scoped.transport.createConversation({});
    const run = iterate(scoped.transport.openRun('c1', { prompt: 'hi' }, new AbortController().signal));
    await run.next();
    const terminal = run.next();
    session.abort();
    resolveCreate({ id: 'old-account-conversation' });
    await expect(creation).rejects.toMatchObject({ name: 'AbortError' });
    await expect(terminal).rejects.toMatchObject({ name: 'AbortError' });
    expect(runSignal?.aborted).toBe(true);
    expect(scoped.active).toBe(false);
    scoped.setActive(true);
    expect(scoped.active).toBe(false);
    await expect(scoped.transport.listConversations()).rejects.toMatchObject({ name: 'AbortError' });
    expect(inner.listConversations).not.toHaveBeenCalled();
  });

  it('shows one bubble per turn and hides tool and system messages', () => {
    expect(
      toVisibleMessages([
        { id: 'u1', role: 'user', content: 'improve' },
        { id: 'a1', role: 'assistant', content: '' },
        { id: 't1', role: 'tool', content: '{}' },
        { id: 'a2', role: 'assistant', content: 'Reading.' },
        { id: 't2', role: 'tool', content: '{}' },
        { id: 'a3', role: 'assistant', content: 'Done.' },
        { id: 's1', role: 'system', content: 'hidden' }
      ])
    ).toEqual([
      { id: 'u1', role: 'user', content: 'improve' },
      // The merged bubble keeps the id of its last message.
      { id: 'a3', role: 'assistant', content: 'Reading.\n\nDone.' }
    ]);
  });

  it('keeps the tool calls of a turn on its answer, including across pages', () => {
    const visible = toVisibleMessages([
      { id: 'u1', role: 'user', content: 'what is here?' },
      { id: 'a1', role: 'assistant', content: '', toolCalls: [{ id: 'c1', name: 'list_paragraphs' }] },
      { id: 't1', role: 'tool', content: '[]' },
      { id: 'a2', role: 'assistant', content: 'Two paragraphs.' },
      { id: 'u2', role: 'user', content: 'thanks' },
      { id: 'a3', role: 'assistant', content: 'You are welcome.' }
    ]);
    expect(visible[1]).toEqual({
      id: 'a2',
      role: 'assistant',
      content: 'Two paragraphs.',
      toolCalls: [{ id: 'c1', name: 'list_paragraphs' }]
    });
    expect(visible[3]).not.toHaveProperty('toolCalls');
  });

  it('keeps tool-only messages until an answer arrives, including across raw history pages', () => {
    const question = { id: 'u1', role: 'user' as const, content: 'Read this notebook' };
    const call = {
      id: 'a1',
      role: 'assistant' as const,
      content: null,
      toolCalls: [{ id: 'c1', name: 'list_paragraphs' }]
    };
    const result = { id: 't1', role: 'tool' as const, content: '[]' };
    const answer = { id: 'a2', role: 'assistant' as const, content: 'Two paragraphs.' };
    const earlier = toVisibleMessages([question, call]);
    expect(earlier[1]).toEqual({ id: 'a1', role: 'assistant', content: '', toolCalls: call.toolCalls });
    expect(joinPages(earlier, toVisibleMessages([result, answer]))).toEqual(
      toVisibleMessages([question, call, result, answer])
    );
    // An intervening page containing only tool results must not lose the pending calls either.
    expect(joinPages(joinPages(earlier, toVisibleMessages([result])), toVisibleMessages([answer]))).toEqual([
      question,
      { ...answer, toolCalls: call.toolCalls }
    ]);
    const nextTurn = [
      { id: 'u2', role: 'user' as const, content: 'Another question' },
      { id: 'a3', role: 'assistant' as const, content: 'Another answer' }
    ];
    expect(joinPages(earlier, toVisibleMessages(nextTurn))).toEqual([...earlier, ...nextTurn]);
    expect(toVisibleMessages([question, call, ...nextTurn])).toEqual([...earlier, ...nextTurn]);
  });

  it('keeps a trailing tool round after answer text and deduplicates call ids at page boundaries', () => {
    const call = { id: 'c1', name: 'list_paragraphs' };
    const earlier = toVisibleMessages([
      { id: 'a1', role: 'assistant', content: 'Looking.' },
      { id: 'a2', role: 'assistant', content: '', toolCalls: [call] }
    ]);
    expect(earlier).toEqual([{ id: 'a2', role: 'assistant', content: 'Looking.', toolCalls: [call] }]);
    const later = toVisibleMessages([
      { id: 'a2', role: 'assistant', content: '', toolCalls: [call] },
      { id: 'a3', role: 'assistant', content: 'Done.' }
    ]);
    expect(joinPages(earlier, later)).toEqual([
      { id: 'a3', role: 'assistant', content: 'Looking.\n\nDone.', toolCalls: [call] }
    ]);
  });
});

describe('retired run subscriptions', () => {
  it('does not subscribe or throw for an inactive or aborted session', () => {
    const inner = createAssistantTransport('/api', 'note', fakeSocket());
    const subscribe = vi.spyOn(inner, 'subscribeRunState');
    const signal = new AbortController();
    const scoped = scopeTransport(inner, signal.signal);
    signal.abort();
    expect(() => scoped.transport.subscribeRunState!('conversation', vi.fn())()).not.toThrow();
    expect(subscribe).not.toHaveBeenCalled();
    const retired = scopeTransport(inner);
    retired.setActive(false);
    expect(() => retired.transport.subscribeRunState!('conversation', vi.fn())()).not.toThrow();
    expect(subscribe).not.toHaveBeenCalled();
  });
});

describe('retained run state', () => {
  it.each([true, false])(
    'keeps old completion separate from a new rejection (completion first: %s)',
    async completionFirst => {
      const socket = fakeSocket();
      const transport = createAssistantTransport('/api', 'note', socket);
      const controller = new AbortController();
      const first = iterate(transport.openRun('conversation', { prompt: 'old' }, controller.signal));
      const started = first.next();
      socket.emit({ conversationId: 'conversation', type: 'run.started', payload: { runId: 'old' } });
      await started;
      controller.abort();
      await expect(first.next()).rejects.toThrow();
      const states: AssistantRunState[] = [];
      const stop = transport.subscribeRunState!('conversation', running => states.push(running));
      const next = collect(transport.openRun('conversation', { prompt: 'new' }, new AbortController().signal));
      const completion = () =>
        socket.emit({ conversationId: 'conversation', type: 'run.completed', payload: { runId: 'old' } });
      const rejection = () =>
        socket.emit({
          conversationId: 'conversation',
          type: 'run.failed',
          payload: { runId: 'new', error: { status: 429 } }
        });
      if (completionFirst) {
        completion();
        rejection();
      } else {
        rejection();
        completion();
      }
      expect(await next).toEqual([
        { type: 'run.failed', runId: 'new', message: 'The assistant is busy. Try again shortly.' }
      ]);
      expect(states[0]).toBe('running');
      expect(states[states.length - 1]).toBe('idle');
      expect(socket.listenerCount()).toBe(0);
      stop();
    }
  );

  it('uses the server conversation response without a running field', async () => {
    const fetch = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify({ body: [{ id: 'conversation', title: 'Question' }] })));
    vi.stubGlobal('fetch', fetch);
    const transport = createAssistantTransport('/api', 'note', fakeSocket());
    expect(await transport.listConversations()).toEqual([{ id: 'conversation', title: 'Question' }]);
    const states: AssistantRunState[] = [];
    const stop = transport.subscribeRunState!('conversation', state => states.push(state));
    expect(states).toEqual(['idle']);
    expect(fetch).toHaveBeenCalledTimes(1);
    stop();
  });

  it('keeps a disconnected run visible until history reload succeeds', async () => {
    const fetch = vi
      .fn()
      .mockRejectedValueOnce(new TypeError('Offline'))
      .mockResolvedValueOnce(new Response(JSON.stringify({ body: { messages: [], cursor: null } })))
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            body: { messages: [{ id: 'answer', role: 'assistant', content: 'Recovered answer' }], cursor: null }
          })
        )
      );
    vi.stubGlobal('fetch', fetch);
    const socket = fakeSocket();
    const transport = createAssistantTransport('/api', 'note', socket);
    const states: AssistantRunState[] = [];
    const stop = transport.subscribeRunState!('conversation', state => states.push(state));
    const iterator = iterate(transport.openRun('conversation', { prompt: 'question' }, new AbortController().signal));
    const first = iterator.next();
    socket.close();
    await expect(first).rejects.toBeInstanceOf(AssistantConnectionError);
    expect(states[states.length - 1]).toBe('disconnected');
    await expect(transport.getMessages('conversation')).rejects.toThrow('Offline');
    expect(states[states.length - 1]).toBe('disconnected');
    await transport.getMessages('conversation', 'older-page');
    expect(states[states.length - 1]).toBe('disconnected');
    await expect(transport.getMessages('conversation')).resolves.toEqual({
      messages: [{ id: 'answer', role: 'assistant', content: 'Recovered answer' }],
      earlierCursor: null
    });
    expect(states[states.length - 1]).toBe('idle');
    expect(fetch).toHaveBeenCalledTimes(3);
    stop();
  });

  it('clears a retained run on disconnect and releases listeners', async () => {
    const socket = fakeSocket();
    const transport = createAssistantTransport('/api', 'note', socket);
    const controller = new AbortController();
    const iterator = iterate(transport.openRun('conversation', { prompt: 'question' }, controller.signal));
    const first = iterator.next();
    socket.emit({ conversationId: 'conversation', type: 'run.started', payload: { runId: 'run' } });
    await first;
    controller.abort();
    await expect(iterator.next()).rejects.toThrow();
    const states: AssistantRunState[] = [];
    const stop = transport.subscribeRunState!('conversation', running => states.push(running));
    socket.close();
    expect(states).toEqual(['running', 'disconnected']);
    expect(socket.listenerCount()).toBe(0);
    expect(socket.closeListenerCount()).toBe(0);
    stop();
  });

  it('follows an abandoned accepted run until completion and releases socket listeners', async () => {
    const socket = fakeSocket();
    const transport = createAssistantTransport('/api', 'note', socket);
    const states: AssistantRunState[] = [];
    const stop = transport.subscribeRunState!('conversation', running => states.push(running));
    const controller = new AbortController();
    const iterator = iterate(transport.openRun('conversation', { prompt: 'question' }, controller.signal));
    const first = iterator.next();
    socket.emit({ conversationId: 'conversation', type: 'run.started', payload: { runId: 'run' } });
    expect((await first).value).toEqual({ type: 'run.started', runId: 'run' });
    controller.abort();
    await expect(iterator.next()).rejects.toThrow();
    const reopened: AssistantRunState[] = [];
    const stopReopened = transport.subscribeRunState!('conversation', running => reopened.push(running));
    expect(reopened).toEqual(['running']);
    socket.emit({ conversationId: 'conversation', type: 'run.completed', payload: { runId: 'run' } });
    expect(reopened).toEqual(['running', 'idle']);
    expect(states).toContain('running');
    expect(socket.listenerCount()).toBe(0);
    expect(socket.closeListenerCount()).toBe(0);
    stop();
    stopReopened();
  });
});
