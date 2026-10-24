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
import {
  AssistantConnectionError,
  type AssistantAnswerMessage,
  type AssistantRunEvent
} from '@/entities/assistant/model/assistantContract';
import { AssistantHttpError } from '@/entities/assistant/model/assistantTransport';
import { answerToolCalls } from '@/entities/assistant/model/messageHistory';
import type { AssistantDataSource } from '../model/assistantDataSource';
import { createAssistantFixture, type AssistantFixtureOptions } from './assistantFixture';
import { ASSISTANT_HISTORY_CONVERSATIONS, FIXTURE_NOW } from './assistantFixtureData';

const createFixture = (options: Pick<AssistantFixtureOptions, 'scenario' | 'delayMs'> = {}) =>
  createAssistantFixture({
    conversations: ASSISTANT_HISTORY_CONVERSATIONS,
    now: FIXTURE_NOW,
    delayMs: 0,
    ...options
  });

const collect = async (source: AssistantDataSource, id = 'c1', content = 'Compare this month with last month') => {
  const events: AssistantRunEvent[] = [];
  for await (const event of source.run(id, content, new AbortController().signal)) events.push(event);
  return events;
};

afterEach(() => {
  vi.useRealTimers();
});

describe('assistant panel fixture', () => {
  it('creates writable conversations without colliding with empty or sparse supplied histories', async () => {
    const empty = createAssistantFixture({ conversations: [], now: FIXTURE_NOW, delayMs: 0 });

    const first = await empty.api.createConversation({});
    await collect(empty, first.id, 'Question in an initially empty source');

    expect((await empty.api.getMessages(first.id)).messages).toContainEqual(
      expect.objectContaining({ role: 'user', content: 'Question in an initially empty source' })
    );

    const seed = [ASSISTANT_HISTORY_CONVERSATIONS[0], ASSISTANT_HISTORY_CONVERSATIONS[2]];
    const source = createAssistantFixture({ conversations: seed, now: FIXTURE_NOW, delayMs: 0 });
    const existing = await source.api.getMessages('c1');

    const created = await source.api.createConversation({});
    const next = await source.api.createConversation({});

    expect(seed.map(value => value.conversation.id)).not.toContain(created.id);
    expect(seed.map(value => value.conversation.id)).not.toContain(next.id);
    expect(next.id).not.toBe(created.id);

    await collect(source, created.id, 'Question belonging only to the new conversation');

    expect((await source.api.getMessages(created.id)).messages).toContainEqual(
      expect.objectContaining({ role: 'user', content: 'Question belonging only to the new conversation' })
    );
    expect(await source.api.getMessages('c1')).toEqual(existing);
  });

  it('persists accepted questions and completed answer snapshots for history reload', async () => {
    const source = createFixture();
    const before = await source.api.getMessages('c1');

    const events = await collect(source);

    expect(events[0].type).toBe('run.started');
    expect(events.at(-1)?.type).toBe('run.completed');
    const snapshots = events.filter(event => event.type === 'message.done');
    expect(snapshots[0].messageId).not.toBe(snapshots.at(-1)?.messageId);

    const latest = await source.api.getMessages('c1');
    expect(latest.cursor).toBe(before.cursor);
    expect(latest.messages.slice(0, before.messages.length)).toEqual(before.messages);
    expect(latest.messages[before.messages.length]).toMatchObject({
      role: 'user',
      content: 'Compare this month with last month'
    });
    const answer = latest.messages.at(-1) as AssistantAnswerMessage;
    expect(answer.id).toBe(snapshots.at(-1)?.messageId);
    expect(answer.segments.map(({ id, content }) => ({ id, content }))).toEqual(
      snapshots.map(event => ({ id: event.messageId, content: event.content }))
    );
    expect(answerToolCalls(answer)).toEqual(
      events
        .filter(event => event.type === 'tool_call.started')
        .map(event => ({ id: event.toolCallId, name: event.name }))
    );

    await source.api.deleteConversation('c1');

    await expect(source.api.getMessages('c1')).rejects.toMatchObject({ status: 404 });
  });

  it('stores the first question under the confirmed conversation id', async () => {
    const source = createFixture();
    const created = await source.api.createConversation({});

    expect(await source.api.getMessages(created.id)).toEqual({ messages: [], cursor: null });

    await collect(source, created.id, 'Explain the results');

    const page = await source.api.getMessages(created.id);
    expect(page.messages[0]).toMatchObject({ role: 'user', content: 'Explain the results' });
    expect(page.messages.map(message => message.role)).toEqual(['user', 'assistant']);
    expect((await source.api.listConversations())[0].id).toBe(created.id);
  });

  it('leaves the list unchanged after creation failure and permits an explicit retry', async () => {
    const source = createFixture({ scenario: 'create-failure' });
    const before = await source.api.listConversations();

    await expect(source.api.createConversation({})).rejects.toBeInstanceOf(AssistantHttpError);
    expect(await source.api.listConversations()).toEqual(before);

    const created = await source.api.createConversation({});
    await collect(source, created.id, 'Retry after creation failed');

    const page = await source.api.getMessages(created.id);
    expect(page.messages.map(message => message.role)).toEqual(['user', 'assistant']);
    expect(page.messages[0]).toMatchObject({ content: 'Retry after creation failed' });
  });

  it('rejects a send before acceptance without persisting it and accepts one explicit retry', async () => {
    const source = createFixture({ scenario: 'send-rejection' });
    const before = await source.api.getMessages('c1');

    const rejected = await collect(source);

    expect(rejected.map(event => event.type)).toEqual(['run.failed']);
    expect(await source.api.getMessages('c1')).toEqual(before);

    const events = await collect(source);

    expect(events[0].type).toBe('run.started');
    expect(events[events.length - 1].type).toBe('run.completed');
    const page = await source.api.getMessages('c1');
    expect(
      page.messages.filter(
        message => message.role === 'user' && message.content === 'Compare this month with last month'
      )
    ).toHaveLength(1);
  });

  it('models unknown acceptance without inventing started or terminal knowledge', async () => {
    const source = createFixture({ scenario: 'unknown' });
    const events: AssistantRunEvent[] = [];
    const consume = async () => {
      for await (const event of source.run('c1', 'Question with unknown outcome', new AbortController().signal)) {
        events.push(event);
      }
    };

    await expect(consume()).rejects.toBeInstanceOf(AssistantConnectionError);
    expect(events).toEqual([]);
    const page = await source.api.getMessages('c1');
    expect(page.messages.at(-1)).toMatchObject({
      role: 'user',
      content: 'Question with unknown outcome'
    });

    const next = await collect(source, 'c1', 'A separate explicit question');

    expect(next.at(-1)?.type).toBe('run.completed');
  });

  it('keeps a failed current round out of persisted history and accepts a separate later question', async () => {
    const source = createFixture({ scenario: 'run-failure' });
    const before = await source.api.getMessages('c1');

    const events = await collect(source);

    expect(events.map(event => event.type)).toEqual(['run.started', 'message.delta', 'run.failed']);
    const saved = await source.api.getMessages('c1');
    expect(saved.messages.slice(0, before.messages.length)).toEqual(before.messages);
    expect(saved.messages.at(-1)).toMatchObject({ role: 'user', content: 'Compare this month with last month' });

    const next = await collect(source, 'c1', 'A new question');

    expect(next.at(-1)?.type).toBe('run.completed');
    expect((await source.api.getMessages('c1')).messages.at(-2)).toMatchObject({
      role: 'user',
      content: 'A new question'
    });
  });

  it('isolates stored conversations and one-shot failures between source instances', async () => {
    const first = createFixture({ scenario: 'send-rejection' });
    const second = createFixture({ scenario: 'send-rejection' });
    const listed = await second.api.listConversations();
    const history = await second.api.getMessages('c1');

    await collect(first);
    await collect(first);
    await first.api.createConversation({});
    await first.api.deleteConversation('c2');

    expect(await second.api.listConversations()).toEqual(listed);
    expect(await second.api.getMessages('c1')).toEqual(history);

    const rejected = await collect(second);

    expect(rejected[0].type).toBe('run.failed');

    const completed = await collect(second);

    expect(completed.at(-1)?.type).toBe('run.completed');
  });

  it('reports read-only, missing and invalid cursor targets with HTTP errors', async () => {
    const source = createFixture();
    const before = await source.api.listConversations();

    await expect(collect(source, 'c3')).rejects.toMatchObject({ status: 403 });
    await expect(source.api.deleteConversation('c3')).rejects.toMatchObject({ status: 403 });
    await expect(collect(source, 'missing')).rejects.toMatchObject({ status: 404 });
    await expect(source.api.deleteConversation('missing')).rejects.toBeInstanceOf(AssistantHttpError);
    await expect(source.api.getMessages('missing')).rejects.toMatchObject({ status: 404 });
    await expect(source.api.getMessages('c1', 'invalid')).rejects.toMatchObject({ status: 400 });
    expect(await source.api.listConversations()).toEqual(before);
  });

  it('aborts before acceptance and releases the conversation for deletion', async () => {
    vi.useFakeTimers();
    const source = createFixture({ delayMs: 40 });
    const controller = new AbortController();
    const iterator = source.run('c1', 'Not accepted', controller.signal)[Symbol.asyncIterator]();

    const next = iterator.next();

    await expect(source.api.deleteConversation('c1')).rejects.toMatchObject({ status: 409 });
    await expect(collect(source)).rejects.toMatchObject({ status: 409 });

    controller.abort();

    await expect(next).rejects.toMatchObject({ name: 'AbortError' });
    expect(vi.getTimerCount()).toBe(0);

    const history = source.api.getMessages('c1');
    await vi.advanceTimersByTimeAsync(40);

    expect(
      (await history).messages.some(message => message.role === 'user' && message.content === 'Not accepted')
    ).toBe(false);

    const deletion = source.api.deleteConversation('c1');
    await vi.advanceTimersByTimeAsync(40);
    await deletion;
  });

  it('cleans up abort after acceptance without rolling back or resending the accepted question', async () => {
    vi.useFakeTimers();
    const source = createFixture({ delayMs: 20 });
    const controller = new AbortController();
    const iterator = source.run('c1', 'Accepted before abort', controller.signal)[Symbol.asyncIterator]();

    const started = iterator.next();
    await vi.advanceTimersByTimeAsync(20);

    expect((await started).value).toMatchObject({ type: 'run.started' });

    const next = iterator.next();
    controller.abort();

    await expect(next).rejects.toMatchObject({ name: 'AbortError' });
    expect(vi.getTimerCount()).toBe(0);

    const history = source.api.getMessages('c1');
    await vi.advanceTimersByTimeAsync(20);
    const page = await history;

    expect(
      page.messages.filter(message => message.role === 'user' && message.content === 'Accepted before abort')
    ).toHaveLength(1);
    expect(page.messages.at(-1)).toMatchObject({ role: 'user', content: 'Accepted before abort' });

    const deletion = source.api.deleteConversation('c1');
    await vi.advanceTimersByTimeAsync(20);
    await deletion;
  });

  it('releases busy state when a consumer explicitly closes the iterator', async () => {
    const source = createFixture();
    const iterator = source.run('c1', 'Accepted once', new AbortController().signal)[Symbol.asyncIterator]();

    const started = await iterator.next();

    expect(started.value).toMatchObject({ type: 'run.started' });
    await expect(source.api.deleteConversation('c1')).rejects.toMatchObject({ status: 409 });

    await iterator.return?.();

    await expect(source.api.deleteConversation('c1')).resolves.toBeUndefined();
  });

  it('rejects an already aborted signal without persisting or consuming its one-shot scenario', async () => {
    const source = createFixture({ scenario: 'send-rejection' });
    const controller = new AbortController();
    const before = await source.api.getMessages('c1');
    controller.abort();

    const iterator = source.run('c1', 'Never accepted', controller.signal)[Symbol.asyncIterator]();

    await expect(iterator.next()).rejects.toMatchObject({ name: 'AbortError' });
    expect(await source.api.getMessages('c1')).toEqual(before);

    const rejected = await collect(source);

    expect(rejected[0].type).toBe('run.failed');

    await expect(source.api.deleteConversation('c1')).resolves.toBeUndefined();
  });

  it('lets a different conversation finish while the first consumer remains active', async () => {
    const source = createFixture();
    const first = source.run('c1', 'Keep this consumer open', new AbortController().signal)[Symbol.asyncIterator]();

    const started = await first.next();

    expect(started.value).toMatchObject({ type: 'run.started' });

    const completed = await collect(source, 'c2', 'Independent question');

    expect(completed.at(-1)?.type).toBe('run.completed');
    await expect(source.api.deleteConversation('c1')).rejects.toMatchObject({ status: 409 });
    expect((await source.api.getMessages('c2')).messages.at(-2)).toMatchObject({
      role: 'user',
      content: 'Independent question'
    });

    await first.return?.();

    await expect(source.api.deleteConversation('c1')).resolves.toBeUndefined();
  });
});
