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
import type { ConversationListState } from '@/entities/assistant/model/conversationListState';
import { AssistantConversationSession } from './assistantConversation';
import type { AssistantDataSource } from './assistantDataSource';
import type { AssistantDeletionResult, AssistantPanel } from './assistantPanelState';

const INITIAL_CONVERSATION_LIST: ConversationListState = {
  conversations: [],
  hasLoaded: false,
  loading: true,
  error: undefined
};

interface ListRequest {
  blocksActivityRefresh: boolean;
  needsActivityRefresh: boolean;
  readonly completion: Promise<void>;
}

export class AssistantPanelModel {
  private conversationList = INITIAL_CONVERSATION_LIST;

  private selectedKey = 'draft:0';
  private draftKey: string | undefined = 'draft:0';
  private draftSequence = 0;

  private readonly sessions = new Map<string, AssistantConversationSession>();
  private readonly deleted = new Set<string>();

  private readonly listeners = new Set<() => void>();
  private active = false;
  private generation = 0;
  private activeListRequest?: ListRequest;
  private hasOpenedList = false;
  private closedWithRun = false;
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

  /** Discard view state except for conversations with a submitted run. */
  close(): boolean {
    let firstRun: string | undefined;
    this.sessions.forEach(session => {
      if (session.hasSubmittedRun) firstRun ??= session.key;
    });
    if (firstRun === undefined) return false;

    this.closedWithRun = true;
    this.activeListRequest = undefined;
    this.sessions.forEach((session, key) => {
      if (session.hasSubmittedRun) return;
      session.dispose();
      this.sessions.delete(key);
    });
    this.draftKey = undefined;
    if (!this.sessions.has(this.selectedKey)) this.selectedKey = firstRun;
    this.publish();
    return true;
  }

  reopen(): void {
    if (!this.closedWithRun) return;
    this.closedWithRun = false;
    this.conversationList = { ...this.conversationList, loading: true, error: undefined };
    this.publish();
    this.refreshListInBackground();
  }

  start(): void {
    if (this.active) return;

    this.active = true;
    ++this.generation;
    this.closedWithRun = false;

    this.sessions.forEach(session => session.dispose());
    this.sessions.clear();
    this.deleted.clear();

    this.conversationList = INITIAL_CONVERSATION_LIST;
    this.hasOpenedList = false;
    this.selectedKey = 'draft:0';
    this.draftKey = 'draft:0';
    this.draftSequence = 0;

    this.createSession(this.selectedKey);
    this.publish();

    void this.startListRequest();
  }

  private startListRequest(blocksActivityRefresh = false): Promise<void> {
    if (!this.active || this.closedWithRun) return Promise.resolve();

    // Publish the completion before starting the read, without deferring the read itself.
    const { promise: completion, resolve, reject } = Promise.withResolvers<void>();
    const request: ListRequest = { blocksActivityRefresh, needsActivityRefresh: false, completion };
    this.activeListRequest = request;
    void this.loadList(request, resolve, reject);
    return completion;
  }

  private async loadList(request: ListRequest, resolve: () => void, reject: (error: unknown) => void): Promise<void> {
    try {
      const listedAtStart = new Set(this.conversationList.conversations.map(conversation => conversation.id));
      try {
        const conversations = await this.source.api.listConversations();
        if (this.activeListRequest === request) this.applyListSnapshot(conversations, listedAtStart);
      } catch (error) {
        if (this.activeListRequest === request) {
          this.conversationList = {
            ...this.conversationList,
            loading: false,
            error: error instanceof Error ? error.message : 'Could not load conversations.'
          };
        }
      }

      if (this.activeListRequest === request) {
        this.activeListRequest = undefined;
        const generation = this.generation;
        this.publish();
        if (request.needsActivityRefresh && this.generation === generation && !this.activeListRequest) {
          void this.startListRequest();
        }
      }
      resolve();
    } catch (error) {
      reject(error);
    }
  }

  private applyListSnapshot(conversations: AssistantConversation[], listedAtStart: ReadonlySet<string>): void {
    const visible = conversations.filter(conversation => !this.deleted.has(conversation.id));
    const refreshed = new Map(visible.map(conversation => [conversation.id, conversation]));
    // A list snapshot can omit conversations confirmed locally after its request began.
    const createdDuringRequest = this.conversationList.conversations.filter(
      conversation => !listedAtStart.has(conversation.id) && !refreshed.has(conversation.id)
    );
    const nextConversations = [...createdDuringRequest, ...visible];
    nextConversations.sort((a, b) => (Date.parse(b.updatedAt) || 0) - (Date.parse(a.updatedAt) || 0));

    this.sessions.forEach(session => {
      const id = session.snapshot.conversation?.id;
      const conversation = id && refreshed.get(id);
      if (conversation) session.updateConversation(conversation);
    });

    this.conversationList = { conversations: nextConversations, hasLoaded: true, loading: false, error: undefined };
  }

  private createSession(key: string, conversation?: AssistantConversation): AssistantConversationSession {
    const session = new AssistantConversationSession(
      key,
      conversation,
      this.source,
      this.publish,
      this.confirmed,
      this.refreshListInBackground
    );
    this.sessions.set(key, session);
    return session;
  }

  private readonly refreshListInBackground = (): void => {
    if (this.activeListRequest?.blocksActivityRefresh) {
      // Apply the requested view refresh before reading the newer run activity.
      this.activeListRequest.needsActivityRefresh = true;
      return;
    }
    void this.startListRequest();
  };

  readonly openList = (): void => {
    if (!this.active || this.closedWithRun) return;

    // The first list entry uses the load already initiated by the sidebar.
    if (!this.hasOpenedList) {
      this.hasOpenedList = true;
      return;
    }
    void this.refreshListForView();
  };

  private async refreshListForView(): Promise<void> {
    const request = this.activeListRequest;
    if (request) request.blocksActivityRefresh = true;
    const completion = request ? request.completion : this.startListRequest(true);
    if (this.activeListRequest && !this.conversationList.loading) {
      this.conversationList = { ...this.conversationList, loading: true };
      this.publish();
    }
    return completion;
  }

  readonly retryList = (): Promise<void> => {
    if (!this.active || this.closedWithRun || this.conversationList.loading || !this.conversationList.error) {
      return Promise.resolve();
    }
    return this.refreshListForView();
  };

  private readonly confirmed = (session: AssistantConversationSession, conversation: AssistantConversation): void => {
    if (!this.active || this.sessions.get(session.key) !== session) return;

    if (this.draftKey === session.key) this.draftKey = undefined;
    this.conversationList = {
      ...this.conversationList,
      conversations: [conversation, ...this.conversationList.conversations.filter(item => item.id !== conversation.id)]
    };
    this.publish();
  };

  readonly select = (id: string): void => {
    if (!this.active) return;

    const conversation = this.conversationList.conversations.find(item => item.id === id);
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

    const conversation = this.conversationList.conversations.find(item => item.id === id);
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
    this.conversationList = {
      ...this.conversationList,
      conversations: this.conversationList.conversations.filter(item => item.id !== id)
    };
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
      conversationList: this.conversationList,
      selectedKey: this.selectedKey,
      entry: this.sessions.get(this.selectedKey)!.snapshot,
      creatingConversation: !!this.draftKey && this.sessions.get(this.draftKey)!.snapshot.phase === 'creating',
      deletingConversationIds,
      select: this.select,
      newConversation: this.newConversation,
      openList: this.openList,
      retryList: this.retryList,
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
    this.activeListRequest = undefined;

    this.sessions.forEach(session => session.dispose());
  }
}
