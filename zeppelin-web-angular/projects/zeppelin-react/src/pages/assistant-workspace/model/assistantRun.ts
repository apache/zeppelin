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
  type AssistantRunEvent,
  type AssistantToolCallRef
} from '@/entities/assistant/model/assistantContract';
import { answerText } from '@/entities/assistant/model/messageHistory';
import type { AssistantAnswerView, AssistantToolActivity } from '@/entities/assistant/ui/answerView';

export interface AssistantRunSnapshot {
  accepted: boolean;
  outcome?: 'completed' | 'failed' | 'unknown';
  view?: AssistantAnswerView;
  message?: AssistantAnswerMessage;
  rejection?: string;
}

/** The accumulated answer and outcome of one submitted question. */
export class AssistantRun {
  private current: AssistantRunSnapshot = { accepted: false };
  private readonly answer: AssistantAnswerMessage;
  private readonly tools: AssistantToolActivity[] = [];
  private readonly pendingTools: AssistantToolCallRef[] = [];

  constructor(answerId: string) {
    this.answer = { id: answerId, role: 'assistant', segments: [] };
  }

  get snapshot(): AssistantRunSnapshot {
    return this.current;
  }

  receive(event: AssistantRunEvent): AssistantRunSnapshot {
    if (this.current.outcome) return this.current;

    switch (event.type) {
      case 'run.started':
        if (!this.current.accepted) this.current = { accepted: true, view: { status: 'loading' } };
        return this.current;

      case 'run.completed':
        this.current = {
          accepted: true,
          outcome: 'completed',
          view: { status: 'done', message: this.answer },
          message: this.answer
        };
        return this.current;

      case 'run.failed':
        this.current = this.current.accepted
          ? {
              accepted: true,
              outcome: 'failed',
              view: {
                status: 'error',
                kind: 'failed',
                message: event.message,
                content: answerText(this.answer),
                tools: this.toolSnapshot()
              },
              message: this.answer
            }
          : { accepted: false, outcome: 'failed', rejection: event.message };
        return this.current;
    }

    if (!this.current.accepted) return this.current;

    switch (event.type) {
      case 'message.delta':
      case 'message.done': {
        let segment = this.answer.segments.find(item => item.id === event.messageId);
        if (!segment) {
          segment = { id: event.messageId, content: '', toolCalls: this.pendingTools.splice(0) };
          this.answer.segments.push(segment);
          this.answer.id = segment.id;
        }
        // message.done ends a round, not the whole answer.
        segment.content = event.type === 'message.done' ? event.content : segment.content + event.delta;
        break;
      }

      case 'tool_call.started': {
        const tool = this.tools.find(item => item.id === event.toolCallId);
        if (tool) Object.assign(tool, { name: event.name, status: 'running' });
        else this.tools.push({ id: event.toolCallId, name: event.name, status: 'running' });

        const calls = this.answer.segments.at(-1)?.toolCalls ?? this.pendingTools;
        if (!calls.some(call => call.id === event.toolCallId)) calls.push({ id: event.toolCallId, name: event.name });
        break;
      }

      case 'tool_call.done': {
        const tool = this.tools.find(item => item.id === event.toolCallId);
        if (tool) tool.status = 'done';
        else this.tools.push({ id: event.toolCallId, status: 'done' });
        break;
      }
    }

    this.current = {
      accepted: true,
      view: { status: 'streaming', content: answerText(this.answer), tools: this.toolSnapshot() }
    };
    return this.current;
  }

  interrupt(error: unknown): AssistantRunSnapshot {
    if (this.current.outcome) return this.current;

    this.current = {
      accepted: this.current.accepted,
      outcome: 'unknown',
      view: {
        status: 'error',
        kind: error instanceof AssistantConnectionError ? 'connection' : 'stream',
        message: error instanceof Error ? error.message : 'The outcome of this question is unknown.',
        content: answerText(this.answer),
        tools: this.toolSnapshot()
      },
      ...(this.current.accepted ? { message: this.answer } : {})
    };
    return this.current;
  }

  private toolSnapshot(): AssistantToolActivity[] {
    return this.tools.map(tool => ({ ...tool }));
  }
}
