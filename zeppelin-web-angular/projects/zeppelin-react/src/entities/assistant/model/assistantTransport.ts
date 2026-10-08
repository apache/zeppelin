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

import type { AssistantSendMessage, AssistantSocket, AssistantSocketEvent } from '@zeppelin/sdk';
import {
  AssistantConnectionError,
  AssistantStreamError,
  type AssistantConversation,
  type AssistantMessage,
  type AssistantMessagePage,
  type AssistantRunEvent,
  type AssistantRunState,
  type AssistantToolCallRef,
  type AssistantTransport
} from './assistantContract';
import { mergeAnswers } from './messageHistory';

const HTTP_MESSAGES: Record<number, string> = {
  403: 'You do not have permission for this conversation.',
  404: 'This conversation or note no longer exists.',
  409: 'This conversation is still answering. Try again when it finishes.',
  503: 'The assistant is not configured on this server. Ask an administrator to enable it.'
};

// run.failed carries only the HTTP status of the rejected or failed run, so the UI words it.
const RUN_ERROR_MESSAGES: Record<number, string> = {
  ...HTTP_MESSAGES,
  400: 'The assistant could not accept this message. Edit it and try again.',
  429: 'The assistant is busy. Try again shortly.',
  // Also returned when the user lost read access to the note, so it does not name the owner.
  403: 'You do not have permission to send messages to this conversation.'
};
const NOT_AVAILABLE_MESSAGE = 'The assistant is not available on this server, or this note no longer exists.';
const RUN_ERROR_FALLBACK = 'The assistant hit an error while answering. Try again.';

export class AssistantHttpError extends Error {
  constructor(
    public readonly status: number,
    public readonly location: string | null,
    message = HTTP_MESSAGES[status] ?? `Assistant request failed with HTTP ${status}`
  ) {
    super(message);
    this.name = 'AssistantHttpError';
  }
}

/** An event this panel cannot read; the field stays on the error for debugging, the message is for the user. */
export class AssistantProtocolError extends Error {
  constructor(readonly detail: string) {
    super('The assistant sent a reply this panel could not read. Try again.');
    this.name = 'AssistantProtocolError';
  }
}

// The server sends no heartbeat, so a run that goes quiet this long is treated as lost.
const RUN_IDLE_TIMEOUT_MS = 180_000;

const isRecord = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null;

const optionalString = (data: Record<string, unknown>, field: string): string | undefined => {
  const value = data[field];
  if (value !== undefined && typeof value !== 'string') {
    throw new AssistantProtocolError(`Assistant event field "${field}" must be a string`);
  }
  return value;
};

const requiredString = (data: Record<string, unknown>, field: string): string => {
  const value = optionalString(data, field);
  if (value === undefined) {
    throw new AssistantProtocolError(`Assistant event field "${field}" is required`);
  }
  return value;
};

const errorMessage = (data: Record<string, unknown>): string => {
  const error = data.error;
  const status = isRecord(error) ? error.status : undefined;
  return (typeof status === 'number' && RUN_ERROR_MESSAGES[status]) || RUN_ERROR_FALLBACK;
};

/** Maps an ASSISTANT_EVENT to the UI contract; unknown types map to undefined, so new server events are ignored. */
export const mapSocketEvent = ({ type, payload }: AssistantSocketEvent): AssistantRunEvent | undefined => {
  const data = isRecord(payload) ? payload : {};
  switch (type) {
    case 'run.started':
      return { type, runId: optionalString(data, 'runId') };
    case 'run.completed':
      return { type, runId: optionalString(data, 'runId') };
    case 'run.failed':
      return { type, runId: optionalString(data, 'runId'), message: errorMessage(data) };
    case 'message.delta':
      return { type, messageId: requiredString(data, 'messageId'), delta: requiredString(data, 'delta') };
    case 'message.done':
      return { type, messageId: requiredString(data, 'messageId'), content: requiredString(data, 'content') };
    case 'tool_call.started':
      return { type, toolCallId: requiredString(data, 'toolCallId'), name: requiredString(data, 'name') };
    case 'tool_call.done':
      return { type, toolCallId: requiredString(data, 'toolCallId'), name: optionalString(data, 'name') };
    default:
      return undefined;
  }
};

const isTerminal = (event: AssistantRunEvent): boolean => event.type === 'run.completed' || event.type === 'run.failed';

interface ConversationMessage {
  id: string;
  role: 'user' | 'assistant' | 'tool' | 'system';
  content?: string | null;
  toolCalls?: Array<{ id?: unknown; name?: unknown }> | null;
}

