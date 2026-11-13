/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { Page, Route, WebSocketRoute } from '@playwright/test';

const ZEPPELIN_WS_URL_PATTERN = /\/ws(\?|$)/;
const CREATED_AT = '2026-10-19T12:00:00Z';
const STREAM_PREFIX = 'I checked the notebook results.';

interface ConversationMetadata {
  id: string;
  noteId: string;
  ownerId: string;
  title: string;
  createdAt: string;
  updatedAt: string;
  canSendMessage: boolean;
}

// Message.User / Message.Assistant / Message.Tool, not the panel's segments contract.
interface RawMessage {
  id: string;
  createdAt: string;
  role: 'user' | 'assistant' | 'tool';
  content: string;
  toolCalls?: Array<{ id: string; name: string; arguments: Record<string, unknown> }>;
  toolCallId?: string;
}

interface SendMessage {
  noteId: string;
  conversationId: string;
  content: string;
}

interface SocketEnvelope {
  op?: string;
  data?: Record<string, unknown>;
}

const parse = (frame: string | Buffer): SocketEnvelope | undefined => {
  try {
    return JSON.parse(frame.toString()) as SocketEnvelope;
  } catch {
    return undefined;
  }
};

const message = (id: string, role: RawMessage['role'], content: string): RawMessage => ({
  id,
  role,
  content,
  createdAt: CREATED_AT,
  ...(role === 'assistant' ? { toolCalls: [] } : {})
});

const revenueHistory = (): RawMessage[] => {
  // Exactly 50 raw messages follow the first assistant round, as requested by the
  // production transport. Its tool results straddle the boundary without adding bubbles.
  const toolCalls = Array.from({ length: 47 }, (_, index) => ({
    id: `c1-tool-${index + 1}`,
    name: 'read_notebook',
    arguments: { paragraphId: `regional-revenue-${index + 1}` }
  }));
  return [
    message('c1-user-1', 'user', 'How did revenue change by region?'),
    {
      ...message('c1-answer-1a', 'assistant', 'I checked the regional revenue paragraph.'),
      toolCalls
    },
    ...toolCalls.map(call => ({
      ...message(`${call.id}-result`, 'tool', 'Regional revenue data.'),
      toolCallId: call.id
    })),
    message('c1-answer-1b', 'assistant', 'Revenue increased in all three regions, led by Europe.'),
    message('c1-user-2', 'user', 'Which region should we investigate next?'),
    message(
      'c1-answer-2',
      'assistant',
      [
        'Compare Europe’s order volume with its average order value.',
        'Start with the monthly totals in the regional revenue paragraph. Use the same reporting period for each region.',
        'Then separate the change in order count from the change in average order value. Either can explain revenue growth.',
        'Check whether the regional mix changed. A larger share of high-value orders can raise revenue without a similar rise in volume.',
        'Compare those observations with the Americas and Asia Pacific before drawing a conclusion about Europe.',
        'The earlier answer describes the initial regional comparison. Scroll to the top to read that part of this conversation.'
      ].join('\n\n')
    )
  ];
};

/** Owns only assistant wire traffic; notebook/auth/other socket traffic still reaches Zeppelin. */
export class AssistantConversationWireMock {
  readonly sent: SendMessage[] = [];
  readonly historyRequests: Array<{ conversationId: string; cursor: string | null; limit: number }> = [];
  readonly restRequests: Array<{ method: string; path: string; body?: unknown }> = [];
  private noteId?: string;
  private readonly conversations = new Map<string, { metadata: ConversationMetadata; messages: RawMessage[] }>();
  private readonly runs: Array<{ socket: WebSocketRoute; request: SendMessage; completed: boolean }> = [];
  private createdCount = 0;

  static async install(page: Page): Promise<AssistantConversationWireMock> {
    const mock = new AssistantConversationWireMock();
    await page.route(
      url => !!mock.noteId && (url.pathname === mock.restBase() || url.pathname.startsWith(`${mock.restBase()}/`)),
      route => mock.handleRest(route)
    );
    // Install before the initial navigation: hash navigation to the note reuses its socket.
    await page.routeWebSocket(ZEPPELIN_WS_URL_PATTERN, socket => {
      const server = socket.connectToServer();
      socket.onMessage(frame => {
        const envelope = parse(frame);
        if (envelope?.op === 'ASSISTANT_SEND_MESSAGE') {
          mock.startRun(socket, envelope.data);
        } else {
          server.send(frame);
        }
      });
      server.onMessage(frame => {
        if (parse(frame)?.op !== 'ASSISTANT_EVENT') socket.send(frame);
      });
    });
    return mock;
  }

  useNote(noteId: string): void {
    this.noteId = noteId;
    this.addConversation('c1', 'Revenue analysis', revenueHistory());
    this.addConversation('c2', 'Notebook summary', [
      message('c2-user-1', 'user', 'Summarize this notebook.'),
      message('c2-answer-1', 'assistant', 'This notebook compares monthly revenue across regions.')
    ]);
  }

