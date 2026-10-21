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

import type { AssistantConversation } from '@/entities/assistant/model/assistantContract';
import { AssistantConversationSession } from './assistantConversation';
import type { AssistantDataSource } from './assistantDataSource';
import type { AssistantDeletionResult, AssistantPanel } from './assistantPanelState';

export class AssistantPanelModel {
  private conversations: AssistantConversation[] = [];
  private listLoading = true;
  private listError?: string;

  private selectedKey = 'draft:0';
  private draftKey: string | undefined = 'draft:0';
  private draftSequence = 0;

  private readonly sessions = new Map<string, AssistantConversationSession>();
  private readonly deleted = new Set<string>();

  private readonly listeners = new Set<() => void>();
  private active = false;
  private generation = 0;
  private listVersion = 0;
  private snapshot: AssistantPanel;

  constructor(private readonly source: AssistantDataSource) {
    this.createSession(this.selectedKey);
    this.snapshot = this.project();
  }

  readonly getSnapshot = (): AssistantPanel => this.snapshot;

  readonly subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  start(): void {
    if (this.active) return;

    this.active = true;
    const generation = ++this.generation;

    this.sessions.forEach(session => session.dispose());
    this.sessions.clear();
    this.deleted.clear();

    this.conversations = [];
    this.listLoading = true;
    this.listError = undefined;
    this.selectedKey = 'draft:0';
    this.draftKey = 'draft:0';
    this.draftSequence = 0;

    this.createSession(this.selectedKey);
    this.publish();

    void this.loadList(generation);
  }

  private async loadList(generation: number): Promise<void> {
    const version = ++this.listVersion;
    try {
      const conversations = await this.source.api.listConversations();
      if (!this.active || this.generation !== generation || this.listVersion !== version) return;

      const visible = conversations.filter(conversation => !this.deleted.has(conversation.id));
      const refreshed = new Map(visible.map(conversation => [conversation.id, conversation]));
      this.conversations = [
        ...this.conversations.filter(conversation => !refreshed.has(conversation.id)),
        ...visible
      ].sort((a, b) => (Date.parse(b.updatedAt) || 0) - (Date.parse(a.updatedAt) || 0));

      this.sessions.forEach(session => {
        const id = session.snapshot.conversation?.id;
        const conversation = id && refreshed.get(id);
        if (conversation) session.updateConversation(conversation);
      });

      this.listLoading = false;
      this.listError = undefined;
    } catch (error) {
      if (!this.active || this.generation !== generation || this.listVersion !== version) return;

      this.listLoading = false;
      this.listError = error instanceof Error ? error.message : 'Could not load conversations.';
    }

    this.publish();
  }

  private createSession(key: string, conversation?: AssistantConversation): AssistantConversationSession {
    const session = new AssistantConversationSession(
      key,
      conversation,
      this.source,
      this.publish,
      this.confirmed,
      this.refreshConversations
    );
    this.sessions.set(key, session);
    return session;
  }

  private readonly refreshConversations = (): void => {
    if (this.active) void this.loadList(this.generation);
  };

  private readonly confirmed = (session: AssistantConversationSession, conversation: AssistantConversation): void => {
    if (!this.active || this.sessions.get(session.key) !== session) return;

    if (this.draftKey === session.key) this.draftKey = undefined;
    this.conversations = [conversation, ...this.conversations.filter(item => item.id !== conversation.id)];
    this.publish();
  };

  readonly select = (id: string): void => {
    if (!this.active) return;

    const conversation = this.conversations.find(item => item.id === id);
    if (!conversation) return;

    const session =
      Array.from(this.sessions.values()).find(item => item.snapshot.conversation?.id === id) ??
      this.createSession(`conversation:${id}`, conversation);

    this.selectedKey = session.key;
    this.publish();

    session.ensureHistory();
  };

  private selectDraft(): void {
    if (!this.draftKey) {
      this.draftKey = `draft:${++this.draftSequence}`;
      this.createSession(this.draftKey);
    }

    this.selectedKey = this.draftKey;
  }

  readonly newConversation = (): void => {
    if (!this.active || (this.draftKey && this.sessions.get(this.draftKey)!.snapshot.phase === 'creating')) return;

    this.selectDraft();
    this.publish();
  };

  readonly setDraft = (value: string): void => {
    if (!this.active) return;

    this.sessions.get(this.selectedKey)!.setDraft(value);
  };

  readonly send = (question?: string): Promise<void> => {
    if (!this.active) return Promise.resolve();

    return this.sessions.get(this.selectedKey)!.send(question);
  };

  readonly reload = (): Promise<void> => {
    if (!this.active) return Promise.resolve();

    return this.sessions.get(this.selectedKey)!.reload();
  };

  readonly loadEarlier = (): Promise<boolean> => {
    if (!this.active) return Promise.resolve(false);

    return this.sessions.get(this.selectedKey)!.loadEarlier();
  };

  readonly deleteConversation = async (id: string): Promise<AssistantDeletionResult> => {
    if (!this.active) return { status: 'ignored' };

    const conversation = this.conversations.find(item => item.id === id);
    if (!conversation) return { status: 'ignored' };

    const session =
      Array.from(this.sessions.values()).find(item => item.snapshot.conversation?.id === id) ??
      this.createSession(`conversation:${id}`, conversation);
    if (session.snapshot.deleting) return { status: 'ignored' };

    const generation = this.generation;
    const result = await session.delete();

    if (!this.active || this.generation !== generation || this.sessions.get(session.key) !== session) {
      return { status: 'ignored' };
    }
    if (result.status !== 'deleted') return result;

    this.deleted.add(id);
    this.sessions.delete(session.key);
    this.conversations = this.conversations.filter(item => item.id !== id);
    if (this.selectedKey === session.key) this.selectDraft();
    this.publish();
    return result;
  };

  private project(): AssistantPanel {
    const deletingConversationIds: string[] = [];
    this.sessions.forEach(session => {
      const entry = session.snapshot;
      if (entry.deleting && entry.conversation) deletingConversationIds.push(entry.conversation.id);
    });

    return {
      conversations: this.conversations,
      listLoading: this.listLoading,
      listError: this.listError,
      selectedKey: this.selectedKey,
      entry: this.sessions.get(this.selectedKey)!.snapshot,
      creatingConversation: !!this.draftKey && this.sessions.get(this.draftKey)!.snapshot.phase === 'creating',
      deletingConversationIds,
      select: this.select,
      newConversation: this.newConversation,
      setDraft: this.setDraft,
      send: this.send,
      reload: this.reload,
      loadEarlier: this.loadEarlier,
      deleteConversation: this.deleteConversation
    };
  }

  private readonly publish = (): void => {
    if (!this.active) return;

    this.snapshot = this.project();
    this.listeners.forEach(listener => listener());
  };

  dispose(): void {
    this.active = false;
    ++this.generation;

    this.sessions.forEach(session => session.dispose());
  }
}
