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
    consumePendingNoteRequest: vi.fn(),
    receive: vi.fn(() => NEVER),
    receiveEnvelope: vi.fn(() => NEVER)
  } as unknown as MessageService;

  const router = {
    navigate: vi.fn(() => Promise.resolve(true))
  } as unknown as Router;

  const component = new NotebookComponent(
    messageService,
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
    messageService,
    router
  };
};

afterEach(() => {
  vi.restoreAllMocks();
});

describe('NotebookComponent', () => {
  it('ignores stale interpreter bindings replies', () => {
    const { component, messageService } = createComponent();

    vi.mocked(messageService.consumePendingNoteRequest).mockReturnValue(false);

    const originalBindings = [{ id: 'existing', selected: true }];
    component.interpreterBindings = originalBindings as typeof component.interpreterBindings;

    const message: WebSocketMessage<MessageReceiveDataTypeMap, OP.INTERPRETER_BINDINGS> = {
      op: OP.INTERPRETER_BINDINGS,
      msgId: 'msg-a',
      data: {
        interpreterBindings: [{ id: 'stale', selected: false }]
      }
    };

    component.loadInterpreterBindings(message);

    expect(messageService.consumePendingNoteRequest).toHaveBeenCalledWith('msg-a', 'note-b');
    expect(component.interpreterBindings).toBe(originalBindings);
  });

  it('ignores stale revision history replies', () => {
    const { component, messageService } = createComponent();

    vi.mocked(messageService.consumePendingNoteRequest).mockReturnValue(false);

    const originalRevisions = [{ id: 'Head', message: 'Head' }];
    component.noteRevisions = originalRevisions;
    component.currentRevision = 'Head';

    const message: WebSocketMessage<MessageReceiveDataTypeMap, OP.LIST_REVISION_HISTORY> = {
      op: OP.LIST_REVISION_HISTORY,
      msgId: 'msg-a',
      data: {
        revisionList: [{ id: 'rev-1', message: 'stale revision' }]
      }
    };
    component.listRevisionHistory(message);

    expect(messageService.consumePendingNoteRequest).toHaveBeenCalledWith('msg-a', 'note-b');
    expect(component.noteRevisions).toBe(originalRevisions);
    expect(component.currentRevision).toBe('Head');
  });

  it('ignores stale set note revision replies', () => {
    const { component, messageService, router } = createComponent();

    vi.mocked(messageService.consumePendingNoteRequest).mockReturnValue(false);

    const message: WebSocketMessage<MessageReceiveDataTypeMap, OP.SET_NOTE_REVISION> = {
      op: OP.SET_NOTE_REVISION,
      msgId: 'msg-a',
      data: undefined
    };

    component.setNoteRevision(message);

    expect(messageService.consumePendingNoteRequest).toHaveBeenCalledWith('msg-a', 'note-b');
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('applies interpreter bindings for the active note', () => {
    const { component, messageService } = createComponent();

    vi.mocked(messageService.consumePendingNoteRequest).mockReturnValue(true);

    const message: WebSocketMessage<MessageReceiveDataTypeMap, OP.INTERPRETER_BINDINGS> = {
      op: OP.INTERPRETER_BINDINGS,
      msgId: 'msg-b',
      data: {
        interpreterBindings: [{ id: 'current', selected: true }]
      }
    };

    component.loadInterpreterBindings(message);

    expect(messageService.consumePendingNoteRequest).toHaveBeenCalledWith('msg-b', 'note-b');
    expect(component.interpreterBindings).toEqual([{ id: 'current', selected: true }]);
  });
});
