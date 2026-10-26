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

import type {
  AssistantConversationApi,
  AssistantAnswerMessage,
  AssistantMessage,
  AssistantMessagePage
} from '@/entities/assistant/model/assistantContract';
import { joinPages, mergeAnswers } from '@/entities/assistant/model/messageHistory';
import type { AssistantAnswerView } from '@/entities/assistant/ui/answerView';
import type { AssistantPanelHistory, AssistantRecovery } from './assistantPanelState';
import type { AssistantRunSnapshot } from './assistantRun';

const reconcileMessageKeys = (
  entry: AssistantPanelHistory,
  messages: AssistantMessage[],
  localAnswer?: AssistantAnswerMessage
) => {
  const turns = new Map<string, { answerKey: string; question?: AssistantMessage }>();
  const rendered = localAnswer ? [...entry.messages, localAnswer] : entry.messages;

  for (let index = 0; index < rendered.length; index++) {
    const message = rendered[index];
    if (message.role !== 'assistant') continue;

    const turn = {
      answerKey:
        (message === localAnswer ? entry.answerKey : undefined) ?? entry.messageKeys.get(message.id) ?? message.id,
      question: rendered[index - 1]
    };
    for (const segment of message.segments) turns.set(segment.id, turn);
  }

  if (!turns.size) return entry.messageKeys;

  const keys = new Map(entry.messageKeys);
  for (let index = 0; index < messages.length; index++) {
    const message = messages[index];
    if (message.role !== 'assistant') continue;

    const segment = message.segments.find(segment => turns.has(segment.id));
    if (!segment) continue;

    const turn = turns.get(segment.id)!;
    keys.set(message.id, turn.answerKey);

    const savedQuestion = messages[index - 1];
    // Identical question text is not enough to identify a turn.
    if (
      turn.question?.role === 'user' &&
      savedQuestion?.role === 'user' &&
      turn.question.content === savedQuestion.content
    ) {
      keys.set(savedQuestion.id, entry.messageKeys.get(turn.question.id) ?? turn.question.id);
    }
  }

  return keys;
};

const reconcileLatestHistory = (
  entry: AssistantPanelHistory,
  page: AssistantMessagePage,
  localAnswer?: AssistantAnswerMessage
): Pick<AssistantPanelHistory, 'messages' | 'messageKeys' | 'localAnswerViews' | 'cursor'> => {
  const messageKeys = reconcileMessageKeys(entry, page.messages, localAnswer);
  let latest = page.messages;
  let cursor = page.cursor;
  let prefix: AssistantMessage[] = [];
  const first = latest[0];

  if (entry.loaded && first) {
    const firstKey = messageKeys.get(first.id) ?? first.id;
    let index = entry.messages.findIndex(message => (messageKeys.get(message.id) ?? message.id) === firstKey);
    if (index < 0 && localAnswer && entry.answerKey === firstKey) index = entry.messages.length;

    if (index >= 0) {
      prefix = entry.messages.slice(0, index);
      const previous = entry.messages[index] ?? localAnswer;
      let leading = 0;

      if (first.role === 'assistant' && previous?.role === 'assistant') {
        leading = previous.segments.findIndex(segment => segment.id === first.segments[0]?.id);
        if (leading > 0) {
          latest = [
            mergeAnswers({ ...previous, segments: previous.segments.slice(0, leading) }, first),
            ...latest.slice(1)
          ];
        }
      }

      // Retained older messages still need their original pagination cursor.
      if (prefix.length || leading >= 0) cursor = entry.cursor;
    }
  }

  const localAnswerViews = new Map<string, AssistantAnswerView>();
  for (const message of prefix) {
    const view = entry.localAnswerViews.get(message.id);
    if (view) localAnswerViews.set(message.id, view);
  }

  return {
    messages: prefix.length ? [...prefix, ...latest] : latest,
    messageKeys,
    localAnswerViews,
    cursor
  };
};

