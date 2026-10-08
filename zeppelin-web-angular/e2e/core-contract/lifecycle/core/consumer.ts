/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { diff_match_patch as DiffMatchPatch } from 'diff-match-patch';
import type { Note, NoteUpdated } from '../../../../projects/zeppelin-sdk/src/interfaces/message-notebook.interface';
import type { RouteContext } from '../fixture.mjs';
import { createNotebookCore } from './runtime/notebook-core.ts';
import type {
  NotebookCoreSnapshot,
  NotebookParagraphStatus,
  NotebookLookAndFeel
} from './runtime/host-remote-contract';

type CapturedNote = NonNullable<Note['note']>;
type ReceivedMessage =
  | { op: 'NOTE'; data: Note }
  | { op: 'PATCH_PARAGRAPH'; data: { paragraphId: string; patch: string } }
  | { op: 'NOTE_UPDATED'; data: NoteUpdated }
  | { op: 'COLLABORATIVE_MODE_STATUS'; data: { status: boolean; users: string[] } };

export interface CoreObservation {
  snapshot: NotebookCoreSnapshot;
  events: ReadonlyArray<{ op: string; snapshot: NotebookCoreSnapshot }>;
  recoveryRequired: boolean;
}

const statuses = new Set<string>(['UNKNOWN', 'READY', 'PENDING', 'RUNNING', 'FINISHED', 'ERROR', 'ABORT']);
const paragraphStatus = (status: string): NotebookParagraphStatus => {
  if (!statuses.has(status)) throw new Error(`Unsupported captured paragraph status: ${status}`);

  return status as NotebookParagraphStatus;
};

const lookAndFeel = (value: string | undefined): NotebookLookAndFeel =>
  value === 'simple' || value === 'report' ? value : 'default';

const paragraphLanguage = (paragraph: CapturedNote['paragraphs'][number]) => {
  if (paragraph.config?.editorSetting?.language) return paragraph.config.editorSetting.language;

  const directive = paragraph.text?.trimStart().match(/^%(\w+)/)?.[1];
  return directive === 'md' ? 'markdown' : directive;
};

/** Maps captured wire messages to Core events. Only the runtime owns notebook state. */
export class LifecycleCoreConsumer {
  private readonly core = createNotebookCore();
  private readonly patcher = new DiffMatchPatch();
  private readonly events: { op: string; snapshot: NotebookCoreSnapshot }[] = [];
  private recoveryRequired = false;

  enterContext(context: RouteContext): void {
    if (context.state !== 'active' || context.revisionId !== null) {
      throw new Error('The collaboration consumer requires an active live route');
    }

    this.core.apply({ type: 'route-changed', noteId: context.noteId, revisionId: context.revisionId });
    this.core.apply({ type: 'load-started' });
  }

  receive(payload: string): void {
    const message = JSON.parse(payload) as ReceivedMessage;
    switch (message.op) {
      case 'NOTE':
        if (!message.data.note) throw new Error('The collaboration consumer requires a full NOTE');
        this.acceptNote(message.data.note);
        break;
      case 'PATCH_PARAGRAPH': {
        const { paragraphId, patch } = message.data;
        const text = this.applyPatch(paragraphId, patch);
        if (text === undefined) {
          this.recoveryRequired = true;
        } else {
          this.core.apply({ type: 'paragraph-updated', paragraphId, text, source: 'collaboration' });
        }
        break;
      }
      case 'NOTE_UPDATED':
        this.core.apply({ type: 'note-updated', title: message.data.name });
        this.core.apply({ type: 'look-and-feel-updated', lookAndFeel: lookAndFeel(message.data.config.looknfeel) });
        this.core.apply({
          type: 'personalized-mode-updated',
          personalizedMode: message.data.config.personalizedMode === 'true'
        });
        break;
      case 'COLLABORATIVE_MODE_STATUS':
        this.core.apply({ type: 'collaboration-updated', users: message.data.status ? message.data.users : null });
        break;
      default:
        return;
    }

    this.events.push({ op: message.op, snapshot: this.core.port.getSnapshot() });
  }

  beforeSend(payload: string): void {
    const message = JSON.parse(payload) as { op: string; data: { id: string; patch: string } };
    if (message.op !== 'PATCH_PARAGRAPH') return;

    const text = this.applyPatch(message.data.id, message.data.patch);
    if (text === undefined) throw new Error('Local captured edit cannot be applied');

    this.core.apply({ type: 'paragraph-updated', paragraphId: message.data.id, text, source: 'local' });
  }

  observe(): CoreObservation {
    return {
      snapshot: this.core.port.getSnapshot(),
      events: [...this.events],
      recoveryRequired: this.recoveryRequired
    };
  }

  private acceptNote(note: CapturedNote): void {
    const accepted = this.core.apply({
      type: 'note-loaded',
      noteId: note.id,
      revisionId: null,
      title: note.name,
      noteForms: note.noteForms,
      noteParams: note.noteParams,
      scheduler: note.config.isZeppelinNotebookCronEnable
        ? { cron: note.config.cron, releaseResource: Boolean(note.config.releaseresource) }
        : undefined,
      lookAndFeel: lookAndFeel(note.config.looknfeel),
      personalizedMode: note.config.personalizedMode === 'true',
      paragraphs: note.paragraphs.map(paragraph => ({
        id: paragraph.id,
        text: paragraph.text ?? '',
        status: paragraphStatus(paragraph.status),
        progress: paragraph.progress ?? 0,
        language: paragraphLanguage(paragraph),
        results: paragraph.results?.msg,
        resultConfigs: paragraph.config?.results
      }))
    });
    if (!accepted) throw new Error('Core rejected the associated NOTE');

    this.recoveryRequired = false;
  }

  private applyPatch(paragraphId: string, patch: string): string | undefined {
    const paragraph = this.core.port.getSnapshot().paragraphs.find(candidate => candidate.id === paragraphId);
    if (!paragraph) return undefined;

    try {
      const [text, applied] = this.patcher.patch_apply(this.patcher.patch_fromText(patch), paragraph.text);
      return applied.every(Boolean) ? text : undefined;
    } catch {
      return undefined;
    }
  }
}
