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
  NgZService,
  NoteStatusService,
  NoteVarShareService,
  ReactFeatureService,
  SecurityService,
  ThemeService,
  TicketService
} from '@zeppelin/services';

vi.mock('./paragraph/paragraph.component', () => ({
  NotebookParagraphComponent: class NotebookParagraphComponent {}
}));

import { NotebookComponent } from './notebook.component';

const createComponent = () => {
  const messageService = {
    receive: vi.fn(() => NEVER),
    receiveEnvelope: vi.fn(() => NEVER),
    consumeLocalAddFocusMsgId: vi.fn(() => false)
  };

  const router = {
    navigate: vi.fn(() => Promise.resolve(true))
  } as unknown as Router;

  const component = new NotebookComponent(
    messageService as unknown as MessageService,
    {} as NgZService,
    {
      snapshot: {
        params: {
          noteId: 'note-b'
        }
      }
    } as unknown as ActivatedRoute,
    {
      markForCheck: vi.fn()
    } as unknown as ChangeDetectorRef,
    {} as NoteStatusService,
    {} as NoteVarShareService,
    {} as TicketService,
    {} as SecurityService,
    router,
    {} as Title,
    {} as ThemeService,
    {} as ReactFeatureService
  );

  return {
    component,
    router,
    messageService
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
});
