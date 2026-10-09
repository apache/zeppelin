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
  ownerId?: string;
  /** Whether the requesting user can send messages to the conversation, as the server decides. */
  canSendMessage?: boolean;
  /** Last activity (ISO time), shown in the conversation list. */
  updatedAt?: string;
}
export interface AssistantMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  /** From history: the tools the server called before this answer. Live runs report them as events instead. */
  toolCalls?: AssistantToolCallRef[];
}
export interface AssistantToolCallRef {
  id: string;
  name: string;
}
/** One page of a conversation's history, oldest first. */
export interface AssistantMessagePage {
  messages: AssistantMessage[];
  /** Pass as `before` to load the page before this one; null when this page starts the conversation. */
  earlierCursor: string | null;
}
export interface AssistantRunBody {
  prompt: string;
}
export type AssistantRunEvent =
  | { type: 'run.started'; runId?: string }
  | { type: 'run.completed'; runId?: string }
  | { type: 'run.failed'; runId?: string; message: string }
  | { type: 'message.delta'; messageId: string; delta: string }
  | { type: 'message.done'; messageId: string; content: string }
  | { type: 'tool_call.started'; toolCallId: string; name: string }
  // The server does not repeat the tool name on completion.
  | { type: 'tool_call.done'; toolCallId: string; name?: string };

export type AssistantRunState = 'idle' | 'running' | 'disconnected';

export interface AssistantTransport {
  /** Conversations of the note the transport was created for. */
  listConversations(): Promise<AssistantConversation[]>;
  createConversation(body: { title?: string }): Promise<AssistantConversation>;
  deleteConversation(conversationId: string): Promise<void>;
  /** The latest page of history, or the page before `before` (an `earlierCursor`). */
  getMessages(conversationId: string, before?: string): Promise<AssistantMessagePage>;
  /** Observe local run state, including a lost connection until history is reloaded. */
  subscribeRunState?(conversationId: string, listener: (state: AssistantRunState) => void): () => void;
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
