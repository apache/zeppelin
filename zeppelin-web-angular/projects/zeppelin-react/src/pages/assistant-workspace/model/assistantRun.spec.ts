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

import { describe, expect, it } from 'vitest';

import {
  AssistantConnectionError,
  AssistantStreamError,
  type AssistantRunEvent
} from '@/entities/assistant/model/assistantContract';
import { answerText, answerToolCalls } from '@/entities/assistant/model/messageHistory';
import { AssistantRun } from './assistantRun';

describe('AssistantRun', () => {
  it('ignores text and tools before acceptance without publishing an answer', () => {
    const run = new AssistantRun('local-answer');
    const initial = run.snapshot;
    const events: AssistantRunEvent[] = [
      { type: 'message.delta', messageId: 'ignored', delta: 'Not this run' },
      { type: 'message.done', messageId: 'ignored', content: 'Still not this run' },
      { type: 'tool_call.started', toolCallId: 'ignored-tool', name: 'read' },
      { type: 'tool_call.done', toolCallId: 'ignored-tool' }
    ];

    expect(initial).toEqual({ accepted: false });

    for (const event of events) {
      const ignored = run.receive(event);

      expect(ignored).toBe(initial);
    }

    const started = run.receive({ type: 'run.started' });

    expect(started).toEqual({ accepted: true, view: { status: 'loading' } });

    const completed = run.receive({ type: 'run.completed' });

    expect(completed.message).toEqual({ id: 'local-answer', role: 'assistant', segments: [] });
    expect(initial).toEqual({ accepted: false });
  });

  it('appends deltas and replaces only the matching round while keeping all rounds in order', () => {
    const run = new AssistantRun('local-answer');

    const loading = run.receive({ type: 'run.started' });
    const partial = run.receive({ type: 'message.delta', messageId: 'first', delta: 'Draft' });
    const appended = run.receive({ type: 'message.delta', messageId: 'first', delta: ' text' });

    expect(appended.view).toEqual({
      status: 'streaming',
      content: 'Draft text',
      tools: []
    });

    run.receive({ type: 'message.done', messageId: 'first', content: 'First answer' });
    run.receive({ type: 'message.done', messageId: 'second', content: 'Second answer' });
    const revised = run.receive({ type: 'message.done', messageId: 'first', content: 'Revised first answer' });

    expect(revised.view).toEqual({
      status: 'streaming',
      content: 'Revised first answer\n\nSecond answer',
      tools: []
    });
    expect(revised.outcome).toBeUndefined();
    expect(revised.message).toBeUndefined();

    const duplicateStart = run.receive({ type: 'run.started' });

    expect(duplicateStart).toBe(revised);

    const completed = run.receive({ type: 'run.completed' });

    expect(completed).toMatchObject({ accepted: true, outcome: 'completed', view: { status: 'done' } });
    expect(completed.message).toMatchObject({
      id: 'second',
      role: 'assistant',
      segments: [
        { id: 'first', content: 'Revised first answer' },
        { id: 'second', content: 'Second answer' }
      ]
    });
    expect(completed.view).toEqual({ status: 'done', message: completed.message });
    expect(loading).toEqual({ accepted: true, view: { status: 'loading' } });
    expect(partial.view).toEqual({ status: 'streaming', content: 'Draft', tools: [] });
  });

  it('associates tools before text with the first round and retains a represented tool-only round', () => {
    const run = new AssistantRun('local-answer');

    run.receive({ type: 'run.started' });
    run.receive({ type: 'tool_call.started', toolCallId: 'before-text', name: 'inspect' });
    run.receive({ type: 'tool_call.done', toolCallId: 'before-text' });
    run.receive({ type: 'message.done', messageId: 'tool-only', content: '' });
    run.receive({ type: 'tool_call.started', toolCallId: 'second-tool', name: 'read' });
    run.receive({ type: 'tool_call.started', toolCallId: 'second-tool', name: 'read' });
    run.receive({ type: 'tool_call.done', toolCallId: 'second-tool' });
    run.receive({ type: 'message.done', messageId: 'answer', content: 'The result' });
    run.receive({ type: 'tool_call.started', toolCallId: 'last-tool', name: 'summarize' });
    const completed = run.receive({ type: 'run.completed' });

    expect(completed.message).toEqual({
      id: 'answer',
      role: 'assistant',
      segments: [
        {
          id: 'tool-only',
          content: '',
          toolCalls: [
            { id: 'before-text', name: 'inspect' },
            { id: 'second-tool', name: 'read' }
          ]
        },
        { id: 'answer', content: 'The result', toolCalls: [{ id: 'last-tool', name: 'summarize' }] }
      ]
    });
    expect(answerText(completed.message!)).toBe('The result');
    expect(answerToolCalls(completed.message!)).toEqual([
      { id: 'before-text', name: 'inspect' },
      { id: 'second-tool', name: 'read' },
      { id: 'last-tool', name: 'summarize' }
    ]);
  });

  it('copies live tool activity so later events never change a published view', () => {
    const run = new AssistantRun('local-answer');
    run.receive({ type: 'run.started' });

    const unnamed = run.receive({ type: 'tool_call.done', toolCallId: 'tool' });
    const running = run.receive({ type: 'tool_call.started', toolCallId: 'tool', name: 'read' });
    const done = run.receive({ type: 'tool_call.done', toolCallId: 'tool' });

    expect(unnamed.view).toEqual({
      status: 'streaming',
      content: '',
      tools: [{ id: 'tool', status: 'done' }]
    });
    expect(running.view).toEqual({
      status: 'streaming',
      content: '',
      tools: [{ id: 'tool', name: 'read', status: 'running' }]
    });
    expect(done.view).toEqual({
      status: 'streaming',
      content: '',
      tools: [{ id: 'tool', name: 'read', status: 'done' }]
    });
    expect(run.snapshot).toBe(done);
    expect(done.message).toBeUndefined();
  });

  it('accepts completion without a start event and uses the constructor id for an empty answer', () => {
    const run = new AssistantRun('fallback-answer');

    const completed = run.receive({ type: 'run.completed' });

    expect(completed).toEqual({
      accepted: true,
      outcome: 'completed',
      view: { status: 'done', message: { id: 'fallback-answer', role: 'assistant', segments: [] } },
      message: { id: 'fallback-answer', role: 'assistant', segments: [] }
    });
  });

  it('does not invent a segment for tools when no round was represented', () => {
    const run = new AssistantRun('fallback-answer');
    run.receive({ type: 'run.started' });

    run.receive({ type: 'tool_call.started', toolCallId: 'tool', name: 'read' });
    const completed = run.receive({ type: 'run.completed' });

    expect(completed.message).toEqual({ id: 'fallback-answer', role: 'assistant', segments: [] });
  });

  it('reports an unaccepted failure as a rejection without an answer', () => {
    const run = new AssistantRun('local-answer');

    const rejected = run.receive({ type: 'run.failed', message: 'Permission denied' });

    expect(rejected).toEqual({
      accepted: false,
      outcome: 'failed',
      rejection: 'Permission denied'
    });
  });

  it('retains accepted partial content and tools on a reported failure', () => {
    const run = new AssistantRun('local-answer');
    run.receive({ type: 'run.started' });
    run.receive({ type: 'message.delta', messageId: 'partial', delta: 'Partial answer' });
    run.receive({ type: 'tool_call.started', toolCallId: 'tool', name: 'read' });

    const failed = run.receive({ type: 'run.failed', message: 'The run failed' });

    expect(failed).toEqual({
      accepted: true,
      outcome: 'failed',
      view: {
        status: 'error',
        kind: 'failed',
        message: 'The run failed',
        content: 'Partial answer',
        tools: [{ id: 'tool', name: 'read', status: 'running' }]
      },
      message: {
        id: 'partial',
        role: 'assistant',
        segments: [{ id: 'partial', content: 'Partial answer', toolCalls: [{ id: 'tool', name: 'read' }] }]
      }
    });
    expect(failed.rejection).toBeUndefined();
  });

  it.each([
    { error: new AssistantConnectionError(), kind: 'connection' },
    { error: new AssistantStreamError(), kind: 'stream' },
    { error: new Error('Interrupted by the source'), kind: 'stream' }
  ])('keeps accepted partial output on a $kind interruption', ({ error, kind }) => {
    const run = new AssistantRun('local-answer');
    run.receive({ type: 'run.started' });
    run.receive({ type: 'message.done', messageId: 'partial', content: 'Saved portion' });
    run.receive({ type: 'tool_call.started', toolCallId: 'tool', name: 'inspect' });

    const interrupted = run.interrupt(error);

    expect(interrupted).toMatchObject({
      accepted: true,
      outcome: 'unknown',
      view: {
        status: 'error',
        kind,
        message: error.message,
        content: 'Saved portion',
        tools: [{ id: 'tool', name: 'inspect', status: 'running' }]
      },
      message: {
        id: 'partial',
        role: 'assistant',
        segments: [{ id: 'partial', content: 'Saved portion', toolCalls: [{ id: 'tool', name: 'inspect' }] }]
      }
    });
  });

  it('shows an unknown outcome before acceptance without inventing a raw answer', () => {
    const run = new AssistantRun('local-answer');
    const error = new AssistantConnectionError();

    const interrupted = run.interrupt(error);

    expect(interrupted).toEqual({
      accepted: false,
      outcome: 'unknown',
      view: { status: 'error', kind: 'connection', message: error.message, content: '', tools: [] }
    });
  });

  it('uses the existing unknown-outcome fallback only for non-Error interruptions', () => {
    const run = new AssistantRun('local-answer');

    const interrupted = run.interrupt('not an Error');

    expect(interrupted.view).toEqual({
      status: 'error',
      kind: 'stream',
      message: 'The outcome of this question is unknown.',
      content: '',
      tools: []
    });

    const emptyError = new AssistantRun('another-answer');

    const interruptedEmptyError = emptyError.interrupt(new Error(''));

    expect(interruptedEmptyError.view).toMatchObject({ message: '' });
  });

  it.each(['completed', 'failed', 'unknown'] as const)('ignores all events and interruptions after %s', outcome => {
    const run = new AssistantRun('local-answer');
    run.receive({ type: 'run.started' });
    run.receive({ type: 'message.done', messageId: 'answer', content: 'Original' });
    run.receive({ type: 'tool_call.started', toolCallId: 'tool', name: 'read' });
    const terminal =
      outcome === 'unknown'
        ? run.interrupt(new AssistantStreamError())
        : run.receive(outcome === 'completed' ? { type: 'run.completed' } : { type: 'run.failed', message: 'Failed' });
    const expected = JSON.stringify(terminal);
    const lateEvents: AssistantRunEvent[] = [
      { type: 'run.started' },
      { type: 'message.delta', messageId: 'answer', delta: ' changed' },
      { type: 'message.done', messageId: 'answer', content: 'Replaced' },
      { type: 'message.done', messageId: 'later', content: 'Another round' },
      { type: 'tool_call.started', toolCallId: 'tool', name: 'renamed' },
      { type: 'tool_call.done', toolCallId: 'tool' },
      { type: 'run.completed' },
      { type: 'run.failed', message: 'Late failure' }
    ];

    for (const event of lateEvents) {
      const ignored = run.receive(event);

      expect(ignored).toBe(terminal);
    }

    const ignoredInterruption = run.interrupt(new AssistantConnectionError());

    expect(ignoredInterruption).toBe(terminal);
    expect(run.snapshot).toBe(terminal);
    expect(JSON.stringify(terminal)).toBe(expected);
  });
});
