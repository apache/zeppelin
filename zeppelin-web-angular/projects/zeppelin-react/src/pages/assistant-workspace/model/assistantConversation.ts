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

import { AssistantStreamError, type AssistantConversation } from '@/entities/assistant/model/assistantContract';
import type { AssistantDataSource } from './assistantDataSource';
import { AssistantHistory } from './assistantHistory';
import type {
  AssistantDeletionResult,
  AssistantPanelEntry,
  AssistantPanelFailure,
  AssistantPanelPhase
} from './assistantPanelState';
import { AssistantRun, type AssistantRunSnapshot } from './assistantRun';

const UNKNOWN_OUTCOME =
  'The outcome of an earlier question is unknown. Reload can show saved messages, but cannot establish whether that run has finished. Sending again is an explicit new submission.';

export class AssistantConversationSession {
  private conversation?: AssistantConversation;
  private draft = '';
  private phase: AssistantPanelPhase = 'idle';
  private failure?: AssistantPanelFailure;
  private uncertainty?: string;
  private submittedQuestion?: string;
  private deleting = false;
  private historyRefreshPending = false;

  private disposed = false;
  private controller?: AbortController;

  private readonly history: AssistantHistory;
  private entry: AssistantPanelEntry;

  constructor(
    readonly key: string,
    conversation: AssistantConversation | undefined,
    private readonly source: AssistantDataSource,
    private readonly changed: () => void,
    private readonly confirmed: (session: AssistantConversationSession, conversation: AssistantConversation) => void,
    private readonly activity: () => void
  ) {
    this.conversation = conversation;
    this.history = new AssistantHistory(key, !!conversation, this.publish);
    this.entry = this.project();
  }

  get snapshot(): AssistantPanelEntry {
    return this.entry;
  }

  /** Does not emit a change notification. */
  updateConversation(conversation: AssistantConversation): void {
    const current = this.conversation;
    if (this.disposed || current?.id !== conversation.id) return;

    if (
      current.title === conversation.title &&
      current.ownerId === conversation.ownerId &&
      current.canSendMessage === conversation.canSendMessage &&
      current.updatedAt === conversation.updatedAt
    ) {
      return;
    }

    this.conversation = conversation;
    this.entry = this.project();
  }

  private project(): AssistantPanelEntry {
    return {
      ...this.history.snapshot,
      key: this.key,
      conversation: this.conversation,
      draft: this.draft,
      phase: this.phase,
      failure: this.failure,
      uncertainty: this.uncertainty,
      submittedQuestion: this.submittedQuestion,
      deleting: this.deleting
    };
  }

  private readonly publish = (): void => {
    if (this.disposed) return;

    this.entry = this.project();
    this.changed();
  };

  setDraft(value: string): void {
    if (this.disposed) return;

    this.draft = value;
    this.publish();
  }

  ensureHistory(): void {
    const history = this.history.snapshot;
    if (!history.loaded && !history.loading && !this.deleting && this.phase === 'idle') void this.reload();
  }

  async reload(preserveFailure = false): Promise<void> {
    if (this.disposed || !this.conversation || this.deleting || (this.phase !== 'idle' && this.phase !== 'unknown')) {
      return;
    }

    this.historyRefreshPending = false;
    if (!preserveFailure) this.failure = undefined;
    const result = await this.history.loadLatest(this.source.api, this.conversation.id);
    if (this.disposed || result.status === 'ignored' || !this.history.isCurrent(result)) return;

    if (result.status === 'failed') {
      this.failure = {
        message: result.error instanceof Error ? result.error.message : 'Could not load conversation history.',
        kind: 'connection',
        recovery: 'reload'
      };
    } else if (this.phase !== 'unknown' && !preserveFailure && this.history.snapshot.answerRecovery !== 'send') {
      this.submittedQuestion = undefined;
    }

    this.publish();
  }

  private async refreshHistory(): Promise<void> {
    if (!this.historyRefreshPending || this.disposed || this.deleting || this.phase !== 'idle') return;

    await this.reload(this.failure?.kind === 'failed');
  }