const toolCallRefs = (message: ConversationMessage): AssistantToolCallRef[] =>
  (message.toolCalls ?? []).flatMap(call =>
    typeof call?.id === 'string' && typeof call.name === 'string' ? [{ id: call.id, name: call.name }] : []
  );

/**
 * One bubble per turn: tool and system messages are hidden, tool rounds merge under the last message's id, and
 * the tools called on the way are kept on the answer so a reopened conversation still shows its action log.
 */
export const toVisibleMessages = (messages: ConversationMessage[]): AssistantMessage[] => {
  const visible: AssistantMessage[] = [];
  for (const message of messages) {
    if (message.role === 'user') {
      visible.push({ id: message.id, role: 'user', content: message.content ?? '' });
    } else if (message.role === 'assistant') {
      const toolCalls = toolCallRefs(message);
      // Keep a tool-only turn even when its answer is on a later page or has not arrived yet.
      if (!message.content && !toolCalls.length) continue;
      const last = visible[visible.length - 1];
      const answer: AssistantMessage = {
        id: message.id,
        role: 'assistant',
        content: message.content ?? '',
        ...(toolCalls.length ? { toolCalls } : {})
      };
      if (last?.role === 'assistant') visible[visible.length - 1] = mergeAnswers(last, answer);
      else visible.push(answer);
    }
  }
  return visible;
};

const requestHeaders = {
  // As the shell does in production, so the server answers 401/405 instead of a login page.
  'X-Requested-With': 'XMLHttpRequest'
};

// Zeppelin JSON endpoints wrap the payload as { status, message, body }.
const unwrapBody = (value: unknown): unknown =>
  typeof value === 'object' && value !== null && 'body' in value ? value.body : value;

/** Hands 401/405 to the host, which owns login redirects and logout. */
export type AssistantAuthErrorHandler = (status: number, location: string | null) => void;

const httpError = (response: Response, onAuthError?: AssistantAuthErrorHandler): AssistantHttpError => {
  const error = new AssistantHttpError(response.status, response.headers.get('Location'));
  if (error.status === 401 || error.status === 405) {
    onAuthError?.(error.status, error.location);
  }
  return error;
};

const requestJson = async <T>(
  url: string,
  init: { method?: string; body?: unknown; signal?: AbortSignal } = {},
  onAuthError?: AssistantAuthErrorHandler
): Promise<T> => {
  const response = await fetch(url, {
    method: init.method ?? 'GET',
    ...(init.signal ? { signal: init.signal } : {}),
    credentials: 'include',
    headers:
      init.body === undefined
        ? { ...requestHeaders, Accept: 'application/json' }
        : { ...requestHeaders, Accept: 'application/json', 'Content-Type': 'application/json' },
    body: init.body === undefined ? undefined : JSON.stringify(init.body)
  });
  if (!response.ok) {
    throw httpError(response, onAuthError);
  }
  const text = await response.text();
  return (text ? unwrapBody(JSON.parse(text)) : undefined) as T;
};

// A server without the assistant API answers the note's conversation collection with 404.
const collectionRequest = async <T>(...args: Parameters<typeof requestJson>): Promise<T> => {
  try {
    return await requestJson<T>(...args);
  } catch (error) {
    if (error instanceof AssistantHttpError && error.status === 404) {
      throw new AssistantHttpError(404, error.location, NOT_AVAILABLE_MESSAGE);
    }
    throw error;
  }
};

const MAX_ABANDONED_RUNS = 200;
/**
 * Runs on one host connection. Pending sends survive note switches until acknowledged; abandoned run ids keep
 * later sends from following their events. The server continues runs after the UI stops listening.
 */
interface PendingRun {
  abandoned: boolean;
  runId?: string;
}
export interface RunTracking {
  abandoned: Set<string>;
  pending: Map<string, PendingRun>;
  active: Map<string, string>;
  disconnected: Set<string>;
  listeners: Map<string, Set<(state: AssistantRunState) => void>>;
  stopTracking?: () => void;
}
export const createRunTracking = (): RunTracking => ({
  abandoned: new Set(),
  pending: new Map(),
  active: new Map(),
  disconnected: new Set(),
  listeners: new Map()
});
const runState = (tracking: RunTracking, conversationId: string): AssistantRunState =>
  tracking.disconnected.has(conversationId)
    ? 'disconnected'
    : tracking.pending.has(conversationId) || tracking.active.has(conversationId)
      ? 'running'
      : 'idle';
