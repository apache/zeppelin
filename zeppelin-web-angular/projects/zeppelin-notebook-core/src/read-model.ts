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

import type { NotebookCoreSnapshot } from './host-remote-contract';

// Inputs: the read payload fields the Core consumes. The Core cannot import SDK types, so these
// mirror the wire shapes structurally; the host converts NOTE, NOTE_REVISION and the permissions
// REST response into them. Fields the Core does not read are deliberately absent.

export type NotebookParagraphStatus = 'UNKNOWN' | 'READY' | 'PENDING' | 'RUNNING' | 'FINISHED' | 'ERROR' | 'ABORT';

export type NotebookResultCode = 'SUCCESS' | 'INCOMPLETE' | 'ERROR' | 'KEEP_PREVIOUS_RESULT';

export interface NotebookResultMessageInput {
  readonly type: string;
  readonly data: string;
}

export interface NotebookParagraphInput {
  readonly id: string;
  readonly title?: string;
  readonly text?: string;
  readonly status: NotebookParagraphStatus;
  /** Persisted visualization settings, keyed by result index. The Core keeps `graph` uninterpreted. */
  readonly config?: Readonly<{ results?: Readonly<{ [resultIndex: string]: Readonly<{ graph?: unknown }> }> }>;
  readonly results?: Readonly<{ code?: NotebookResultCode; msg?: readonly NotebookResultMessageInput[] }>;
}

/** The `note` of a full NOTE message. */
export interface NotebookNoteInput {
  readonly id: string;
  readonly name: string;
  readonly path?: string;
  readonly paragraphs: readonly NotebookParagraphInput[];
}

/** A full NOTE_REVISION message. */
export interface NotebookRevisionInput {
  readonly noteId: string;
  readonly revisionId: string;
  readonly note?: NotebookNoteInput;
}

/** The note permissions REST response. */
export interface NotebookAclInput {
  readonly owners: readonly string[];
  readonly readers: readonly string[];
  readonly runners: readonly string[];
  readonly writers: readonly string[];
}

// Read state: what the Core publishes. Every snapshot is deeply frozen.

/** A live note and a historical revision of it are different targets. */
export type NotebookReadTarget =
  Readonly<{ kind: 'note'; noteId: string }> | Readonly<{ kind: 'revision'; noteId: string; revisionId: string }>;

/**
 * `not-found` and `access-denied` are reported only when the host can tell them apart;
 * anything else it cannot load is `failed`.
 */
export type NotebookReadStatus = 'idle' | 'loading' | 'ready' | 'not-found' | 'access-denied' | 'failed' | 'disposed';

export type NotebookReadFailure = 'not-found' | 'access-denied' | 'failed';

export type NotebookAclStatus = 'unknown' | 'loading' | 'ready' | 'access-denied' | 'failed';

export type NotebookResultMessage = Readonly<{ type: string; data: string }>;

export type NotebookReadParagraph = Readonly<{
  id: string;
  title: string | null;
  text: string;
  status: NotebookParagraphStatus;
  resultCode: NotebookResultCode | null;
  resultMessages: readonly NotebookResultMessage[];
  /** Persisted `graph` settings by result index, kept as the server sent them. */
  chartConfigs: Readonly<{ [resultIndex: string]: unknown }>;
}>;

export type NotebookReadNote = Readonly<{
  id: string;
  name: string;
  path: string | null;
  paragraphIds: readonly string[];
  paragraphsById: Readonly<{ [paragraphId: string]: NotebookReadParagraph }>;
}>;

export type NotebookReadAcl = Readonly<{
  status: NotebookAclStatus;
  owners: readonly string[];
  readers: readonly string[];
  runners: readonly string[];
  writers: readonly string[];
}>;

/**
 * Extends the NotebookPort snapshot, so a read store can be handed to a remote as its port.
 * Without a target, `noteId` is empty and `revisionId` is null.
 */
export type NotebookReadSnapshot = NotebookCoreSnapshot &
  Readonly<{
    target: NotebookReadTarget | null;
    status: NotebookReadStatus;
    note: NotebookReadNote | null;
    acl: NotebookReadAcl;
  }>;
