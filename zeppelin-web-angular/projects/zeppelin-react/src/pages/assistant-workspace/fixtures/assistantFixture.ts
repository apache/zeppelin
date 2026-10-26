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

import {
  AssistantConnectionError,
  type AssistantAnswerMessage,
  type AssistantConversation,
  type AssistantMessage,
  type AssistantRunEvent
} from '@/entities/assistant/model/assistantContract';
import { AssistantHttpError } from '@/entities/assistant/model/assistantTransport';
import type { AssistantDataSource } from '../model/assistantDataSource';
import type { AssistantFixtureConversation } from './assistantFixtureData';

export type AssistantFixtureScenario = 'normal' | 'create-failure' | 'send-rejection' | 'unknown' | 'run-failure';

export interface AssistantFixtureOptions {
  conversations: readonly AssistantFixtureConversation[];
  /** Reference time in milliseconds since the Unix epoch. */
  now: number;
  /** Non-normal scenarios fail the first matching operation only. */
  scenario?: AssistantFixtureScenario;
  delayMs?: number;
}

interface FixtureConversation {
  conversation: AssistantConversation;
  latest: AssistantMessage[];
  earlier?: AssistantMessage[];
  busy: boolean;
}

const copyMessages = (messages: readonly AssistantMessage[]): AssistantMessage[] =>
  messages.map(message =>
    message.role === 'user'
      ? { ...message }
      : {
          ...message,
          segments: message.segments.map(segment => ({
            ...segment,
            ...(segment.toolCalls && { toolCalls: segment.toolCalls.map(call => ({ ...call })) })
          }))
        }
  );

const pause = (delayMs: number, signal?: AbortSignal): Promise<void> => {
  if (signal?.aborted) return Promise.reject(new DOMException('The operation was aborted.', 'AbortError'));
  if (delayMs === 0) return Promise.resolve();

  const { promise, resolve, reject } = Promise.withResolvers<void>();
  const onAbort = () => {
    clearTimeout(timer);
    signal?.removeEventListener('abort', onAbort);
    reject(new DOMException('The operation was aborted.', 'AbortError'));
  };
  const timer = setTimeout(() => {
    signal?.removeEventListener('abort', onAbort);
    resolve();
  }, delayMs);

  signal?.addEventListener('abort', onAbort, { once: true });
  return promise;
};