  async loadEarlier(): Promise<boolean> {
    if (this.disposed || !this.conversation || this.deleting || (this.phase !== 'idle' && this.phase !== 'unknown')) {
      return false;
    }
    if (!this.history.canLoadEarlier) return false;

    this.failure = undefined;
    const result = await this.history.loadEarlier(this.source.api, this.conversation.id);
    if (this.disposed || result.status === 'ignored' || !this.history.isCurrent(result)) return false;

    if (result.status === 'failed') {
      this.failure = {
        message: result.error instanceof Error ? result.error.message : 'Could not load earlier messages.',
        kind: 'connection',
        recovery: 'reload'
      };
    } else {
      this.failure = undefined;
    }

    this.publish();
    return result.status === 'committed';
  }

  async send(question?: string): Promise<void> {
    const submitted = question ?? this.draft;
    const content = submitted.trim();

    if (
      this.disposed ||
      !content ||
      (this.phase !== 'idle' && this.phase !== 'unknown') ||
      this.deleting ||
      this.conversation?.canSendMessage === false
    ) {
      return;
    }

    const controller = new AbortController();
    this.controller = controller;
    this.draft = submitted;
    this.submittedQuestion = content;
    this.failure = undefined;
    this.phase = this.conversation ? 'pending' : 'creating';
    if (this.history.snapshot.loading) this.historyRefreshPending = true;
    const answerKey = this.history.beginTurn();

    if (!this.conversation) {
      try {
        const conversation = await this.source.api.createConversation({});
        if (this.disposed || this.controller !== controller) return;

        // A rejected first send must reuse the conversation already created.
        this.conversation = conversation;
        this.phase = 'pending';
        this.entry = this.project();
        this.confirmed(this, conversation);
      } catch (error) {
        if (!this.disposed && this.controller === controller) {
          this.controller = undefined;
          this.phase = 'idle';
          this.failure = {
            message: error instanceof Error ? error.message : 'Could not create a conversation.',
            kind: 'failed',
            recovery: 'send'
          };
          this.publish();
        }
        return;
      }
    }

    const run = new AssistantRun(answerKey);
    const publishRun = (previous: AssistantRunSnapshot, next: AssistantRunSnapshot) => {
      if (!previous.accepted && next.accepted) {
        this.phase = 'running';
        if (this.draft === submitted) this.draft = '';
      }

      if (next.outcome === 'failed') {
        this.draft ||= submitted;
        if (!next.accepted) {
          this.failure = { message: next.rejection!, kind: 'failed', recovery: 'send' };
        }
      } else if (next.outcome === 'unknown') {
        this.draft ||= submitted;
        this.uncertainty = UNKNOWN_OUTCOME;
      }

      if (!previous.accepted && next.accepted) this.history.acceptTurn(content, next);
      else this.history.updateTurn(next);
    };

    try {
      for await (const event of this.source.run(this.conversation.id, content, controller.signal)) {
        if (this.disposed || this.controller !== controller) return;

        const previous = run.snapshot;
        const next = run.receive(event);
        if (next !== previous) publishRun(previous, next);
        if (next.outcome) break;
      }

      if (this.disposed || this.controller !== controller) return;
      if (!run.snapshot.outcome) throw new AssistantStreamError();
    } catch (error) {
      if (this.disposed || this.controller !== controller) return;

      const previous = run.snapshot;
      const next = run.interrupt(error);
      if (next !== previous) publishRun(previous, next);
    } finally {
      if (this.controller === controller) this.controller = undefined;
    }

    if (this.disposed) return;

    // The next send must wait for this consumer to release the conversation.
    this.historyRefreshPending ||=
      run.snapshot.outcome === 'completed' || (run.snapshot.outcome === 'failed' && !this.history.snapshot.loaded);
    this.phase = run.snapshot.outcome === 'unknown' ? 'unknown' : 'idle';
    this.publish();

    if (run.snapshot.accepted) this.activity();
    await this.refreshHistory();
  }

  async delete(): Promise<AssistantDeletionResult> {
    if (this.disposed || !this.conversation || this.deleting) return { status: 'ignored' };

    this.deleting = true;
    this.publish();

    try {
      await this.source.api.deleteConversation(this.conversation.id);
      if (this.disposed) return { status: 'ignored' };

      this.dispose();
      return { status: 'deleted' };
    } catch (error) {
      if (this.disposed) return { status: 'ignored' };

      const message = error instanceof Error ? error.message : 'Could not delete the conversation.';
      this.deleting = false;
      this.failure = { message, kind: 'failed' };
      this.publish();
      void this.refreshHistory();

      return { status: 'failed', message };
    }
  }

  dispose(): void {
    this.disposed = true;
    this.history.dispose();
    this.controller?.abort();
    this.controller = undefined;
  }
}