type HistoryLoadResult =
  | { status: 'committed'; version: number }
  | { status: 'ignored' }
  | { status: 'failed'; version: number; error: unknown };

interface RetainedTurn {
  key: string;
  questionKey: string;
  view?: AssistantAnswerView;
  message?: AssistantAnswerMessage;
  recovery?: AssistantRecovery;
}

/** Restores saved history without replacing the current failed turn's runtime error and partial answer. */
const retainFailedTurn = (
  entry: AssistantPanelHistory,
  messages: AssistantMessage[],
  keys: Map<string, string>,
  turn?: RetainedTurn
): Pick<AssistantPanelHistory, 'messages' | 'messageKeys'> => {
  if (turn?.recovery !== 'send') return { messages, messageKeys: keys };

  const question = entry.messages.find(
    message => message.role === 'user' && (entry.messageKeys.get(message.id) ?? message.id) === turn.questionKey
  );
  if (!question || question.role !== 'user') return { messages, messageKeys: keys };

  const messageKeys = new Map(keys);
  let savedQuestion =
    messages.find(
      message =>
        message.role === 'user' && message.id !== question.id && messageKeys.get(message.id) === turn.questionKey
    ) ?? messages.find(message => message.id === question.id);

  // The fixture has one writer: only this failed turn's trailing question may match without a shared user ID.
  const trailing = messages.at(-1);
  if (!savedQuestion && trailing?.role === 'user' && trailing.content === question.content) savedQuestion = trailing;
  if (savedQuestion) messageKeys.set(savedQuestion.id, turn.questionKey);

  const retained = messages.filter(
    message =>
      message.role !== 'user' ||
      !savedQuestion ||
      message.id === savedQuestion.id ||
      (messageKeys.get(message.id) ?? message.id) !== turn.questionKey
  );
  if (!savedQuestion) retained.push(question);

  return { messages: retained, messageKeys };
};

export class AssistantHistory {
  private messages: AssistantMessage[] = [];
  private messageKeys = new Map<string, string>();
  private localAnswerViews = new Map<string, AssistantAnswerView>();
  private cursor: string | null = null;
  private loaded: boolean;

  private requestStatus: 'idle' | 'latest' | 'earlier' | 'failed' = 'idle';
  private requestVersion = 0;
  private consumedCursors = new Set<string>();

  private turnSequence = 0;
  private turn?: RetainedTurn;

  private disposed = false;

  constructor(
    private readonly key: string,
    hasConversation: boolean,
    private readonly changed: () => void
  ) {
    this.loaded = !hasConversation;
  }

  get snapshot(): AssistantPanelHistory {
    const failedKey = this.turn?.recovery === 'send' ? this.turn.key : undefined;

    return {
      // Keep saved answer boundaries for pagination, but render the retained failed answer only once.
      messages: failedKey
        ? this.messages.filter(
            message => message.role !== 'assistant' || this.messageKeys.get(message.id) !== failedKey
          )
        : this.messages,
      messageKeys: this.messageKeys,
      localAnswerViews: this.localAnswerViews,
      cursor: this.cursor,
      loaded: this.loaded,
      loading: this.requestStatus === 'latest',
      loadingEarlier: this.requestStatus === 'earlier',
      answer: this.turn?.view,
      answerKey: this.turn?.key,
      answerRecovery: this.turn?.recovery
    };
  }

  /** Starts a new turn and retains any preceding unreconciled answer. */
  beginTurn(): string {
    this.invalidateRequests();
    this.promoteTurn();

    const sequence = ++this.turnSequence;
    this.turn = {
      key: `local-answer:${this.key}:${sequence}`,
      questionKey: `local-question:${this.key}:${sequence}`
    };

    this.changed();
    return this.turn.key;
  }