  // No timer can complete a stream: the test releases it after observing the busy DOM.
  completeRun(index: number): void {
    const run = this.runs[index];
    if (!run || run.completed) throw new Error(`No pending assistant run at index ${index}`);
    run.completed = true;
    const content = [
      STREAM_PREFIX,
      run.request.conversationId === 'c2'
        ? '| Region | Summary |\n| --- | --- |\n| Europe | Strongest regional growth |'
        : '| Region | Revenue |\n| --- | --- |\n| Europe | 118 |'
    ].join('\n\n');
    const answer = message(`run-${index}-answer`, 'assistant', content);
    this.conversations.get(run.request.conversationId)!.messages.push(answer);
    this.emit(run.socket, run.request.conversationId, 'message.done', { messageId: answer.id, content });
    this.emit(run.socket, run.request.conversationId, 'run.completed', { runId: `run-${index}` });
  }

  private restBase(): string {
    return `/api/notes/${encodeURIComponent(this.noteId!)}/conversations`;
  }

  private addConversation(id: string, title: string, messages: RawMessage[]): ConversationMetadata {
    const metadata = {
      id,
      noteId: this.noteId!,
      ownerId: 'e2e-user',
      title,
      createdAt: CREATED_AT,
      updatedAt: CREATED_AT,
      canSendMessage: true
    };
    this.conversations.set(id, { metadata, messages });
    return metadata;
  }

  private async handleRest(route: Route): Promise<void> {
    const url = new URL(route.request().url());
    const suffix = url.pathname.slice(this.restBase().length);
    const method = route.request().method();
    this.restRequests.push({
      method,
      path: url.pathname,
      ...(method === 'POST' ? { body: route.request().postDataJSON() } : {})
    });
    const respond = (body: unknown, status = 200) =>
      route.fulfill({ status, json: { status: status === 201 ? 'CREATED' : 'OK', message: '', body } });
    if (!suffix && method === 'GET') {
      await respond([...this.conversations.values()].map(({ metadata }) => metadata));
      return;
    }
    if (!suffix && method === 'POST') {
      const body = route.request().postDataJSON() as { title?: string };
      await respond(
        this.addConversation(`c-created-${++this.createdCount}`, body.title ?? 'New conversation', []),
        201
      );
      return;
    }
    const match = /^\/([^/]+)\/messages$/.exec(suffix);
    const conversationId = match ? decodeURIComponent(match[1]) : '';
    const conversation = this.conversations.get(conversationId);
    if (conversation && method === 'GET') {
      const cursor = url.searchParams.get('cursor');
      const limit = Number(url.searchParams.get('limit') ?? 10);
      this.historyRequests.push({ conversationId, cursor, limit });
      const newestFirst = [...conversation.messages].reverse();
      const cursorIndex = cursor === null ? -1 : newestFirst.findIndex(item => item.id === cursor);
      if (!Number.isInteger(limit) || limit <= 0 || (cursor !== null && cursorIndex < 0)) {
        await route.fulfill({ status: cursor !== null && cursorIndex < 0 ? 404 : 400 });
        return;
      }
      const start = cursorIndex + 1;
      const messages = newestFirst.slice(start, start + limit);
      const nextCursor = start + messages.length < newestFirst.length ? messages[messages.length - 1].id : null;
      // Gson omits the null cursor field in MessagesResponse.
      await respond({ messages, ...(nextCursor === null ? {} : { cursor: nextCursor }) });
      return;
    }
    throw new Error(`Unexpected assistant REST request: ${method} ${url.pathname}`);
  }

  private startRun(socket: WebSocketRoute, data?: Record<string, unknown>): void {
    if (
      typeof data?.noteId !== 'string' ||
      data?.noteId !== this.noteId ||
      typeof data.conversationId !== 'string' ||
      typeof data.content !== 'string' ||
      !this.conversations.has(data.conversationId)
    ) {
      throw new Error(`Unexpected assistant send: ${JSON.stringify(data)}`);
    }
    const request = { noteId: data.noteId, conversationId: data.conversationId, content: data.content };
    const index = this.runs.length;
    this.sent.push(request);
    this.runs.push({ socket, request, completed: false });
    this.conversations
      .get(request.conversationId)!
      .messages.push(message(`run-${index}-user`, 'user', request.content));
    this.emit(socket, request.conversationId, 'run.started', { runId: `run-${index}`, createdAt: CREATED_AT });
    this.emit(socket, request.conversationId, 'message.delta', {
      messageId: `run-${index}-answer`,
      delta: STREAM_PREFIX
    });
  }

  private emit(socket: WebSocketRoute, conversationId: string, type: string, payload: unknown): void {
    socket.send(JSON.stringify({ op: 'ASSISTANT_EVENT', data: { conversationId, type, payload } }));
  }
}
