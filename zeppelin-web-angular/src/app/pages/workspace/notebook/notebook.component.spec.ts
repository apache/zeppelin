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

import { NEVER } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { ChangeDetectorRef } from '@angular/core';
import type { Title } from '@angular/platform-browser';
import type { ActivatedRoute, Router } from '@angular/router';
import { OP, type MessageReceiveDataTypeMap, type WebSocketMessage } from '@zeppelin/sdk';

import type {
  MessageService,
  NoteStatusService,
  NoteVarShareService,
  ReactFeatureService,
  SecurityService,
  ThemeService,
  TicketService
} from '@zeppelin/services';
import { NgZService } from '../../../services/ng-z.service';

vi.mock('./paragraph/paragraph.component', () => ({
  NotebookParagraphComponent: class NotebookParagraphComponent {}
}));

import { NotebookComponent } from './notebook.component';

const createComponent = () => {
  const messageService = {
    receive: vi.fn(() => NEVER),
    receiveEnvelope: vi.fn(() => NEVER),
    consumeLocalAddFocusMsgId: vi.fn(() => false),
    getInterpreterBindings: vi.fn()
  };

  const router = {
    navigate: vi.fn(() => Promise.resolve(true))
  } as unknown as Router;
  const ngZService = new NgZService();
  const routeParams: Record<string, string> = { noteId: 'note-b' };

  const component = new NotebookComponent(
    messageService as unknown as MessageService,
    ngZService,
    {
      snapshot: {
        params: routeParams
      }
    } as unknown as ActivatedRoute,
    {
      markForCheck: vi.fn()
    } as unknown as ChangeDetectorRef,
    {} as NoteStatusService,
    {} as NoteVarShareService,
    {} as TicketService,
    { getPermissions: () => NEVER } as unknown as SecurityService,
    router,
    { setTitle: vi.fn() } as unknown as Title,
    { updateMonacoTheme: vi.fn() } as unknown as ThemeService,
    {} as ReactFeatureService
  );

  return {
    component,
    router,
    messageService,
    ngZService,
    routeParams
  };
};

afterEach(() => {
  vi.restoreAllMocks();
});

