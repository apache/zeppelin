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

import type { AssistantConversation, AssistantMessage } from '@/entities/assistant/model/assistantContract';
import type { AssistantAnswerErrorKind, AssistantAnswerView } from '@/entities/assistant/ui/answerView';

export type AssistantPanelPhase = 'idle' | 'creating' | 'pending' | 'running' | 'unknown';
export type AssistantRecovery = 'send' | 'reload';

export interface AssistantPanelFailure {
  message: string;
  kind: AssistantAnswerErrorKind;
  recovery?: AssistantRecovery;
}

export type AssistantDeletionResult =
  { status: 'deleted' } | { status: 'ignored' } | { status: 'failed'; message: string };

export interface AssistantPanelHistory {
  messages: AssistantMessage[];
  /** Message IDs mapped to stable rendered-row keys. */
  messageKeys: Map<string, string>;
  /** Display states retained until their answers are reconciled with history. */
  localAnswerViews: Map<string, AssistantAnswerView>;

  cursor: string | null;
  loaded: boolean;
  loading: boolean;
  loadingEarlier: boolean;

  answer?: AssistantAnswerView;
  answerKey?: string;
  answerRecovery?: AssistantRecovery;
}

export interface AssistantPanelEntry extends AssistantPanelHistory {
  /** Stable session key, including after conversation creation. */
  key: string;
  conversation?: AssistantConversation;
  draft: string;

  phase: AssistantPanelPhase;
  /** Failure displayed outside an answer. */
  failure?: AssistantPanelFailure;
  uncertainty?: string;
  submittedQuestion?: string;

  deleting: boolean;
}

export interface AssistantPanelSnapshot {
  conversations: AssistantConversation[];
  listLoading: boolean;
  listError?: string;

  selectedKey: string;
  entry: AssistantPanelEntry;

  deletingConversationIds: string[];
  /** Whether the unconfirmed draft is being created, even when another conversation is selected. */
  creatingConversation: boolean;
}

export interface AssistantPanelActions {
  select(id: string): void;
  newConversation(): void;

  setDraft(value: string): void;
  send(question?: string): Promise<void>;

  reload(): Promise<void>;
  /** Resolves to true only when an earlier page is applied to the current history. */
  loadEarlier(): Promise<boolean>;

  deleteConversation(id: string): Promise<AssistantDeletionResult>;
}

export interface AssistantPanel extends AssistantPanelSnapshot, AssistantPanelActions {}
