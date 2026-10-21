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

import { StrictMode } from 'react';
import { act, renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import {
  AssistantConnectionError,
  type AssistantConversation,
  type AssistantConversationApi,
  type AssistantMessagePage,
  type AssistantRunEvent
} from '@/entities/assistant/model/assistantContract';
import type { AssistantDataSource } from './assistantDataSource';
import { createAssistantFixture } from '../fixtures/assistantFixture';
import { ASSISTANT_HISTORY_CONVERSATIONS, FIXTURE_NOW } from '../fixtures/assistantFixtureData';
import { AssistantPanelModel } from './assistantPanelModel';
import type { AssistantPanel } from './assistantPanelState';
import { useAssistantPanel } from './useAssistantPanel';

const conversation = (id: string, canSendMessage = true): AssistantConversation => ({
  id,
  title: `Conversation ${id}`,
  ownerId: 'fixture-owner',
  canSendMessage,
  updatedAt: '2026-10-19T00:00:00Z'
});

const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<T>((accept, fail) => {
    resolve = accept;
    reject = fail;
  });

  return { promise, resolve, reject };
};

/** Allows late events after abort to exercise stale-response handling. */
class RunFeed implements AsyncIterable<AssistantRunEvent> {
  private queue: (AssistantRunEvent | Error | null)[] = [];
  private waiting?: (value: AssistantRunEvent | Error | null) => void;

  push(...items: (AssistantRunEvent | Error | null)[]) {
    for (const item of items) {
      const waiting = this.waiting;
      if (waiting) {
        this.waiting = undefined;
        waiting(item);
      } else {
        this.queue.push(item);
      }
    }
  }

  async *[Symbol.asyncIterator]() {
    while (true) {
      const item = this.queue.length
        ? this.queue.shift()!
        : await new Promise<AssistantRunEvent | Error | null>(resolve => {
            this.waiting = resolve;
          });

      if (item instanceof Error) throw item;
      if (item === null) return;
      yield item;
    }
  }
}

const sourceHarness = () => {
  const feeds: RunFeed[] = [];
  const api = {
    listConversations: vi
      .fn<AssistantConversationApi['listConversations']>()
      .mockResolvedValue([conversation('a'), conversation('b'), conversation('read-only', false)]),
    createConversation: vi
      .fn<AssistantConversationApi['createConversation']>()
      .mockResolvedValue(conversation('created')),
    deleteConversation: vi.fn<AssistantConversationApi['deleteConversation']>().mockResolvedValue(undefined),
    getMessages: vi.fn<AssistantConversationApi['getMessages']>().mockResolvedValue({ messages: [], cursor: null })
  };
  const run = vi.fn((_id: string, _content: string, _signal: AbortSignal) => {
    const feed = new RunFeed();
    feeds.push(feed);
    return feed;
  });

  const source: AssistantDataSource = { api, run };
  return { source, api, run, feeds };
};

const mountPanel = async (source: AssistantDataSource) => {
  const panel = renderHook(() => useAssistantPanel(source));
  await act(async () => {});
  return panel;
};

type Panel = { result: { current: AssistantPanel } };

const sendQuestion = async (panel: Panel, question?: string) => {
  let completion!: Promise<void>;
  await act(async () => {
    completion = panel.result.current.send(question);
  });

  return { completion };
};