const notifyRunState = (tracking: RunTracking, conversationId: string) => {
  tracking.listeners.get(conversationId)?.forEach(listener => listener(runState(tracking, conversationId)));
};
const releaseTracking = (tracking: RunTracking) => {
  if (!tracking.pending.size && !tracking.active.size) tracking.stopTracking?.();
};
const connectionRuns = new WeakMap<object, RunTracking>();
const runsFor = (socket: AssistantSocket): RunTracking => {
  const key = socket.connectionKey ?? socket;
  let runs = connectionRuns.get(key);
  if (!runs) connectionRuns.set(key, (runs = createRunTracking()));
  return runs;
};
const abandon = (tracking: RunTracking, runId: string) => {
  tracking.abandoned.add(runId);
  if (tracking.abandoned.size > MAX_ABANDONED_RUNS) {
    const oldest = tracking.abandoned.values().next().value;
    if (oldest !== undefined) tracking.abandoned.delete(oldest);
  }
};

// ACKs belong to the connection, even if the note or conversation that sent the request has unmounted.
const trackPendingRun = (socket: AssistantSocket, conversationId: string, tracking: RunTracking): PendingRun => {
  const pending: PendingRun = { abandoned: false };
  tracking.disconnected.delete(conversationId);
  tracking.pending.set(conversationId, pending);
  notifyRunState(tracking, conversationId);
  if (!tracking.stopTracking) {
    const unsubscribe = socket.subscribe(event => {
      const request = tracking.pending.get(event.conversationId);
      const payload = isRecord(event.payload) ? event.payload : {};
      const runId = typeof payload.runId === 'string' ? payload.runId : undefined;
      if (event.type === 'run.started' && runId && request) {
        request.runId = runId;
        tracking.active.set(event.conversationId, runId);
        if (request.abandoned) abandon(tracking, runId);
        tracking.pending.delete(event.conversationId);
      } else if (event.type === 'run.failed' || event.type === 'run.completed') {
        const activeRun = tracking.active.get(event.conversationId);
        if (activeRun && (!runId || runId === activeRun)) {
          tracking.active.delete(event.conversationId);
        } else if (request && !(runId && tracking.abandoned.has(runId))) {
          tracking.pending.delete(event.conversationId);
        } else return;
      } else return;
      notifyRunState(tracking, event.conversationId);
      releaseTracking(tracking);
    });
    const unsubscribeClose = socket.subscribeClose(() => {
      const conversations = new Set([...Array.from(tracking.pending.keys()), ...Array.from(tracking.active.keys())]);
      tracking.pending.clear();
      tracking.active.clear();
      tracking.abandoned.clear();
      conversations.forEach(id => tracking.disconnected.add(id));
      conversations.forEach(id => notifyRunState(tracking, id));
      tracking.stopTracking?.();
    });
    tracking.stopTracking = () => {
      unsubscribe();
      unsubscribeClose();
      tracking.stopTracking = undefined;
    };
  }
  return pending;
};

/**
 * Sends one message over the host's notebook WebSocket and yields that conversation's events until a terminal one.
 * Until this message's run.started arrives, only a rejection (run.failed) belongs to it; events of an abandoned run
 * on the same conversation are skipped.
 */
