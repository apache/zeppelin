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

export interface AssistantConversation {
  id: string;
  title?: string;
  /** Account that created the conversation, shown as "Started by …". */
  ownerId: string;
  /** Whether the requesting user can send messages to the conversation, as the server decides. */
  canSendMessage: boolean;
  /** Last activity (ISO time), shown in the conversation list. */
  updatedAt: string;
}
export type AssistantMessage = AssistantUserMessage | AssistantAnswerMessage;
export interface AssistantUserMessage {
  id: string;
  role: 'user';
  content: string;
}
export interface AssistantAnswerMessage {
  /** The last assistant round's id, used as the bubble key. */
  id: string;
  role: 'assistant';
  /** Ordered assistant rounds as stored by the server, including tool-only rounds. */
  segments: AssistantMessageSegment[];
}
export interface AssistantToolCallRef {
  id: string;
  name: string;
}
export interface AssistantMessageSegment {
  id: string;
  content: string;
  toolCalls?: AssistantToolCallRef[];
}
/** The transport reverses the server's newest-first page so messages are oldest first. */
export interface AssistantMessagePage {
  messages: AssistantMessage[];
  /** Pass as `before` to load older messages; null at the start of the conversation. */
  cursor: string | null;
}
export interface AssistantRunBody {
  content: string;
}
export type AssistantRunEvent =
  | { type: 'run.started'; runId?: string }
  | { type: 'run.completed'; runId?: string }
  | { type: 'run.failed'; runId?: string; message: string }
  | { type: 'message.delta'; messageId: string; delta: string }
  | { type: 'message.done'; messageId: string; content: string }
  | { type: 'tool_call.started'; toolCallId: string; name: string }
  | { type: 'tool_call.done'; toolCallId: string };

export interface AssistantTransport {
  /** Conversations of the note the transport was created for. */
  listConversations(signal?: AbortSignal): Promise<AssistantConversation[]>;
  createConversation(body: { title?: string }, signal?: AbortSignal): Promise<AssistantConversation>;
  deleteConversation(conversationId: string, signal?: AbortSignal): Promise<void>;
  /** The latest page of history, or the page before `before` (a page cursor). */
  getMessages(conversationId: string, before?: string, signal?: AbortSignal): Promise<AssistantMessagePage>;
  /** Aborting stops local event consumption; it does not cancel a run already accepted by the server. */
  openRun(conversationId: string, body: AssistantRunBody, signal: AbortSignal): AsyncIterable<AssistantRunEvent>;
}

export class AssistantStreamError extends Error {
  constructor() {
    super('The assistant stopped responding before the reply finished. Retry the request.');
    this.name = 'AssistantStreamError';
  }
}

/** The notebook WebSocket closed during a run; its events went with it, so only a reload shows where it got to. */
export class AssistantConnectionError extends Error {
  constructor() {
    super('The connection to the server was lost. Retry to reload the conversation and see where the answer got to.');
    this.name = 'AssistantConnectionError';
  }
}