/** Creates an isolated in-memory conversation source from the supplied seed data. */
export const createAssistantFixture = (options: AssistantFixtureOptions): AssistantDataSource => {
  const scenario = options.scenario ?? 'normal';
  const delayMs = Math.max(0, options.delayMs ?? 120);
  const now = options.now;

  let createFailureUsed = false;
  let runFailureUsed = false;
  let nextConversation = 1;
  let nextRun = 1;
  let activity = 0;

  const conversations = new Map<string, FixtureConversation>();
  for (const value of options.conversations) {
    conversations.set(value.conversation.id, {
      conversation: { ...value.conversation },
      latest: copyMessages(value.latest),
      ...(value.earlier && { earlier: copyMessages(value.earlier) }),
      busy: false
    });
  }

  const find = (id: string): FixtureConversation => {
    const value = conversations.get(id);
    if (!value) throw new AssistantHttpError(404);

    return value;
  };

  const writable = (id: string): FixtureConversation => {
    const value = find(id);
    if (!value.conversation.canSendMessage) throw new AssistantHttpError(403);
    if (value.busy) throw new AssistantHttpError(409);

    return value;
  };

  return {
    api: {
      async listConversations() {
        await pause(delayMs);
        return Array.from(conversations.values(), value => ({ ...value.conversation })).sort((a, b) =>
          b.updatedAt.localeCompare(a.updatedAt)
        );
      },
      async createConversation(body) {
        await pause(delayMs);

        if (scenario === 'create-failure' && !createFailureUsed) {
          createFailureUsed = true;
          throw new AssistantHttpError(500, 'The fixture could not create the conversation. Try again.');
        }

        while (conversations.has(`c${nextConversation}`)) nextConversation++;
        const id = `c${nextConversation++}`;
        const value: FixtureConversation = {
          conversation: {
            id,
            title: body.title ?? 'New conversation',
            ownerId: 'demo-user',
            canSendMessage: true,
            updatedAt: new Date(now + ++activity * 1_000).toISOString()
          },
          latest: [],
          busy: false
        };

        conversations.set(id, value);
        return { ...value.conversation };
      },
      async deleteConversation(id) {
        writable(id);
        await pause(delayMs);
        writable(id);
        conversations.delete(id);
      },
      async getMessages(id, before) {
        find(id);
        await pause(delayMs);

        const value = find(id);
        if (before !== undefined) {
          if (!value.earlier || before !== `${id}:earlier`)
            throw new AssistantHttpError(400, 'Unknown history cursor.');
          return { messages: copyMessages(value.earlier), cursor: null };
        }

        return { messages: copyMessages(value.latest), cursor: value.earlier ? `${id}:earlier` : null };
      }
    },
    async *run(id, content, signal): AsyncIterable<AssistantRunEvent> {
      if (signal.aborted) throw new DOMException('The operation was aborted.', 'AbortError');

      const value = writable(id);
      value.busy = true;

      try {
        await pause(delayMs, signal);
        const failThisRun = !runFailureUsed && ['send-rejection', 'unknown', 'run-failure'].includes(scenario);
        if (failThisRun) runFailureUsed = true;

        if (failThisRun && scenario === 'send-rejection') {
          yield { type: 'run.failed', message: 'The fixture could not accept this message. Try again.' };
          return;
        }

        const runId = `${id}-run-${nextRun++}`;
        value.latest.push({ id: `${runId}-user`, role: 'user', content });
        value.conversation.updatedAt = new Date(now + ++activity * 1_000).toISOString();
        if (failThisRun && scenario === 'unknown') throw new AssistantConnectionError();
        yield { type: 'run.started', runId };

        const answer: AssistantAnswerMessage = { id: `${runId}-answer-1`, role: 'assistant', segments: [] };
        const first = { id: answer.id, content: '' };
        const toolCallId = `${runId}-tool-1`;
        const events: AssistantRunEvent[] = [{ type: 'message.delta', messageId: first.id, delta: 'I checked ' }];
        if (failThisRun && scenario === 'run-failure') {
          events.push({ type: 'run.failed', runId, message: 'The fixture stopped while reading the revenue data.' });
        } else {
          const secondId = `${runId}-answer-2`;
          events.push(
            { type: 'message.delta', messageId: first.id, delta: 'the revenue paragraph.' },
            { type: 'message.done', messageId: first.id, content: 'I checked the revenue paragraph.' },
            { type: 'tool_call.started', toolCallId, name: 'read_notebook' },
            { type: 'tool_call.done', toolCallId },
            { type: 'message.delta', messageId: secondId, delta: 'Revenue grew **12%**' },
            {
              type: 'message.done',
              messageId: secondId,
              content:
                'Revenue grew **12%** compared with last month.\n\n| Region | Growth |\n| --- | ---: |\n| Europe | 18% |\n| Americas | 10% |\n| Asia Pacific | 8% |'
            },
            { type: 'run.completed', runId }
          );
        }

        for (const event of events) {
          await pause(delayMs, signal);
          if (event.type === 'message.delta' || event.type === 'message.done') {
            let segment = answer.segments.find(item => item.id === event.messageId);
            if (!segment) {
              segment = { id: event.messageId, content: '' };
              answer.segments.push(segment);
            }
            segment.content = event.type === 'message.delta' ? segment.content + event.delta : event.content;
            answer.id = event.messageId;
          } else if (event.type === 'tool_call.started') {
            answer.segments[0].toolCalls = [{ id: event.toolCallId, name: event.name }];
          }

          if (event.type === 'tool_call.done' || event.type === 'run.completed') {
            const persisted = value.latest.findIndex(
              message => message.role === 'assistant' && message.segments.some(segment => segment.id === first.id)
            );
            const snapshot = copyMessages([answer])[0];
            if (persisted < 0) value.latest.push(snapshot);
            else value.latest[persisted] = snapshot;
          }

          yield event;
        }
      } finally {
        value.busy = false;
      }
    }
  };
};