export async function* socketRun(
  socket: AssistantSocket,
  message: AssistantSendMessage,
  signal: AbortSignal,
  tracking: RunTracking,
  idleTimeoutMs = RUN_IDLE_TIMEOUT_MS
): AsyncIterable<AssistantRunEvent> {
  signal.throwIfAborted();
  const { abandoned } = tracking;
  if (tracking.pending.has(message.conversationId)) {
    yield { type: 'run.failed', message: 'The previous request is still being confirmed. Try again shortly.' };
    return;
  }
  const pending = trackPendingRun(socket, message.conversationId, tracking);
  let sent = false;
  const queue: Array<AssistantRunEvent | Error> = [];
  let wake: (() => void) | undefined;
  const push = (item: AssistantRunEvent | Error) => {
    queue.push(item);
    wake?.();
  };
  const unsubscribe = socket.subscribe(event => {
    if (event.conversationId !== message.conversationId) return;
    try {
      const mapped = mapSocketEvent(event);
      if (mapped) push(mapped);
    } catch (error) {
      push(error instanceof Error ? error : new Error(String(error)));
    }
  });
  const unsubscribeClose = socket.subscribeClose(() => push(new AssistantConnectionError()));
  const onAbort = () => {
    pending.abandoned = true;
    if (pending.runId) abandon(tracking, pending.runId);
    wake?.();
  };
  signal.addEventListener('abort', onAbort, { once: true });
  let started = false;
  let ownRunId: string | undefined;
  let ended = false;
  try {
    socket.send(message);
    sent = true;
    while (true) {
      signal.throwIfAborted();
      if (queue.length === 0) {
        let timer: ReturnType<typeof setTimeout> | undefined;
        await new Promise<void>(resolve => {
          wake = resolve;
          timer = setTimeout(() => push(new AssistantStreamError()), idleTimeoutMs);
        });
        clearTimeout(timer);
        wake = undefined;
      }
      signal.throwIfAborted();
      const item = queue.shift();
      if (item === undefined) continue;
      if (item instanceof Error) throw item;
      const runId = 'runId' in item ? item.runId : undefined;
      if (runId && runId !== ownRunId && abandoned.has(runId)) {
        if (isTerminal(item)) abandoned.delete(runId);
        continue;
      }
      // Once running, only its own terminal event ends it; deltas have no run id, but runs are one at a time.
      if (started && isTerminal(item) && runId && ownRunId && runId !== ownRunId) continue;
      if (!started) {
        if (item.type === 'run.started') {
          started = true;
          ownRunId = item.runId;
        } else if (item.type !== 'run.failed') continue;
      }
      if (isTerminal(item)) ended = true;
      yield item;
      if (ended) return;
    }
  } finally {
    if (!ended && sent) {
      pending.abandoned = true;
      const runId = pending.runId ?? ownRunId;
      if (runId) abandon(tracking, runId);
    }
    if (!sent || ended) {
      if (tracking.pending.get(message.conversationId) === pending) tracking.pending.delete(message.conversationId);
      notifyRunState(tracking, message.conversationId);
      releaseTracking(tracking);
    }
    signal.removeEventListener('abort', onAbort);
    unsubscribe();
    unsubscribeClose();
  }
}

type ConversationSummary = {
  id: string;
  title?: string;
  ownerId?: string;
  canSendMessage?: boolean;
  createdAt?: string;
  updatedAt?: string;
};
type MessagePage = { messages: ConversationMessage[]; cursor?: string | null };

// One page of raw server messages; the panel loads earlier pages as the user scrolls up.
const HISTORY_PAGE_SIZE = 50;

/**
 * Transport for the per-note conversation API. REST for conversations and history (`apiBase` is the host's
 * REST base, e.g. `.../api`); sending goes over the host's notebook WebSocket.
 */
export const createAssistantTransport = (
  apiBase: string,
  noteId: string,
  socket: AssistantSocket,
  onAuthError?: AssistantAuthErrorHandler
): AssistantTransport => {
  const base = `${apiBase.replace(/\/$/, '')}/notes/${encodeURIComponent(noteId)}/conversations`;
  const runs = runsFor(socket);
  const toConversation = (conversation: ConversationSummary): AssistantConversation => {
    return {
      id: conversation.id,
      title: conversation.title,
      ...(conversation.ownerId !== undefined ? { ownerId: conversation.ownerId } : {}),
      ...(typeof conversation.canSendMessage === 'boolean' ? { canSendMessage: conversation.canSendMessage } : {}),
      ...((conversation.updatedAt ?? conversation.createdAt)
        ? { updatedAt: conversation.updatedAt ?? conversation.createdAt }
        : {})
    };
  };
  // The server names an untitled conversation by its creation time.
  const titleFor = (title?: string) => {
    const trimmed = title?.trim().replace(/\s+/g, ' ');
    return trimmed ? { title: trimmed.length > 80 ? `${trimmed.slice(0, 79)}…` : trimmed } : {};
  };
  return {
    subscribeRunState: (conversationId, listener) => {
      let listeners = runs.listeners.get(conversationId);
      if (!listeners) runs.listeners.set(conversationId, (listeners = new Set()));
      let stopped = false;
      const observe = (state: AssistantRunState) => {
        listener(state);
      };
      listeners.add(observe);
      observe(runState(runs, conversationId));
      const stop = () => {
        if (stopped) return;
        stopped = true;
        listeners.delete(observe);
        if (!listeners.size) runs.listeners.delete(conversationId);
        socket.signal?.removeEventListener('abort', stop);
      };
      socket.signal?.addEventListener('abort', stop, { once: true });
      if (socket.signal?.aborted) stop();
      return stop;
    },
    // Latest activity first, so the panel opens on it; undated ones stay last in server order (stable sort).
    listConversations: async () =>
      (await collectionRequest<ConversationSummary[]>(base, {}, onAuthError))
        .map(conversation => ({
          conversation,
          time: Date.parse(conversation.updatedAt ?? conversation.createdAt ?? '') || 0
        }))
        .sort((a, b) => b.time - a.time)
        .map(({ conversation }) => toConversation(conversation)),
    createConversation: async ({ title } = {}) =>
      toConversation(
        await collectionRequest<ConversationSummary>(base, { method: 'POST', body: titleFor(title) }, onAuthError)
      ),
    deleteConversation: async conversationId => {
      await requestJson<void>(`${base}/${encodeURIComponent(conversationId)}`, { method: 'DELETE' }, onAuthError);
    },
    getMessages: async (conversationId, before): Promise<AssistantMessagePage> => {
      const cursor = before ? `&cursor=${encodeURIComponent(before)}` : '';
      const page = await requestJson<MessagePage>(
        `${base}/${encodeURIComponent(conversationId)}/messages?limit=${HISTORY_PAGE_SIZE}${cursor}`,
        {},
        onAuthError
      );
      if (!before && runs.disconnected.delete(conversationId)) notifyRunState(runs, conversationId);
      // The server pages from the latest message backwards; the panel renders oldest to newest.
      return { messages: toVisibleMessages([...page.messages].reverse()), earlierCursor: page.cursor ?? null };
    },
    openRun: (conversationId, body, signal) =>
      socketRun(
        socket,
        {
          noteId,
          conversationId,
          content: body.prompt
        },
        signal,
        runs
      )
  };
};