describe('NotebookComponent', () => {
  it('ignores stale interpreter bindings replies', () => {
    const { component } = createComponent();

    const originalBindings = [{ id: 'existing', selected: true }];
    component.interpreterBindings = originalBindings as typeof component.interpreterBindings;

    const data: MessageReceiveDataTypeMap[OP.INTERPRETER_BINDINGS] = {
      noteId: 'note-a',
      interpreterBindings: [{ id: 'stale', selected: false }]
    };

    component.loadInterpreterBindings(data);

    expect(component.interpreterBindings).toBe(originalBindings);
  });

  it('ignores stale revision history replies', () => {
    const { component } = createComponent();

    const originalRevisions = [{ id: 'Head', message: 'Head' }];
    component.noteRevisions = originalRevisions;
    component.currentRevision = 'Head';

    const data: MessageReceiveDataTypeMap[OP.LIST_REVISION_HISTORY] = {
      noteId: 'note-a',
      revisionList: [{ id: 'rev-1', message: 'stale revision' }]
    };

    component.listRevisionHistory(data);

    expect(component.noteRevisions).toBe(originalRevisions);
    expect(component.currentRevision).toBe('Head');
  });

  it('ignores stale set note revision replies', () => {
    const { component, router } = createComponent();

    const data: MessageReceiveDataTypeMap[OP.SET_NOTE_REVISION] = {
      noteId: 'note-a',
      status: true
    };

    component.setNoteRevision(data);

    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('applies interpreter bindings for the active note', () => {
    const { component } = createComponent();

    const data: MessageReceiveDataTypeMap[OP.INTERPRETER_BINDINGS] = {
      noteId: 'note-b',
      interpreterBindings: [{ id: 'current', selected: true }]
    };

    component.loadInterpreterBindings(data);

    expect(component.interpreterBindings).toEqual([{ id: 'current', selected: true }]);
  });

  it('applies revision history for the active note', () => {
    const { component } = createComponent();

    const data: MessageReceiveDataTypeMap[OP.LIST_REVISION_HISTORY] = {
      noteId: 'note-b',
      revisionList: [{ id: 'rev-1', message: 'current revision' }]
    };

    component.listRevisionHistory(data);

    expect(component.noteRevisions).toEqual([
      { id: 'Head', message: 'Head' },
      { id: 'rev-1', message: 'current revision' }
    ]);

    expect(component.currentRevision).toBe('Head');
  });

  it('navigates after setting a revision for the active note', () => {
    const { component, router } = createComponent();

    const data: MessageReceiveDataTypeMap[OP.SET_NOTE_REVISION] = {
      noteId: 'note-b',
      status: true
    };

    component.setNoteRevision(data);

    expect(router.navigate).toHaveBeenCalledWith(['/notebook', 'note-b']);
  });

  it('uses the paragraph added envelope msgId for local focus', () => {
    const { component, messageService } = createComponent();

    component.note = {
      paragraphs: []
    } as typeof component.note;

    const message: WebSocketMessage<MessageReceiveDataTypeMap, OP.PARAGRAPH_ADDED> = {
      op: OP.PARAGRAPH_ADDED,
      msgId: 'local-add-msg',
      data: {
        index: 0,
        paragraph: {
          id: 'paragraph-1'
        } as MessageReceiveDataTypeMap[OP.PARAGRAPH_ADDED]['paragraph']
      }
    };

    component.addParagraph(message);

    expect(messageService.consumeLocalAddFocusMsgId).toHaveBeenCalledWith('local-add-msg');
  });

  it('clears models missing from a reconnected NOTE snapshot without disconnecting existing outputs', () => {
    const { component, ngZService } = createComponent();
    const note = {
      id: 'note-b',
      name: 'Notebook',
      config: {},
      paragraphs: [{ id: 'A', config: {} }]
    } as NonNullable<MessageReceiveDataTypeMap[OP.NOTE]['note']>;
    component.note = note;
    ngZService.registerParagraphBindings(note.id, 'A');
    const context: Record<string, unknown> = {};
    ngZService.bindParagraphContext('A', context);
    const binding = { noteId: note.id, paragraphId: null, interpreterGroupId: 'spark' };
    ngZService.setContextValue('controlValue', 'deleted-while-disconnected', 'A', false, binding);

    component.getNote({ op: OP.NOTE, data: { note } });
    expect(context.controlValue).toBe('deleted-while-disconnected');

    component.getNote({ op: OP.NOTE, msgId: 'reload-read', data: { note } });

    expect(context.controlValue).toBeUndefined();
    ngZService.setContextValue('currentValue', 'replayed', 'A', false, binding);
    expect(context.currentValue).toBe('replayed');
    expect(context.controlValue).toBeUndefined();
  });

  it('cleans up removed paragraph state when a revision replaces the notebook snapshot', () => {
    const { component, ngZService } = createComponent();
    component.note = {
      id: 'note-b',
      config: {},
      paragraphs: [{ id: 'removed', config: {} }]
    } as NonNullable<typeof component.note>;
    ngZService.registerParagraphBindings('note-b', 'removed');
    ngZService.setContextValue('localValue', 'old-paragraph', 'removed');

    component.getNoteRevision({
      noteId: 'note-b',
      revisionId: 'r1',
      note: { ...component.note, paragraphs: [] }
    });

    ngZService.registerParagraphBindings('note-b', 'removed');
    const context: Record<string, unknown> = {};
    ngZService.bindParagraphContext('removed', context);
    expect(context.localValue).toBeUndefined();
  });

  it('releases bindings outside the visible snapshot when switching a single-paragraph route', () => {
    const { component, ngZService, routeParams } = createComponent();
    const note = {
      id: 'note-b',
      name: 'Notebook',
      config: {},
      paragraphs: [
        { id: 'A', config: {} },
        { id: 'B', config: {} }
      ]
    } as NonNullable<typeof component.note>;
    component.note = { ...note, paragraphs: [note.paragraphs[0]] };
    ngZService.registerParagraphBindings(note.id, 'A');
    ngZService.setContextValue('privateValue', 'previous-output', 'A');
    routeParams.paragraphId = 'B';

    component.getNote({ op: OP.NOTE, msgId: 'single-paragraph-read', data: { note } });

    expect(component.note?.paragraphs.map(paragraph => paragraph.id)).toEqual(['B']);
    const staleOutput: Record<string, unknown> = {};
    ngZService.bindParagraphContext('A', staleOutput);
    ngZService.setContextValue('privateValue', 'late-old-output', 'A');
    expect(staleOutput.privateValue).toBeUndefined();
  });

  it('preserves bindings while viewing a revision and reconciles them only on a live read', () => {
    const { component, ngZService } = createComponent();
    const note = {
      id: 'note-b',
      name: 'Notebook',
      config: {},
      paragraphs: [{ id: 'A', config: {} }]
    } as NonNullable<typeof component.note>;
    component.note = note;
    ngZService.registerParagraphBindings(note.id, 'A');
    const context: Record<string, unknown> = {};
    const release = ngZService.bindParagraphContext('A', context);
    const binding = { noteId: note.id, paragraphId: null, interpreterGroupId: 'spark' };
    ngZService.setContextValue('controlValue', 'before-revision', 'A', false, binding);
    ngZService.setContextValue('localValue', 'client-local', 'A');

    component.getNoteRevision({ noteId: note.id, revisionId: 'r1', note });

    expect(context.controlValue).toBe('before-revision');
    expect(context.localValue).toBe('client-local');
    release();
    const recreatedOutput: Record<string, unknown> = {};
    ngZService.bindParagraphContext('A', recreatedOutput);
    expect(recreatedOutput.controlValue).toBe('before-revision');
    expect(recreatedOutput.localValue).toBe('client-local');

    component.getNote({ op: OP.NOTE, msgId: 'return-to-live', data: { note } });

    expect(recreatedOutput.controlValue).toBeUndefined();
    expect(recreatedOutput.localValue).toBeUndefined();
    ngZService.setContextValue('controlValue', 'live-model', 'A', false, binding);
    expect(recreatedOutput.controlValue).toBe('live-model');
  });
});