  private promoteTurn(): void {
    if (!this.turn?.message) return;

    const { message, key, view } = this.turn;
    const saved = this.messages.findIndex(
      current => current.role === 'assistant' && this.messageKeys.get(current.id) === key
    );
    this.messages =
      saved < 0
        ? [...this.messages, message]
        : this.messages.map((current, index) => (index === saved ? message : current));
    this.messageKeys = new Map(this.messageKeys).set(message.id, key);
    if (view) this.localAnswerViews = new Map(this.localAnswerViews).set(message.id, view);
  }

  acceptTurn(content: string, run: AssistantRunSnapshot): void {
    if (!this.turn || this.disposed) return;

    this.messages = [...this.messages, { id: this.turn.questionKey, role: 'user', content }];
    this.updateTurn(run);
  }

  updateTurn(run: AssistantRunSnapshot): void {
    if (!this.turn || this.disposed) return;

    this.turn = {
      ...this.turn,
      view: run.view,
      message: run.message,
      recovery: run.outcome === 'failed' && run.accepted ? 'send' : run.outcome === 'unknown' ? 'reload' : undefined
    };
    this.changed();
  }

  private invalidateRequests(): void {
    ++this.requestVersion;
    this.requestStatus = 'idle';
  }

  async loadLatest(api: AssistantConversationApi, conversationId: string): Promise<HistoryLoadResult> {
    if (this.disposed) return { status: 'ignored' };

    const version = ++this.requestVersion;
    this.requestStatus = 'latest';
    this.changed();

    try {
      const page = await api.getMessages(conversationId);
      if (!this.current(version)) return { status: 'ignored' };

      const reconciled = reconcileLatestHistory(this.snapshot, page, this.turn?.message);
      const retained = retainFailedTurn(this.snapshot, reconciled.messages, reconciled.messageKeys, this.turn);
      this.messages = retained.messages;
      this.messageKeys = retained.messageKeys;
      this.localAnswerViews = reconciled.localAnswerViews;
      this.cursor = reconciled.cursor;
      if (this.turn?.recovery !== 'send') this.turn = undefined;
      this.loaded = true;
      this.requestStatus = 'idle';
      this.consumedCursors.clear();

      this.changed();
      return { status: 'committed', version };
    } catch (error) {
      if (!this.current(version)) return { status: 'ignored' };

      this.requestStatus = 'failed';
      this.changed();
      return { status: 'failed', version, error };
    }
  }

  get canLoadEarlier(): boolean {
    return !this.disposed && !!this.cursor && this.requestStatus === 'idle' && !this.consumedCursors.has(this.cursor);
  }

  async loadEarlier(api: AssistantConversationApi, conversationId: string): Promise<HistoryLoadResult> {
    const cursor = this.cursor;
    if (!this.canLoadEarlier || !cursor) return { status: 'ignored' };

    const version = ++this.requestVersion;
    this.requestStatus = 'earlier';
    this.changed();

    try {
      const page = await api.getMessages(conversationId, cursor);
      if (!this.current(version)) return { status: 'ignored' };

      const messages = joinPages(page.messages, this.messages);
      const keys = reconcileMessageKeys(this.snapshot, messages, this.turn?.message);
      const retained = retainFailedTurn(this.snapshot, messages, keys, this.turn);
      this.messages = retained.messages;
      this.messageKeys = retained.messageKeys;
      this.cursor = page.cursor;
      this.requestStatus = 'idle';
      this.consumedCursors.add(cursor);

      this.changed();
      return { status: 'committed', version };
    } catch (error) {
      if (!this.current(version)) return { status: 'ignored' };

      this.requestStatus = 'failed';
      this.changed();
      return { status: 'failed', version, error };
    }
  }

  /** Whether this result is still valid for the current history. */
  isCurrent(result: HistoryLoadResult): boolean {
    return result.status !== 'ignored' && this.current(result.version);
  }

  private current(version: number): boolean {
    return !this.disposed && this.requestVersion === version;
  }

  dispose(): void {
    this.disposed = true;
    this.invalidateRequests();
  }
}