/**
 * Guards a per-note transport. After `setActive(false)` (note change or unmount) every pending or later call rejects
 * with an AbortError and open runs are aborted, so a late response from the previous note cannot reach the new one.
 */
export const scopeTransport = (inner: AssistantTransport, sessionSignal?: AbortSignal) => {
  let active = true;
  const runs = new Set<AbortController>();
  const subscriptions = new Set<() => void>();
  const assertActive = () => {
    if (!active || sessionSignal?.aborted) {
      throw new DOMException('Notebook changed', 'AbortError');
    }
  };
  const guarded = async <T>(request: () => Promise<T>): Promise<T> => {
    assertActive();
    try {
      const result = await request();
      assertActive();
      return result;
    } catch (error) {
      assertActive();
      throw error;
    }
  };
  const transport: AssistantTransport = {
    listConversations: () => guarded(() => inner.listConversations()),
    createConversation: body => guarded(() => inner.createConversation(body)),
    deleteConversation: id => guarded(() => inner.deleteConversation(id)),
    getMessages: (id, before) => guarded(() => inner.getMessages(id, before)),
    ...(inner.subscribeRunState
      ? {
          subscribeRunState: (id: string, listener: (state: AssistantRunState) => void) => {
            if (!active || sessionSignal?.aborted) return () => undefined;
            const unsubscribe = inner.subscribeRunState!(id, state => {
              if (active && !sessionSignal?.aborted) listener(state);
            });
            const stop = () => {
              if (!subscriptions.delete(stop)) return;
              unsubscribe();
              sessionSignal?.removeEventListener('abort', stop);
            };
            subscriptions.add(stop);
            sessionSignal?.addEventListener('abort', stop, { once: true });
            return stop;
          }
        }
      : {}),
    async *openRun(id, body, signal) {
      assertActive();
      const controller = new AbortController();
      const abort = () => controller.abort();
      signal.addEventListener('abort', abort, { once: true });
      sessionSignal?.addEventListener('abort', abort, { once: true });
      if (signal.aborted) {
        abort();
      }
      runs.add(controller);
      try {
        for await (const event of inner.openRun(id, body, controller.signal)) {
          assertActive();
          if (controller.signal.aborted) {
            throw new DOMException('Run stopped', 'AbortError');
          }
          yield event;
        }
      } catch (error) {
        assertActive();
        throw error;
      } finally {
        controller.abort();
        signal.removeEventListener('abort', abort);
        sessionSignal?.removeEventListener('abort', abort);
        runs.delete(controller);
      }
    }
  };
  return {
    get active() {
      return active && !sessionSignal?.aborted;
    },
    transport,
    setActive(next: boolean) {
      active = next;
      if (!next) {
        runs.forEach(controller => controller.abort());
        runs.clear();
        subscriptions.forEach(stop => stop());
      }
    }
  };
};
