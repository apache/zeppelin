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

import { afterEach, describe, expect, it, vi } from 'vitest';

import type { AssistantSendMessage, AssistantSocketEvent } from '@zeppelin/sdk';

import {
  AssistantHttpError,
  createAssistantTransport,
  mapSocketEvent,
  scopeTransport,
  socketRun,
  toVisibleMessages
} from './assistantTransport';
import {
  AssistantConnectionError,
  AssistantStreamError,
  type AssistantAnswerMessage,
  type AssistantConversation,
  type AssistantRunEvent
} from './assistantContract';
import { answerText, answerToolCalls, joinPages } from './messageHistory';

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

const forNote = (noteId: string, onAuthError?: (status: number) => void) =>
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
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('lists the most recently active conversation first', async () => {
    const body = [
      { id: 'old', ownerId: 'alice', canSendMessage: true, updatedAt: '2026-10-01T00:00:00Z' },
      { id: 'new', ownerId: 'bob', canSendMessage: false, updatedAt: '2026-10-03T00:00:00Z' },
      { id: 'middle', ownerId: 'carol', canSendMessage: true, updatedAt: '2026-10-02T00:00:00Z' }
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
    expect(conversations.map(conversation => conversation.id)).toEqual(['new', 'middle', 'old']);
    expect(conversations.map(conversation => conversation.updatedAt)).toEqual([
      '2026-10-03T00:00:00Z',
      '2026-10-02T00:00:00Z',
      '2026-10-01T00:00:00Z'
    ]);
    expect(conversations[0]).toMatchObject({ ownerId: 'bob', canSendMessage: false });
  });

  it('uses the per-note conversation endpoints and unwraps Zeppelin JSON responses', async () => {
    const json = (body: unknown, status = 200) =>
      new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        json({
          status: 'OK',
          body: [
            {
              id: 'conv-1',
              title: 'First',
              noteId: 'note/one',
              ownerId: 'alice',
              canSendMessage: false,
              updatedAt: '2026-10-01T00:00:00Z'
            }
          ]
        })
      )
      .mockResolvedValueOnce(
        json(
          {
            status: 'CREATED',
            body: {
              id: 'conv-2',
              title: 'Load the data',
              ownerId: 'bob',
              canSendMessage: true,
              updatedAt: '2026-10-02T00:00:00Z'
            }
          },
          201
        )
      )
      .mockResolvedValueOnce(
        json({
          status: 'OK',
          body: {
            messages: [
              { id: 'message-2', role: 'assistant', content: 'hi there' },
              { id: 'message-1', role: 'user', content: 'hello' }
            ]
          }
        })
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);
    const transport = forNote('note/one');

    await expect(transport.listConversations()).resolves.toEqual([
      { id: 'conv-1', title: 'First', ownerId: 'alice', canSendMessage: false, updatedAt: '2026-10-01T00:00:00Z' }
    ]);
    await expect(transport.createConversation({ title: '  Load   the data  ' })).resolves.toEqual({
      id: 'conv-2',
      title: 'Load the data',
      ownerId: 'bob',
      canSendMessage: true,
      updatedAt: '2026-10-02T00:00:00Z'
    });
    // The server pages latest first; the panel wants oldest first.
    await expect(transport.getMessages('conv/1')).resolves.toEqual({
      messages: [
        { id: 'message-1', role: 'user', content: 'hello' },
        {
          id: 'message-2',
          role: 'assistant',
          segments: [{ id: 'message-2', content: 'hi there' }]
        }
      ],
      cursor: null
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
      body: JSON.stringify({ title: '  Load   the data  ' })
    });
  });

  it('preserves a long conversation title when creating it', async () => {
    const title = `  ${'A'.repeat(85)}  `;
    const fetchMock = vi.fn().mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          status: 'CREATED',
          body: { id: 'conv-1', title, ownerId: 'alice', canSendMessage: true, updatedAt: '2026-10-02T00:00:00Z' }
        }),
        { status: 201 }
      )
    );
    vi.stubGlobal('fetch', fetchMock);

    await expect(forNote('n').createConversation({ title })).resolves.toMatchObject({ title });
    expect(fetchMock.mock.calls[0][1].body).toBe(JSON.stringify({ title }));
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
        .mockResolvedValueOnce(new Response(null, { status: 401 }))
        .mockResolvedValueOnce(new Response(null, { status: 405 }))
        .mockResolvedValueOnce(new Response(null, { status: 500 }))
    );
    const transport = forNote('n', onAuthError);

    await expect(transport.listConversations()).rejects.toMatchObject({ status: 401 });
    await expect(transport.createConversation({})).rejects.toMatchObject({ status: 405 });
    await expect(transport.getMessages('c')).rejects.toBeInstanceOf(AssistantHttpError);

    expect(onAuthError.mock.calls).toEqual([[401], [405]]);
  });

  it('does not redirect for an auth response that arrives after the note scope ends', async () => {
    let resolveFetch: (response: Response) => void = () => undefined;
    const fetch = vi.fn(
      (_url: string, _init: RequestInit) => new Promise<Response>(resolve => (resolveFetch = resolve))
    );
    vi.stubGlobal('fetch', fetch);
    const onAuthError = vi.fn();
    const scoped = scopeTransport(forNote('n', onAuthError));
    const request = scoped.transport.listConversations();
    const signal = fetch.mock.calls[0][1].signal as AbortSignal;

    scoped.deactivate();
    expect(signal.aborted).toBe(true);
    resolveFetch(new Response(null, { status: 401 }));
    await expect(request).rejects.toMatchObject({ name: 'AbortError' });
    expect(onAuthError).not.toHaveBeenCalled();
  });

  it('sends over the socket and yields only this conversation until a terminal event', async () => {
    const socket = fakeSocket();
    const transport = createAssistantTransport('https://example.test/api', 'n', socket);
    const run = iterate(transport.openRun('c1', { content: 'improve' }, new AbortController().signal));
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
      { type: 'tool_call.done', toolCallId: 't1' },
      { type: 'message.delta', messageId: 'm1', delta: '한글' },
      { type: 'run.completed', runId: 'run_1' }
    ]);
    expect(socket.listenerCount()).toBe(0);
  });

  it('ends on a run failure that arrives before run.started', async () => {
    const socket = fakeSocket();
    const pending = collect(socketRun(socket, message(), new AbortController().signal));
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

  it('sends again after an abandoned request and leaves a duplicate to the server', async () => {
    const socket = fakeSocket();
    const leave = new AbortController();
    const first = iterate(socketRun(socket, message('first'), leave.signal));
    const response = first.next();
    leave.abort();
    await expect(response).rejects.toMatchObject({ name: 'AbortError' });
    expect(socket.listenerCount()).toBe(0);
    expect(socket.closeListenerCount()).toBe(0);

    const second = collect(socketRun(socket, message('second'), new AbortController().signal));
    expect(socket.sent).toHaveLength(2);
    socket.emit({ conversationId: 'c1', type: 'run.failed', payload: { runId: 'second', error: { status: 409 } } });
    await expect(second).resolves.toEqual([
      {
        type: 'run.failed',
        runId: 'second',
        message: 'This conversation is still answering. Try again when it finishes.'
      }
    ]);
  });

  it('ends the run and releases its listeners when the connection closes', async () => {
    const socket = fakeSocket();
    const pending = collect(socketRun(socket, message(), new AbortController().signal));
    socket.close();
    await expect(pending).rejects.toBeInstanceOf(AssistantConnectionError);
    expect(socket.listenerCount()).toBe(0);
    expect(socket.closeListenerCount()).toBe(0);
  });

  it('rethrows a failed send and releases its listeners', async () => {
    const socket = fakeSocket();
    socket.send.mockImplementationOnce(() => {
      throw new DOMException('Notebook changed', 'AbortError');
    });
    await expect(collect(socketRun(socket, message(), new AbortController().signal))).rejects.toMatchObject({
      name: 'AbortError'
    });
    expect(socket.listenerCount()).toBe(0);
    expect(socket.closeListenerCount()).toBe(0);
  });

  it('keeps an active run going when a duplicate send is rejected with 409', async () => {
    const socket = fakeSocket();
    const first = iterate(socketRun(socket, message('first'), new AbortController().signal));
    const firstEvent = first.next();
    socket.emit({ conversationId: 'c1', type: 'run.started', payload: { runId: 'run_old' } });
    expect((await firstEvent).value).toEqual({ type: 'run.started', runId: 'run_old' });

    const second = collect(socketRun(socket, message('second'), new AbortController().signal));
    expect(socket.sent).toHaveLength(2);
    socket.emit({ conversationId: 'c1', type: 'run.failed', payload: { runId: 'run_dup', error: { status: 409 } } });
    await expect(second).resolves.toEqual([
      {
        type: 'run.failed',
        runId: 'run_dup',
        message: 'This conversation is still answering. Try again when it finishes.'
      }
    ]);

    socket.emit({ conversationId: 'c1', type: 'message.delta', payload: { messageId: 'old', delta: 'tail' } });
    socket.emit({ conversationId: 'c1', type: 'run.completed', payload: { runId: 'run_old' } });
    expect((await first.next()).value).toEqual({ type: 'message.delta', messageId: 'old', delta: 'tail' });
    expect((await first.next()).value).toEqual({ type: 'run.completed', runId: 'run_old' });
    expect((await first.next()).done).toBe(true);
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
    expect(page).toEqual({
      messages: [{ id: 'm3', role: 'assistant', segments: [{ id: 'm3', content: 'part one.' }] }],
      cursor: 'm3'
    });
    expect(
      joinPages(page.messages, [
        { id: 'm5', role: 'assistant', segments: [{ id: 'm5', content: 'part two.' }] },
        { id: 'm6', role: 'user', content: 'next' }
      ])
    ).toEqual([
      {
        id: 'm5',
        role: 'assistant',
        segments: [
          { id: 'm3', content: 'part one.' },
          { id: 'm5', content: 'part two.' }
        ]
      },
      { id: 'm6', role: 'user', content: 'next' }
    ]);
  });

  it('explains a missing assistant configuration instead of the bare status', () => {
    expect(new AssistantHttpError(503).message).toBe(
      'The assistant is not configured on this server. Ask an administrator to enable it.'
    );
    expect(new AssistantHttpError(500).message).toBe('Assistant request failed with HTTP 500');
  });

  it('releases every listener when aborted before the run starts', async () => {
    const socket = fakeSocket();
    const controller = new AbortController();
    const run = iterate(socketRun(socket, message(), controller.signal));
    const pending = run.next();
    await vi.waitFor(() => expect(socket.listenerCount()).toBe(1));

    controller.abort();

    await expect(pending).rejects.toMatchObject({ name: 'AbortError' });
    expect(socket.listenerCount()).toBe(0);
    expect(socket.closeListenerCount()).toBe(0);
  });

  it('ends a run with a connection error when the notebook socket closes', async () => {
    const socket = fakeSocket();
    const pending = collect(socketRun(socket, message(), new AbortController().signal));
    socket.emit({ conversationId: 'c1', type: 'run.started', payload: { runId: 'r1' } });
    socket.close();
    await expect(pending).rejects.toBeInstanceOf(AssistantConnectionError);
    expect(socket.closeListenerCount()).toBe(0);
  });

  it('reports a retryable error and releases its listeners when the run goes quiet past the idle timeout', async () => {
    vi.useFakeTimers();
    const socket = fakeSocket();
    const pending = collect(socketRun(socket, message(), new AbortController().signal, 1000));
    const outcome = pending.then(
      () => null,
      (error: unknown) => error
    );
    await vi.advanceTimersByTimeAsync(1000);
    expect(await outcome).toBeInstanceOf(AssistantStreamError);
    expect(socket.listenerCount()).toBe(0);
    expect(socket.closeListenerCount()).toBe(0);
  });

  it('fails the run on an event with a malformed payload', async () => {
    const socket = fakeSocket();
    const pending = collect(socketRun(socket, message(), new AbortController().signal));
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
    const run = iterate(scoped.transport.openRun('t', { content: 'hi' }, new AbortController().signal));
    await run.next();
    expect(inner.listConversations).toHaveBeenCalledWith(expect.any(AbortSignal));

    scoped.deactivate();
    resolveList([]);

    await expect(list).rejects.toMatchObject({ name: 'AbortError' });
    expect(runSignal?.aborted).toBe(true);
    await expect(scoped.transport.getMessages('t')).rejects.toMatchObject({ name: 'AbortError' });
  });

  it('retires an old REST request permanently while a fresh note scope can read', async () => {
    let resolveOld: (conversations: []) => void = () => undefined;
    const inner = {
      listConversations: vi
        .fn()
        .mockImplementationOnce(() => new Promise<[]>(resolve => (resolveOld = resolve)))
        .mockResolvedValueOnce([]),
      createConversation: vi.fn(),
      deleteConversation: vi.fn(),
      getMessages: vi.fn(),
      openRun: vi.fn()
    };
    const oldScope = scopeTransport(inner);
    const oldRead = oldScope.transport.listConversations();
    const oldSignal = inner.listConversations.mock.calls[0][0] as AbortSignal;

    oldScope.deactivate();
    expect(oldSignal.aborted).toBe(true);
    const newScope = scopeTransport(inner);
    await expect(newScope.transport.listConversations()).resolves.toEqual([]);
    resolveOld([]);
    await expect(oldRead).rejects.toMatchObject({ name: 'AbortError' });
    expect(oldScope.active).toBe(false);
  });

  it('passes a caller abort to REST without retiring the note scope', async () => {
    let resolveRead: (conversations: []) => void = () => undefined;
    const inner = {
      listConversations: vi
        .fn()
        .mockImplementationOnce(() => new Promise<[]>(resolve => (resolveRead = resolve)))
        .mockResolvedValueOnce([]),
      createConversation: vi.fn(),
      deleteConversation: vi.fn(),
      getMessages: vi.fn(),
      openRun: vi.fn()
    };
    const scoped = scopeTransport(inner);
    const caller = new AbortController();
    const read = scoped.transport.listConversations(caller.signal);
    const requestSignal = inner.listConversations.mock.calls[0][0] as AbortSignal;

    caller.abort();
    expect(requestSignal.aborted).toBe(true);
    resolveRead([]);
    await expect(read).rejects.toMatchObject({ name: 'AbortError' });
    expect(scoped.active).toBe(true);
    await expect(scoped.transport.listConversations()).resolves.toEqual([]);
  });

  it('retires pending creations and active runs when the host session aborts', async () => {
    const session = new AbortController();
    let resolveCreate: (value: AssistantConversation) => void = () => undefined;
    let runSignal: AbortSignal | undefined;
    const inner = {
      listConversations: vi.fn().mockResolvedValue([]),
      createConversation: vi.fn(
        () =>
          new Promise<AssistantConversation>(resolve => {
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
    const creation = scoped.transport.createConversation({});
    const run = iterate(scoped.transport.openRun('c1', { content: 'hi' }, new AbortController().signal));
    await run.next();
    const terminal = run.next();
    session.abort();
    resolveCreate({
      id: 'old-account-conversation',
      ownerId: 'alice',
      canSendMessage: true,
      updatedAt: '2026-10-01T00:00:00Z'
    });
    await expect(creation).rejects.toMatchObject({ name: 'AbortError' });
    await expect(terminal).rejects.toMatchObject({ name: 'AbortError' });
    expect(runSignal?.aborted).toBe(true);
    expect(scoped.active).toBe(false);
    scoped.deactivate();
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
      {
        id: 'a3',
        role: 'assistant',
        segments: [
          { id: 'a2', content: 'Reading.' },
          { id: 'a3', content: 'Done.' }
        ]
      }
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
      segments: [
        { id: 'a1', content: '', toolCalls: [{ id: 'c1', name: 'list_paragraphs' }] },
        { id: 'a2', content: 'Two paragraphs.' }
      ]
    });
    expect(answerToolCalls(visible[1] as AssistantAnswerMessage)).toEqual([{ id: 'c1', name: 'list_paragraphs' }]);
    expect(answerText(visible[1] as AssistantAnswerMessage)).toBe('Two paragraphs.');
    expect(answerToolCalls(visible[3] as AssistantAnswerMessage)).toEqual([]);
  });

  it('preserves the order of interleaved answer and tool rounds from server history', () => {
    const visible = toVisibleMessages([
      { id: 'u1', role: 'user', content: 'Investigate' },
      { id: 'a1', role: 'assistant', content: 'First.', toolCalls: [{ id: 'c1', name: 'list_paragraphs' }] },
      { id: 't1', role: 'tool', content: '[]' },
      { id: 'a2', role: 'assistant', content: 'Next.', toolCalls: [{ id: 'c2', name: 'read_paragraph' }] },
      { id: 't2', role: 'tool', content: '{}' },
      { id: 'a3', role: 'assistant', content: 'Final.' }
    ]);
    expect(visible[1]).toMatchObject({
      id: 'a3',
      segments: [
        { id: 'a1', content: 'First.', toolCalls: [{ id: 'c1', name: 'list_paragraphs' }] },
        { id: 'a2', content: 'Next.', toolCalls: [{ id: 'c2', name: 'read_paragraph' }] },
        { id: 'a3', content: 'Final.' }
      ]
    });
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
    expect(earlier[1]).toEqual({
      id: 'a1',
      role: 'assistant',
      segments: [{ id: 'a1', content: '', toolCalls: call.toolCalls }]
    });
    expect(joinPages(earlier, toVisibleMessages([result, answer]))).toEqual(
      toVisibleMessages([question, call, result, answer])
    );
    // An intervening page containing only tool results must not lose the pending calls either.
    expect(joinPages(joinPages(earlier, toVisibleMessages([result])), toVisibleMessages([answer]))).toEqual([
      question,
      {
        id: answer.id,
        role: answer.role,
        segments: [
          { id: 'a1', content: '', toolCalls: call.toolCalls },
          { id: 'a2', content: 'Two paragraphs.' }
        ]
      }
    ]);
    const nextTurn = [
      { id: 'u2', role: 'user' as const, content: 'Another question' },
      { id: 'a3', role: 'assistant' as const, content: 'Another answer' }
    ];
    expect(joinPages(earlier, toVisibleMessages(nextTurn))).toEqual([
      ...earlier,
      nextTurn[0],
      { id: 'a3', role: 'assistant', segments: [{ id: 'a3', content: 'Another answer' }] }
    ]);
    expect(toVisibleMessages([question, call, ...nextTurn])).toEqual([
      ...earlier,
      nextTurn[0],
      { id: 'a3', role: 'assistant', segments: [{ id: 'a3', content: 'Another answer' }] }
    ]);
  });

  it('keeps a trailing tool round after answer text and deduplicates call ids at page boundaries', () => {
    const call = { id: 'c1', name: 'list_paragraphs' };
    const earlier = toVisibleMessages([
      { id: 'a1', role: 'assistant', content: 'Looking.' },
      { id: 'a2', role: 'assistant', content: '', toolCalls: [call] }
    ]);
    expect(earlier).toEqual([
      {
        id: 'a2',
        role: 'assistant',
        segments: [
          { id: 'a1', content: 'Looking.' },
          { id: 'a2', content: '', toolCalls: [call] }
        ]
      }
    ]);
    const later = toVisibleMessages([
      { id: 'a2', role: 'assistant', content: '', toolCalls: [call] },
      { id: 'a3', role: 'assistant', content: 'Done.' }
    ]);
    expect(joinPages(earlier, later)).toEqual([
      {
        id: 'a3',
        role: 'assistant',
        segments: [
          { id: 'a1', content: 'Looking.' },
          { id: 'a2', content: '', toolCalls: [call] },
          { id: 'a3', content: 'Done.' }
        ]
      }
    ]);
  });
});

describe('conversation list', () => {
  it('uses the server conversation response without a running field', async () => {
    const conversation = {
      id: 'conversation',
      title: 'Question',
      ownerId: 'alice',
      canSendMessage: true,
      updatedAt: '2026-10-01T00:00:00Z'
    };
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ body: [conversation] })));
    vi.stubGlobal('fetch', fetch);
    const transport = createAssistantTransport('/api', 'note', fakeSocket());
    expect(await transport.listConversations()).toEqual([conversation]);
    expect(fetch).toHaveBeenCalledTimes(1);
  });
});