describe('useAssistantPanel', () => {
  it('keeps New local and preserves its draft without creating a conversation', async () => {
    const harness = sourceHarness();
    const panel = await mountPanel(harness.source);
    act(() => panel.result.current.setDraft('Local question'));
    const draftKey = panel.result.current.selectedKey;

    await act(async () => panel.result.current.select('a'));
    act(() => panel.result.current.setDraft('Question for A'));
    act(() => panel.result.current.newConversation());
    act(() => panel.result.current.newConversation());

    expect(panel.result.current.selectedKey).toBe(draftKey);
    expect(panel.result.current.entry.draft).toBe('Local question');
    expect(harness.api.createConversation).not.toHaveBeenCalled();
    expect(harness.run).not.toHaveBeenCalled();

    await act(async () => panel.result.current.select('a'));

    expect(panel.result.current.entry.draft).toBe('Question for A');
  });

  it('guards New during creation, permits an existing conversation send, and releases New on confirmation', async () => {
    const harness = sourceHarness();
    const creation = deferred<AssistantConversation>();
    harness.api.createConversation.mockImplementationOnce(() => creation.promise);
    const panel = await mountPanel(harness.source);
    const draftKey = panel.result.current.selectedKey;

    expect(draftKey).toBe('draft:0');

    const draftSend = await sendQuestion(panel, 'Draft A');

    expect(panel.result.current.creatingConversation).toBe(true);

    act(() => panel.result.current.newConversation());

    expect(panel.result.current.selectedKey).toBe(draftKey);

    await act(async () => panel.result.current.select('b'));
    const selectedKey = panel.result.current.selectedKey;

    act(() => panel.result.current.newConversation());

    expect(panel.result.current.selectedKey).toBe(selectedKey);

    const existingSend = await sendQuestion(panel, 'Existing B');

    expect(harness.run).toHaveBeenCalledWith('b', 'Existing B', expect.any(AbortSignal));

    await act(async () => creation.resolve(conversation('confirmed')));

    expect(panel.result.current.selectedKey).toBe(selectedKey);
    expect(panel.result.current.creatingConversation).toBe(false);

    act(() => panel.result.current.newConversation());

    const nextDraftKey = panel.result.current.selectedKey;
    expect(nextDraftKey).not.toBe(draftKey);
    expect(panel.result.current.entry).toMatchObject({ draft: '', phase: 'idle' });

    await act(async () => panel.result.current.select('confirmed'));

    expect(panel.result.current.entry).toMatchObject({ key: draftKey, phase: 'pending' });

    act(() => panel.result.current.newConversation());

    expect(panel.result.current.selectedKey).toBe(nextDraftKey);

    await act(async () => {
      harness.feeds[0].push({ type: 'run.failed', message: 'B stopped' });
      harness.feeds[1].push({ type: 'run.failed', message: 'A stopped' });
      await Promise.all([draftSend.completion, existingSend.completion]);
    });

    expect(panel.result.current.selectedKey).toBe(nextDraftKey);
    expect(harness.api.createConversation).toHaveBeenCalledTimes(1);
  });

  it('keeps the failed creation in its one draft slot without stealing another selection', async () => {
    const harness = sourceHarness();
    const creation = deferred<AssistantConversation>();
    harness.api.createConversation.mockImplementationOnce(() => creation.promise);
    const panel = await mountPanel(harness.source);
    const draftKey = panel.result.current.selectedKey;

    const sending = await sendQuestion(panel, 'Keep draft A');
    await act(async () => panel.result.current.select('b'));
    act(() => panel.result.current.setDraft('Keep B too'));
    const selectedKey = panel.result.current.selectedKey;

    await act(async () => {
      creation.reject(new Error('Creation unavailable'));
      await sending.completion;
    });

    expect(panel.result.current.selectedKey).toBe(selectedKey);
    expect(panel.result.current.entry.draft).toBe('Keep B too');
    expect(panel.result.current.creatingConversation).toBe(false);

    act(() => panel.result.current.newConversation());

    expect(panel.result.current.entry).toMatchObject({
      key: draftKey,
      draft: 'Keep draft A',
      phase: 'idle',
      failure: { message: 'Creation unavailable', kind: 'failed', recovery: 'send' }
    });

    act(() => panel.result.current.newConversation());

    expect(panel.result.current.selectedKey).toBe(draftKey);
    expect(harness.api.createConversation).toHaveBeenCalledTimes(1);
    expect(harness.run).not.toHaveBeenCalled();
  });

  it('uses the same creating draft as the selected deletion fallback without allocating another slot', async () => {
    const harness = sourceHarness();
    const creation = deferred<AssistantConversation>();
    harness.api.createConversation.mockImplementationOnce(() => creation.promise);
    const panel = await mountPanel(harness.source);
    const draftKey = panel.result.current.selectedKey;

    const sending = await sendQuestion(panel, 'Still creating');
    await act(async () => panel.result.current.select('a'));

    await act(async () => {
      const deleted = await panel.result.current.deleteConversation('a');

      expect(deleted).toBe(true);
    });

    expect(panel.result.current.entry).toMatchObject({ key: draftKey, phase: 'creating', draft: 'Still creating' });
    expect(panel.result.current.creatingConversation).toBe(true);

    act(() => panel.result.current.newConversation());

    expect(panel.result.current.selectedKey).toBe(draftKey);

    await act(async () => {
      creation.reject(new Error('Creation unavailable'));
      await sending.completion;
    });

    await act(async () => panel.result.current.select('b'));
    await act(async () => {
      const deleted = await panel.result.current.deleteConversation('b');

      expect(deleted).toBe(true);
    });

    expect(panel.result.current.selectedKey).toBe(draftKey);
    expect(panel.result.current.entry.draft).toBe('Still creating');
    expect(harness.api.createConversation).toHaveBeenCalledTimes(1);
  });

  it('allocates one fallback draft after deleting the selected confirmed conversation when its slot is empty', async () => {
    const harness = sourceHarness();
    const panel = await mountPanel(harness.source);
    const confirmedKey = panel.result.current.selectedKey;

    const sending = await sendQuestion(panel, 'Confirm this draft');
    await act(async () => {
      harness.feeds[0].push({ type: 'run.failed', message: 'Question rejected' });
      await sending.completion;
    });

    await act(async () => {
      const deleted = await panel.result.current.deleteConversation('created');

      expect(deleted).toBe(true);
    });

    const fallbackKey = panel.result.current.selectedKey;
    expect(fallbackKey).not.toBe(confirmedKey);
    expect(panel.result.current.entry).toMatchObject({ phase: 'idle', draft: '' });
    expect(panel.result.current.entry.conversation).toBeUndefined();

    await act(async () => panel.result.current.select('a'));
    act(() => panel.result.current.newConversation());

    expect(panel.result.current.selectedKey).toBe(fallbackKey);
    expect(harness.api.createConversation).toHaveBeenCalledTimes(1);
  });

  it('retains the draft and exposes a create failure without attempting a run', async () => {
    const harness = sourceHarness();
    harness.api.createConversation.mockRejectedValueOnce(new Error('Creation unavailable'));
    const panel = await mountPanel(harness.source);
    act(() => panel.result.current.setDraft('Keep this question'));

    await act(async () => panel.result.current.send());

    expect(panel.result.current.entry).toMatchObject({
      draft: 'Keep this question',
      phase: 'idle',
      failure: { message: 'Creation unavailable', kind: 'failed', recovery: 'send' }
    });
    expect(panel.result.current.entry.conversation).toBeUndefined();
    expect(harness.run).not.toHaveBeenCalled();

    await act(async () => panel.result.current.send('  '));

    expect(harness.api.createConversation).toHaveBeenCalledTimes(1);
  });

  it('saves the confirmed id before rejection and retries that same conversation', async () => {
    const harness = sourceHarness();
    const panel = await mountPanel(harness.source);
    const draftKey = panel.result.current.selectedKey;

    const first = await sendQuestion(panel, 'Try this question');
    await act(async () => {
      harness.feeds[0].push({ type: 'run.failed', message: 'Already running' });
      await first.completion;
    });

    expect(panel.result.current.entry).toMatchObject({
      key: draftKey,
      conversation: { id: 'created' },
      draft: 'Try this question',
      phase: 'idle',
      failure: { message: 'Already running', kind: 'failed', recovery: 'send' }
    });
    expect(panel.result.current.entry.messages).toEqual([]);

    const retry = await sendQuestion(panel);

    expect(harness.api.createConversation).toHaveBeenCalledTimes(1);
    expect(harness.run.mock.calls.map(([id]) => id)).toEqual(['created', 'created']);

    await act(async () => {
      harness.feeds[1].push({ type: 'run.started' }, { type: 'run.failed', message: 'Run stopped' });
      await retry.completion;
    });

    expect(panel.result.current.entry.messages).toHaveLength(1);
    expect(panel.result.current.entry.draft).toBe('Try this question');
  });

  it('prevents duplicate submits while creating, pending, or running in the same conversation', async () => {
    const harness = sourceHarness();
    const creation = deferred<AssistantConversation>();
    harness.api.createConversation.mockImplementationOnce(() => creation.promise);
    const panel = await mountPanel(harness.source);

    const sending = await sendQuestion(panel, 'One question');

    expect(panel.result.current.entry.phase).toBe('creating');

    await act(async () => panel.result.current.send('Duplicate while creating'));

    expect(harness.api.createConversation).toHaveBeenCalledTimes(1);
    expect(harness.run).not.toHaveBeenCalled();

    await act(async () => creation.resolve(conversation('confirmed')));

    expect(panel.result.current.entry.phase).toBe('pending');

    await act(async () => panel.result.current.send('Duplicate while pending'));

    expect(harness.run).toHaveBeenCalledTimes(1);

    await act(async () => harness.feeds[0].push({ type: 'run.started' }));

    expect(panel.result.current.entry.phase).toBe('running');

    await act(async () => panel.result.current.send('Duplicate while running'));

    expect(harness.run).toHaveBeenCalledTimes(1);

    await act(async () => {
      harness.feeds[0].push({ type: 'run.failed', message: 'Stopped' });
      await sending.completion;
    });

    expect(panel.result.current.entry).toMatchObject({ phase: 'idle', draft: 'One question' });
  });

  it('keeps late A events in A and returns to its existing consumer without resending or reloading', async () => {
    const harness = sourceHarness();
    const panel = await mountPanel(harness.source);
    await act(async () => panel.result.current.select('a'));

    const sending = await sendQuestion(panel, 'A question');
    await act(async () => harness.feeds[0].push({ type: 'run.started' }));
    await act(async () => panel.result.current.select('b'));
    act(() => panel.result.current.setDraft('B draft'));
    const selectedB = panel.result.current.entry;

    await act(async () => harness.feeds[0].push({ type: 'message.delta', messageId: 'a-answer', delta: 'Only A' }));

    expect(panel.result.current.entry).toBe(selectedB);
    expect(panel.result.current.entry).toMatchObject({ conversation: { id: 'b' }, draft: 'B draft', messages: [] });
    expect(panel.result.current.entry.answer).toBeUndefined();

    const loads = harness.api.getMessages.mock.calls.length;
    await act(async () => panel.result.current.select('a'));

    expect(panel.result.current.entry.answer).toMatchObject({ status: 'streaming', content: 'Only A' });
    expect(harness.run).toHaveBeenCalledTimes(1);
    expect(harness.api.getMessages).toHaveBeenCalledTimes(loads);

    await act(async () => {
      harness.feeds[0].push({ type: 'run.failed', message: 'Stopped' });
      await sending.completion;
    });
  });

  it('lets B run independently while A is active and does not clear a newly edited draft on acceptance', async () => {
    const harness = sourceHarness();
    const panel = await mountPanel(harness.source);
    await act(async () => panel.result.current.select('a'));

    const a = await sendQuestion(panel, 'Question A');
    act(() => panel.result.current.setDraft('Next question A'));
    await act(async () => harness.feeds[0].push({ type: 'run.started' }));

    expect(panel.result.current.entry.draft).toBe('Next question A');

    await act(async () => panel.result.current.select('b'));
    const b = await sendQuestion(panel, 'Question B');
    await act(async () =>
      harness.feeds[1].push({ type: 'run.started' }, { type: 'message.delta', messageId: 'b1', delta: 'B reply' })
    );

    expect(panel.result.current.entry.answer).toMatchObject({ content: 'B reply' });
    expect(harness.run.mock.calls.map(([id]) => id)).toEqual(['a', 'b']);

    await act(async () => {
      harness.feeds[0].push({ type: 'run.failed', message: 'A stopped' });
      harness.feeds[1].push({ type: 'run.failed', message: 'B stopped' });
      await Promise.all([a.completion, b.completion]);
    });

    expect(panel.result.current.entry.answer).toMatchObject({ status: 'error', message: 'B stopped' });
    expect(panel.result.current.entry.failure).toBeUndefined();
    expect(panel.result.current.entry.answerRecovery).toBe('send');

    await act(async () => panel.result.current.select('a'));

    expect(panel.result.current.entry.draft).toBe('Next question A');
  });

  it('never automatically resends an unknown question and permits an explicit send with the confirmed id', async () => {
    const harness = sourceHarness();
    const panel = await mountPanel(harness.source);

    const sending = await sendQuestion(panel, 'Did the server accept this?');
    await act(async () => {
      harness.feeds[0].push(new AssistantConnectionError());
      await sending.completion;
    });

    expect(panel.result.current.entry).toMatchObject({ phase: 'unknown', draft: 'Did the server accept this?' });
    expect(panel.result.current.entry.uncertainty).toContain('cannot establish');
    expect(harness.run).toHaveBeenCalledTimes(1);

    act(() => panel.result.current.setDraft('An explicit next question'));
    harness.api.getMessages.mockResolvedValue({
      messages: [{ id: 'canonical-question', role: 'user', content: 'Did the server accept this?' }],
      cursor: null
    });

    await act(async () => panel.result.current.reload());

    expect(panel.result.current.entry).toMatchObject({
      phase: 'unknown',
      draft: 'An explicit next question',
      loaded: true
    });
    expect(panel.result.current.entry.uncertainty).toContain('cannot establish');
    expect(panel.result.current.entry.messages.map(message => message.id)).toEqual(['canonical-question']);
    expect(panel.result.current.entry.failure).toBeUndefined();

    await act(async () => panel.result.current.select('b'));
    const loads = harness.api.getMessages.mock.calls.length;

    await act(async () => panel.result.current.select('created'));

    expect(harness.api.getMessages).toHaveBeenCalledTimes(loads);
    expect(harness.run).toHaveBeenCalledTimes(1);

    const next = await sendQuestion(panel);

    expect(panel.result.current.entry.phase).toBe('pending');
    expect(panel.result.current.entry.uncertainty).toContain('cannot establish');
    expect(harness.api.createConversation).toHaveBeenCalledTimes(1);
    expect(harness.run.mock.calls.map(([id, content]) => [id, content])).toEqual([
      ['created', 'Did the server accept this?'],
      ['created', 'An explicit next question']
    ]);

    await act(async () => {
      harness.feeds[1].push({ type: 'run.started' }, { type: 'run.failed', message: 'Next run stopped' });
      await next.completion;
    });

    expect(panel.result.current.entry.phase).toBe('idle');
    expect(panel.result.current.entry.uncertainty).toContain('cannot establish');
  });

  it('retains an interrupted accepted answer and allows an explicit next question without reloading', async () => {
    const harness = sourceHarness();
    const panel = await mountPanel(harness.source);

    const sending = await sendQuestion(panel, 'Accepted question');
    await act(async () => {
      harness.feeds[0].push(
        { type: 'run.started' },
        { type: 'message.delta', messageId: 'p1', delta: 'Partial' },
        null
      );
      await sending.completion;
    });

    expect(panel.result.current.entry).toMatchObject({
      phase: 'unknown',
      draft: 'Accepted question',
      submittedQuestion: 'Accepted question',
      answer: { status: 'error', kind: 'stream', content: 'Partial' }
    });
    expect(harness.run).toHaveBeenCalledTimes(1);

    const next = await sendQuestion(panel, 'Follow-up question');

    expect(harness.run.mock.calls.map(([id]) => id)).toEqual(['created', 'created']);
    expect(harness.api.getMessages).not.toHaveBeenCalled();
    expect(panel.result.current.entry.messages).toEqual([
      expect.objectContaining({ role: 'user', content: 'Accepted question' }),
      expect.objectContaining({
        id: 'p1',
        role: 'assistant',
        segments: [expect.objectContaining({ id: 'p1', content: 'Partial' })]
      })
    ]);

    await act(async () => {
      harness.feeds[1].push({ type: 'run.failed', message: 'Follow-up rejected' });
      await next.completion;
    });
  });

  it('restores an accepted failed question and retries the same id without a mandatory history reload', async () => {
    const harness = sourceHarness();
    const panel = await mountPanel(harness.source);

    const sending = await sendQuestion(panel, 'Accepted but failed');
    await act(async () => {
      harness.feeds[0].push(
        { type: 'run.started' },
        { type: 'message.delta', messageId: 'p1', delta: 'Preserved partial' },
        { type: 'tool_call.started', toolCallId: 't1', name: 'read_note' },
        { type: 'tool_call.done', toolCallId: 't1' },
        { type: 'run.failed', message: 'Execution failed' }
      );
      await sending.completion;
    });

    expect(panel.result.current.entry).toMatchObject({
      phase: 'idle',
      draft: 'Accepted but failed',
      submittedQuestion: 'Accepted but failed',
      answerRecovery: 'send',
      answer: {
        status: 'error',
        kind: 'failed',
        content: 'Preserved partial',
        tools: [{ id: 't1', name: 'read_note', status: 'done' }]
      }
    });
    expect(panel.result.current.entry.failure).toBeUndefined();
    expect(panel.result.current.entry.messages).toHaveLength(1);
    expect(harness.run).toHaveBeenCalledTimes(1);
    expect(harness.api.getMessages).not.toHaveBeenCalled();

    const retry = await sendQuestion(panel);

    expect(harness.api.createConversation).toHaveBeenCalledTimes(1);
    expect(harness.run.mock.calls.map(([id, content]) => [id, content])).toEqual([
      ['created', 'Accepted but failed'],
      ['created', 'Accepted but failed']
    ]);
    expect(panel.result.current.entry.messages).toHaveLength(2);

    await act(async () => {
      harness.feeds[1].push({ type: 'run.started' });
    });
    act(() => panel.result.current.setDraft('Keep my next draft'));

    await act(async () => {
      harness.feeds[1].push({ type: 'run.failed', message: 'Retry failed' });
      await retry.completion;
    });

    expect(panel.result.current.entry.draft).toBe('Keep my next draft');
  });

  it('replaces each message.done segment, keeps later rounds, and reloads the canonical completed answer', async () => {
    const harness = sourceHarness();
    const canonical = deferred<AssistantMessagePage>();
    harness.api.getMessages
      .mockResolvedValueOnce({ messages: [], cursor: null })
      .mockImplementationOnce(() => canonical.promise);
    const panel = await mountPanel(harness.source);
    await act(async () => panel.result.current.select('a'));

    const sending = await sendQuestion(panel, 'Multiple rounds');
    await act(async () =>
      harness.feeds[0].push(
        { type: 'run.started' },
        { type: 'message.delta', messageId: 'm1', delta: 'Draft text' },
        { type: 'message.done', messageId: 'm1', content: 'First canonical round' },
        { type: 'tool_call.started', toolCallId: 'tool', name: 'read_note' },
        { type: 'tool_call.done', toolCallId: 'tool' },
        { type: 'message.delta', messageId: 'm2', delta: 'Second' },
        { type: 'message.done', messageId: 'm2', content: 'Second canonical round' }
      )
    );

    expect(panel.result.current.entry).toMatchObject({
      phase: 'running',
      answer: { status: 'streaming', content: 'First canonical round\n\nSecond canonical round' }
    });

    await act(async () => harness.feeds[0].push({ type: 'run.completed' }));

    expect(panel.result.current.entry).toMatchObject({
      phase: 'idle',
      loading: true,
      answer: {
        status: 'done',
        message: {
          id: 'm2',
          segments: [
            { id: 'm1', content: 'First canonical round', toolCalls: [{ id: 'tool', name: 'read_note' }] },
            { id: 'm2', content: 'Second canonical round' }
          ]
        }
      }
    });

    act(() => panel.result.current.setDraft('Next question'));

    await act(async () => {
      canonical.resolve({
        messages: [
          { id: 'server-user', role: 'user', content: 'Multiple rounds' },
          {
            id: 'm2',
            role: 'assistant',
            segments: [
              { id: 'm1', content: 'First canonical round' },
              { id: 'm2', content: 'Second canonical round' }
            ]
          }
        ],
        cursor: null
      });
      await sending.completion;
    });

    expect(panel.result.current.entry).toMatchObject({ phase: 'idle', loading: false, draft: 'Next question' });
    expect(panel.result.current.entry.messages.map(message => message.id)).toEqual(['server-user', 'm2']);
    expect(panel.result.current.entry.answer).toBeUndefined();
  });

  it('starts a new send during completion history loading without losing either turn to the stale response', async () => {
    const harness = sourceHarness();
    const canonical = deferred<AssistantMessagePage>();
    harness.api.getMessages.mockImplementationOnce(() => canonical.promise);
    const panel = await mountPanel(harness.source);

    const first = await sendQuestion(panel, 'First question');
    await act(async () =>
      harness.feeds[0].push(
        { type: 'run.started' },
        { type: 'message.done', messageId: 'first-answer', content: 'First answer' },
        { type: 'run.completed' }
      )
    );

    expect(panel.result.current.entry).toMatchObject({ phase: 'idle', loading: true });

    act(() => panel.result.current.setDraft('Second question'));
    const second = await sendQuestion(panel);

    expect(panel.result.current.entry).toMatchObject({ phase: 'pending', loading: false, loadingEarlier: false });

    await act(async () =>
      harness.feeds[1].push(
        { type: 'run.started' },
        { type: 'message.delta', messageId: 'second-answer', delta: 'Second answer' }
      )
    );
    act(() => panel.result.current.setDraft('A later draft'));
    await act(async () => {
      canonical.resolve({
        messages: [{ id: 'stale-user', role: 'user', content: 'First question' }],
        cursor: 'stale-cursor'
      });
      await first.completion;
    });

    expect(panel.result.current.entry).toMatchObject({
      phase: 'running',
      loading: false,
      loadingEarlier: false,
      draft: 'A later draft',
      cursor: null,
      answer: { status: 'streaming', content: 'Second answer' }
    });
    expect(panel.result.current.entry.messages).toEqual([
      expect.objectContaining({ role: 'user', content: 'First question' }),
      expect.objectContaining({
        id: 'first-answer',
        role: 'assistant',
        segments: [expect.objectContaining({ id: 'first-answer', content: 'First answer' })]
      }),
      expect.objectContaining({ role: 'user', content: 'Second question' })
    ]);

    await act(async () => panel.result.current.send('Duplicate while running'));

    expect(harness.run).toHaveBeenCalledTimes(2);

    await act(async () => {
      harness.feeds[1].push(
        { type: 'message.delta', messageId: 'second-answer', delta: ' continues' },
        { type: 'run.failed', message: 'Second run stopped' }
      );
      await second.completion;
    });

    expect(panel.result.current.entry.answer).toMatchObject({ content: 'Second answer continues' });
    expect(panel.result.current.entry.draft).toBe('A later draft');
  });

  it('preserves a completed local answer after a history failure and permits the next send', async () => {
    const harness = sourceHarness();
    harness.api.getMessages.mockRejectedValueOnce(new Error('History unavailable'));
    const panel = await mountPanel(harness.source);

    const sending = await sendQuestion(panel, 'Complete locally');
    await act(async () => {
      harness.feeds[0].push(
        { type: 'run.started' },
        { type: 'message.done', messageId: 'm1', content: 'Complete answer' },
        { type: 'run.completed' }
      );
      await sending.completion;
    });

    expect(panel.result.current.entry).toMatchObject({
      phase: 'idle',
      loading: false,
      failure: { message: 'History unavailable', kind: 'connection', recovery: 'reload' },
      answer: { status: 'done', message: { id: 'm1' } }
    });

    act(() => panel.result.current.setDraft('Next question'));
    const next = await sendQuestion(panel);

    expect(harness.run.mock.calls.map(([id]) => id)).toEqual(['created', 'created']);
    expect(panel.result.current.entry.failure).toBeUndefined();
    expect(panel.result.current.entry.messages).toEqual([
      expect.objectContaining({ role: 'user', content: 'Complete locally' }),
      expect.objectContaining({
        id: 'm1',
        role: 'assistant',
        segments: [expect.objectContaining({ id: 'm1', content: 'Complete answer' })]
      })
    ]);

    await act(async () => {
      harness.feeds[1].push({ type: 'run.failed', message: 'Next run rejected' });
      await next.completion;
    });
  });

  it('passes the earlier cursor and joins a split answer chronologically without duplicating a bubble', async () => {
    const harness = sourceHarness();
    harness.api.getMessages
      .mockResolvedValueOnce({
        messages: [{ id: 'second', role: 'assistant', segments: [{ id: 'second', content: 'Later round' }] }],
        cursor: 'earlier-page'
      })
      .mockResolvedValueOnce({
        messages: [
          { id: 'question', role: 'user', content: 'History question' },
          { id: 'first', role: 'assistant', segments: [{ id: 'first', content: 'Earlier round' }] }
        ],
        cursor: null
      });
    const panel = await mountPanel(harness.source);
    await act(async () => panel.result.current.select('a'));

    await act(async () => panel.result.current.loadEarlier());

    expect(harness.api.getMessages.mock.calls).toEqual([['a'], ['a', 'earlier-page']]);
    expect(panel.result.current.entry.messages).toEqual([
      { id: 'question', role: 'user', content: 'History question' },
      {
        id: 'second',
        role: 'assistant',
        segments: [
          { id: 'first', content: 'Earlier round' },
          { id: 'second', content: 'Later round' }
        ]
      }
    ]);
    expect(panel.result.current.entry.cursor).toBeNull();

    await act(async () => panel.result.current.loadEarlier());

    expect(harness.api.getMessages).toHaveBeenCalledTimes(2);
  });

  it('stops a repeated history cursor without duplicating the already loaded pages', async () => {
    const harness = sourceHarness();
    harness.api.getMessages
      .mockResolvedValueOnce({
        messages: [{ id: 'latest', role: 'user', content: 'Latest question' }],
        cursor: 'before-a'
      })
      .mockResolvedValueOnce({
        messages: [{ id: 'older', role: 'user', content: 'Older question' }],
        cursor: 'before-b'
      })
      .mockResolvedValueOnce({
        messages: [{ id: 'oldest', role: 'user', content: 'Oldest question' }],
        cursor: 'before-a'
      });
    const panel = await mountPanel(harness.source);
    await act(async () => panel.result.current.select('a'));

    await act(async () => panel.result.current.loadEarlier());
    await act(async () => panel.result.current.loadEarlier());
    await act(async () => panel.result.current.loadEarlier());

    expect(harness.api.getMessages).toHaveBeenCalledTimes(3);
    expect(panel.result.current.entry.messages.map(message => message.id)).toEqual(['oldest', 'older', 'latest']);
  });

  it('ignores an old latest-page response after a newer reload has completed', async () => {
    const harness = sourceHarness();
    const old = deferred<AssistantMessagePage>();
    const latest = deferred<AssistantMessagePage>();
    harness.api.getMessages.mockImplementationOnce(() => old.promise).mockImplementationOnce(() => latest.promise);
    const panel = await mountPanel(harness.source);

    act(() => panel.result.current.select('a'));
    let reload!: Promise<void>;
    act(() => {
      reload = panel.result.current.reload();
    });
    await act(async () => {
      latest.resolve({ messages: [{ id: 'new', role: 'user', content: 'New history' }], cursor: 'new-cursor' });
      await reload;
    });

    await act(async () =>
      old.resolve({ messages: [{ id: 'old', role: 'user', content: 'Stale history' }], cursor: null })
    );

    expect(panel.result.current.entry.messages.map(message => message.id)).toEqual(['new']);
    expect(panel.result.current.entry).toMatchObject({ loading: false, cursor: 'new-cursor' });
  });

  it('invalidates an earlier-page response when latest history replaces it', async () => {
    const harness = sourceHarness();
    const earlier = deferred<AssistantMessagePage>();
    harness.api.getMessages
      .mockResolvedValueOnce({
        messages: [{ id: 'old-latest', role: 'user', content: 'Before reload' }],
        cursor: 'before'
      })
      .mockImplementationOnce(() => earlier.promise)
      .mockResolvedValueOnce({ messages: [{ id: 'new-latest', role: 'user', content: 'After reload' }], cursor: null });
    const panel = await mountPanel(harness.source);
    await act(async () => panel.result.current.select('a'));

    let pagination!: Promise<boolean>;
    act(() => {
      pagination = panel.result.current.loadEarlier();
    });
    await act(async () => panel.result.current.reload());

    await act(async () => {
      earlier.resolve({ messages: [{ id: 'oldest', role: 'user', content: 'Stale earlier page' }], cursor: null });
      await pagination;
    });

    expect(panel.result.current.entry.messages.map(message => message.id)).toEqual(['new-latest']);
    expect(panel.result.current.entry.loadingEarlier).toBe(false);
  });

  it('allows sending during initial history loading and ignores that old latest page', async () => {
    const harness = sourceHarness();
    const history = deferred<AssistantMessagePage>();
    harness.api.getMessages.mockImplementationOnce(() => history.promise);
    const panel = await mountPanel(harness.source);

    act(() => panel.result.current.select('a'));

    expect(panel.result.current.entry.loading).toBe(true);

    act(() => panel.result.current.setDraft('Question during history loading'));
    const sending = await sendQuestion(panel);

    expect(panel.result.current.entry).toMatchObject({ phase: 'pending', loading: false, loadingEarlier: false });

    await act(async () =>
      harness.feeds[0].push(
        { type: 'run.started' },
        { type: 'message.delta', messageId: 'current-answer', delta: 'Current answer' }
      )
    );
    await act(async () =>
      history.resolve({ messages: [{ id: 'stale-history', role: 'user', content: 'Old history' }], cursor: null })
    );

    expect(panel.result.current.entry.messages).toEqual([
      expect.objectContaining({ role: 'user', content: 'Question during history loading' })
    ]);
    expect(panel.result.current.entry.answer).toMatchObject({ status: 'streaming', content: 'Current answer' });

    await act(async () => {
      harness.feeds[0].push({ type: 'run.failed', message: 'Stopped' });
      await sending.completion;
    });
  });

  it('allows sending during earlier-page loading and ignores its late rejection without clearing the new answer', async () => {
    const harness = sourceHarness();
    const earlier = deferred<AssistantMessagePage>();
    harness.api.getMessages
      .mockResolvedValueOnce({
        messages: [{ id: 'loaded-question', role: 'user', content: 'Loaded history' }],
        cursor: 'earlier'
      })
      .mockImplementationOnce(() => earlier.promise);
    const panel = await mountPanel(harness.source);
    await act(async () => panel.result.current.select('a'));

    let pagination!: Promise<boolean>;
    act(() => {
      pagination = panel.result.current.loadEarlier();
    });

    expect(panel.result.current.entry.loadingEarlier).toBe(true);

    act(() => panel.result.current.setDraft('Question during pagination'));
    const sending = await sendQuestion(panel);

    expect(panel.result.current.entry).toMatchObject({ phase: 'pending', loading: false, loadingEarlier: false });

    await act(async () =>
      harness.feeds[0].push(
        { type: 'run.started' },
        { type: 'message.delta', messageId: 'current-answer', delta: 'New answer' }
      )
    );
    await act(async () => {
      earlier.reject(new Error('Stale earlier-page error'));
      await pagination;
    });

    expect(panel.result.current.entry).toMatchObject({
      phase: 'running',
      cursor: 'earlier',
      loadingEarlier: false,
      answer: { status: 'streaming', content: 'New answer' }
    });
    expect(panel.result.current.entry.failure).toBeUndefined();
    expect(panel.result.current.entry.messages).toEqual([
      { id: 'loaded-question', role: 'user', content: 'Loaded history' },
      expect.objectContaining({ role: 'user', content: 'Question during pagination' })
    ]);

    await act(async () => {
      harness.feeds[0].push({ type: 'run.failed', message: 'Stopped' });
      await sending.completion;
    });
  });

  it('preserves a failed deletion and invalidates pending history after a confirmed deletion', async () => {
    const harness = sourceHarness();
    const history = deferred<AssistantMessagePage>();
    harness.api.getMessages.mockImplementationOnce(() => history.promise);
    harness.api.deleteConversation.mockRejectedValueOnce(new Error('Conversation busy'));
    const panel = await mountPanel(harness.source);
    act(() => panel.result.current.select('a'));

    let deleted = true;
    await act(async () => {
      deleted = await panel.result.current.deleteConversation('a');
    });

    expect(deleted).toBe(false);
    expect(panel.result.current.conversations.map(item => item.id)).toContain('a');
    expect(panel.result.current.entry.failure).toEqual({ message: 'Conversation busy', kind: 'failed' });
    expect(panel.result.current.listError).toBe('Conversation busy');
    expect(panel.result.current.entry.failure?.recovery).toBeUndefined();

    await act(async () => {
      deleted = await panel.result.current.deleteConversation('a');
    });

    expect(deleted).toBe(true);
    expect(panel.result.current.conversations.map(item => item.id)).not.toContain('a');

    await act(async () =>
      history.resolve({ messages: [{ id: 'late', role: 'user', content: 'Deleted history' }], cursor: null })
    );

    expect(panel.result.current.entry.conversation).toBeUndefined();
    expect(panel.result.current.entry.messages).toEqual([]);
    expect(panel.result.current.entry.draft).toBe('');
  });

  it('blocks sending only while deletion is pending and permits it after the API rejects deletion', async () => {
    const harness = sourceHarness();
    const deletion = deferred<void>();
    harness.api.deleteConversation.mockImplementationOnce(() => deletion.promise);
    const panel = await mountPanel(harness.source);
    await act(async () => panel.result.current.select('a'));

    let removing!: Promise<boolean>;
    act(() => {
      removing = panel.result.current.deleteConversation('a');
    });

    act(() => panel.result.current.setDraft('Keep this draft'));
    await act(async () => panel.result.current.send());

    expect(harness.run).not.toHaveBeenCalled();
    expect(panel.result.current.entry).toMatchObject({ deleting: true, phase: 'idle', draft: 'Keep this draft' });

    await act(async () => {
      deletion.reject(new Error('Deletion rejected'));
      await removing;
    });

    const sending = await sendQuestion(panel);

    expect(harness.run).toHaveBeenCalledWith('a', 'Keep this draft', expect.any(AbortSignal));

    await act(async () => {
      harness.feeds[0].push({ type: 'run.failed', message: 'Stopped' });
      await sending.completion;
    });
  });

  it('blocks read-only sending but allows the API to delete an uncertain conversation', async () => {
    const harness = sourceHarness();
    const panel = await mountPanel(harness.source);
    await act(async () => panel.result.current.select('read-only'));

    await act(async () => panel.result.current.send('Forbidden'));

    expect(harness.run).not.toHaveBeenCalled();

    await act(async () => panel.result.current.select('a'));
    const sending = await sendQuestion(panel, 'Unknown acceptance');
    await act(async () => {
      harness.feeds[0].push(new AssistantConnectionError());
      await sending.completion;
    });

    expect(panel.result.current.entry.phase).toBe('unknown');

    let deleted = false;
    await act(async () => {
      deleted = await panel.result.current.deleteConversation('a');
    });

    expect(deleted).toBe(true);
    expect(harness.api.deleteConversation).toHaveBeenCalledWith('a');
    expect(panel.result.current.conversations.map(item => item.id)).not.toContain('a');
    expect(panel.result.current.entry.conversation).toBeUndefined();
  });

  it('keeps consuming an active answer when deletion is pending and the API rejects it with 409', async () => {
    const harness = sourceHarness();
    const deletion = deferred<void>();
    harness.api.deleteConversation.mockImplementationOnce(() => deletion.promise);
    const panel = await mountPanel(harness.source);
    await act(async () => panel.result.current.select('a'));

    const sending = await sendQuestion(panel, 'Keep running');
    await act(async () =>
      harness.feeds[0].push(
        { type: 'run.started' },
        { type: 'message.delta', messageId: 'answer', delta: 'Before deletion' }
      )
    );
    const signal = harness.run.mock.calls[0][2];

    let removing!: Promise<boolean>;
    act(() => {
      removing = panel.result.current.deleteConversation('a');
    });

    expect(panel.result.current.entry.deleting).toBe(true);
    expect(panel.result.current.conversations.map(item => item.id)).toContain('a');
    expect(signal.aborted).toBe(false);

    await act(async () =>
      harness.feeds[0].push({ type: 'message.delta', messageId: 'answer', delta: ' while deleting' })
    );

    expect(panel.result.current.entry.answer).toMatchObject({ content: 'Before deletion while deleting' });

    let deleted = true;
    await act(async () => {
      deletion.reject(new Error('409: Conversation busy'));
      deleted = await removing;
    });

    expect(deleted).toBe(false);
    expect(signal.aborted).toBe(false);
    expect(panel.result.current.entry).toMatchObject({
      phase: 'running',
      deleting: false,
      failure: { message: '409: Conversation busy', kind: 'failed' }
    });
    expect(panel.result.current.entry.failure?.recovery).toBeUndefined();

    await act(async () =>
      harness.feeds[0].push({ type: 'message.delta', messageId: 'answer', delta: ' after rejection' })
    );

    expect(panel.result.current.entry.answer).toMatchObject({
      status: 'streaming',
      content: 'Before deletion while deleting after rejection'
    });

    await act(async () => {
      harness.feeds[0].push({ type: 'run.failed', message: 'Run stopped' });
      await sending.completion;
    });
  });

  it('aborts an active consumer only after deletion succeeds and ignores its late callbacks', async () => {
    const harness = sourceHarness();
    const deletion = deferred<void>();
    harness.api.deleteConversation.mockImplementationOnce(() => deletion.promise);
    const panel = await mountPanel(harness.source);
    await act(async () => panel.result.current.select('a'));

    const sending = await sendQuestion(panel, 'Delete active conversation');
    await act(async () => harness.feeds[0].push({ type: 'run.started' }));
    const signal = harness.run.mock.calls[0][2];

    let removing!: Promise<boolean>;
    act(() => {
      removing = panel.result.current.deleteConversation('a');
    });

    expect(signal.aborted).toBe(false);
    expect(panel.result.current.entry.conversation?.id).toBe('a');

    let deleted = false;
    await act(async () => {
      deletion.resolve(undefined);
      deleted = await removing;
    });

    expect(deleted).toBe(true);
    expect(signal.aborted).toBe(true);
    expect(panel.result.current.conversations.map(item => item.id)).not.toContain('a');

    await act(async () => panel.result.current.select('b'));
    const next = await sendQuestion(panel, 'B question');
    const loads = harness.api.getMessages.mock.calls.length;

    await act(async () => {
      harness.feeds[0].push(
        { type: 'message.delta', messageId: 'deleted-answer', delta: 'Stale answer' },
        { type: 'run.completed' }
      );
      await sending.completion;
    });

    expect(harness.api.getMessages).toHaveBeenCalledTimes(loads);
    expect(panel.result.current.entry).toMatchObject({
      conversation: { id: 'b' },
      phase: 'pending',
      draft: 'B question',
      messages: []
    });
    expect(panel.result.current.entry.answer).toBeUndefined();

    await act(async () => {
      harness.feeds[1].push({ type: 'run.failed', message: 'B rejected' });
      await next.completion;
    });
  });

  it('aborts local consumption and ignores callbacks after disposal without affecting a new panel', async () => {
    const harness = sourceHarness();
    const panel = await mountPanel(harness.source);
    const sending = await sendQuestion(panel, 'Old panel question');
    await act(async () => harness.feeds[0].push({ type: 'run.started' }));
    const signal = harness.run.mock.calls[0][2];

    panel.unmount();

    expect(signal.aborted).toBe(true);

    const next = await mountPanel(harness.source);
    await act(async () => {
      harness.feeds[0].push({ type: 'run.completed' });
      await sending.completion;
    });

    expect(next.result.current.entry).toMatchObject({ draft: '', messages: [], phase: 'idle' });
    expect(next.result.current.entry.conversation).toBeUndefined();
    expect(harness.api.getMessages).not.toHaveBeenCalled();
    expect(harness.run).toHaveBeenCalledTimes(1);
  });

  it('ignores a confirmed create from an old source rather than sending it through either source', async () => {
    const oldHarness = sourceHarness();
    const newHarness = sourceHarness();
    const creation = deferred<AssistantConversation>();
    oldHarness.api.createConversation.mockImplementationOnce(() => creation.promise);
    const panel = renderHook(({ source }) => useAssistantPanel(source), {
      initialProps: { source: oldHarness.source }
    });
    await act(async () => {});

    const sending = await sendQuestion(panel, 'Old source draft');

    expect(panel.result.current.entry.phase).toBe('creating');

    await act(async () => panel.rerender({ source: newHarness.source }));
    await act(async () => {
      creation.resolve(conversation('obsolete-created'));
      await sending.completion;
    });

    expect(oldHarness.run).not.toHaveBeenCalled();
    expect(newHarness.run).not.toHaveBeenCalled();
    expect(panel.result.current.entry).toMatchObject({ draft: '', messages: [], phase: 'idle' });
    expect(panel.result.current.entry.conversation).toBeUndefined();
    expect(panel.result.current.conversations.map(item => item.id)).not.toContain('obsolete-created');
  });

  it('ignores StrictMode cleanup results and stale source responses', async () => {
    const oldHarness = sourceHarness();
    const newHarness = sourceHarness();
    const obsolete = deferred<AssistantConversation[]>();
    oldHarness.api.listConversations.mockImplementation(() => obsolete.promise);
    newHarness.api.listConversations.mockResolvedValue([conversation('new-source')]);
    const panel = renderHook(({ source }) => useAssistantPanel(source), {
      initialProps: { source: oldHarness.source },
      wrapper: StrictMode
    });

    await act(async () => panel.rerender({ source: newHarness.source }));
    await act(async () => obsolete.resolve([conversation('obsolete')]));

    expect(panel.result.current.conversations.map(item => item.id)).toEqual(['new-source']);
    expect(panel.result.current.listLoading).toBe(false);
    expect(panel.result.current.entry.draft).toBe('');
    expect(panel.result.current.entry.phase).toBe('idle');
  });

  it('releases the real completed fixture consumer before a new send during history synchronization', async () => {
    const source = createAssistantFixture({
      conversations: ASSISTANT_HISTORY_CONVERSATIONS,
      now: FIXTURE_NOW,
      delayMs: 0
    });
    const refresh = deferred<AssistantMessagePage>();
    const getMessages = source.api.getMessages;
    vi.spyOn(source.api, 'getMessages').mockImplementationOnce(() => refresh.promise);
    const panel = await mountPanel(source);

    const first = await sendQuestion(panel, 'First question');

    expect(panel.result.current.entry).toMatchObject({ phase: 'idle', loading: true });

    const second = await sendQuestion(panel, 'Second question');
    await act(async () => second.completion);
    const secondState = panel.result.current.entry;
    const saved = await getMessages(secondState.conversation!.id);

    await act(async () => {
      refresh.resolve({ messages: [], cursor: null });
      await first.completion;
    });

    expect(secondState.phase).toBe('idle');
    expect(secondState.uncertainty).toBeUndefined();
    expect(saved.messages.filter(message => message.role === 'user').map(message => message.content)).toEqual([
      'First question',
      'Second question'
    ]);
  });

  it('constructs a pure model with a cached initial snapshot and starts fresh sessions after disposal', async () => {
    const harness = sourceHarness();
    const model = new AssistantPanelModel(harness.source);
    const initial = model.getSnapshot();

    expect(model.getSnapshot()).toBe(initial);
    expect(initial.entry).toMatchObject({ key: 'draft:0', draft: '', phase: 'idle' });
    expect(harness.api.listConversations).not.toHaveBeenCalled();
    expect(harness.api.getMessages).not.toHaveBeenCalled();

    model.start();
    model.setDraft('Discard on restart');
    model.dispose();
    model.start();

    expect(model.getSnapshot().entry).toMatchObject({ key: 'draft:0', draft: '', phase: 'idle' });

    await Promise.resolve();
    model.dispose();
  });

  it('keeps inline answer recovery independent of a failed history reload', async () => {
    const harness = sourceHarness();
    const panel = await mountPanel(harness.source);
    const sending = await sendQuestion(panel, 'Accepted but failed');
    await act(async () => {
      harness.feeds[0].push(
        { type: 'run.started' },
        { type: 'message.delta', messageId: 'partial', delta: 'Retained partial' },
        { type: 'run.failed', message: 'Execution failed' }
      );
      await sending.completion;
    });

    harness.api.getMessages.mockRejectedValueOnce(new Error('History unavailable'));

    await act(async () => panel.result.current.reload());

    expect(panel.result.current.entry).toMatchObject({
      phase: 'idle',
      failure: { message: 'History unavailable', kind: 'connection', recovery: 'reload' },
      answerRecovery: 'send',
      answer: { status: 'error', kind: 'failed', message: 'Execution failed', content: 'Retained partial' }
    });

    expect(await panel.result.current.loadEarlier()).toBe(false);
  });

  it('renders a fresh initial snapshot synchronously when the source identity changes', async () => {
    const oldHarness = sourceHarness();
    const newHarness = sourceHarness();
    const renders: { source: AssistantDataSource; panel: AssistantPanel }[] = [];
    const panel = renderHook(
      ({ source }) => {
        const value = useAssistantPanel(source);
        renders.push({ source, panel: value });
        return value;
      },
      { initialProps: { source: oldHarness.source } }
    );
    await act(async () => {});
    act(() => panel.result.current.setDraft('Old source only'));

    await act(async () => panel.rerender({ source: newHarness.source }));

    const firstNewRender = renders.find(render => render.source === newHarness.source)!;
    expect(firstNewRender.panel.entry).toMatchObject({ key: 'draft:0', draft: '', messages: [], phase: 'idle' });
    expect(firstNewRender.panel.conversations).toEqual([]);
    expect(firstNewRender.panel.listLoading).toBe(true);
  });

  it.each([
    { page: 'latest', outcome: 'success' },
    { page: 'latest', outcome: 'failure' },
    { page: 'earlier', outcome: 'success' },
    { page: 'earlier', outcome: 'failure' }
  ] as const)(
    'ignores a $page $outcome result invalidated by a subscriber starting a new send',
    async ({ page, outcome }) => {
      const harness = sourceHarness();
      harness.api.getMessages.mockResolvedValueOnce({ messages: [], cursor: 'older' });
      const model = new AssistantPanelModel(harness.source);
      model.start();
      await Promise.resolve();
      model.select('a');
      await Promise.resolve();
      await Promise.resolve();

      const history = deferred<AssistantMessagePage>();
      harness.api.getMessages.mockImplementationOnce(() => history.promise);
      let sawLoading = false;
      let nextSend: Promise<void> | undefined;
      const unsubscribe = model.subscribe(() => {
        const entry = model.getSnapshot().entry;
        if (entry.loading || entry.loadingEarlier) {
          sawLoading = true;
          return;
        }
        if (!sawLoading) return;
        // Clear before send(): its synchronous notification must not submit a second time.
        sawLoading = false;
        nextSend = model.send('New submitted question');
      });

      const previous = page === 'latest' ? model.reload() : model.loadEarlier();
      if (outcome === 'failure') history.reject(new Error('Obsolete history failure'));
      else history.resolve({ messages: [], cursor: null });
      await previous;

      expect(nextSend).toBeDefined();
      expect(model.getSnapshot().entry).toMatchObject({
        phase: 'pending',
        submittedQuestion: 'New submitted question',
        draft: 'New submitted question'
      });
      expect(model.getSnapshot().entry.failure).toBeUndefined();

      unsubscribe();
      harness.feeds[0].push({ type: 'run.failed', message: 'Stop new run' });
      await nextSend;
      model.dispose();
    }
  );

  it('restores initial history after a rejected send without losing its error or draft', async () => {
    const harness = sourceHarness();
    const initial = deferred<AssistantMessagePage>();
    const saved: AssistantMessagePage = {
      messages: [{ id: 'saved-question', role: 'user', content: 'Existing question' }],
      cursor: 'earlier'
    };
    harness.api.getMessages.mockImplementationOnce(() => initial.promise).mockResolvedValueOnce(saved);
    const panel = await mountPanel(harness.source);
    act(() => panel.result.current.select('a'));
    const sending = await sendQuestion(panel, 'Retry this question');

    await act(async () => {
      harness.feeds[0].push({ type: 'run.failed', message: 'Message rejected' });
      await sending.completion;
      initial.resolve({ messages: [], cursor: null });
    });

    expect(panel.result.current.entry).toMatchObject({
      loaded: true,
      loading: false,
      messages: saved.messages,
      cursor: 'earlier',
      draft: 'Retry this question',
      failure: { message: 'Message rejected', recovery: 'send' }
    });
  });

  it('offers history recovery when restoring initial history after rejection fails', async () => {
    const harness = sourceHarness();
    const initial = deferred<AssistantMessagePage>();
    harness.api.getMessages
      .mockImplementationOnce(() => initial.promise)
      .mockRejectedValueOnce(new Error('History unavailable'));
    const panel = await mountPanel(harness.source);
    act(() => panel.result.current.select('a'));
    const sending = await sendQuestion(panel, 'Keep this question');

    await act(async () => {
      harness.feeds[0].push({ type: 'run.failed', message: 'Message rejected' });
      await sending.completion;
      initial.resolve({ messages: [], cursor: null });
    });

    expect(panel.result.current.entry).toMatchObject({
      draft: 'Keep this question',
      failure: { message: 'History unavailable', recovery: 'reload' }
    });
    harness.api.getMessages.mockResolvedValueOnce({
      messages: [{ id: 'saved', role: 'user', content: 'Recovered history' }],
      cursor: null
    });
    await act(async () => panel.result.current.reload());
    expect(panel.result.current.entry.messages).toEqual([{ id: 'saved', role: 'user', content: 'Recovered history' }]);
    expect(panel.result.current.entry.draft).toBe('Keep this question');
  });

});
