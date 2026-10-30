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

import { ElementRef, QueryList } from '@angular/core';
import { AngularObjectRemove, AngularObjectUpdate, Note, OP, ParagraphItem } from '@zeppelin/sdk';
import { EMPTY, Subject } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('@zeppelin/services', () => ({
  HeliumService: class {},
  MessageService: class {},
  NgZService: class {},
  NoteStatusService: class {},
  NoteVarShareService: class {},
  ShortcutService: class {}
}));
// The hoisted module mock must load the real listener owner before unrelated view modules.
vi.mock('@zeppelin/core', async () => ({
  ...(await import('../core/paragraph-base/paragraph-base')),
  ...(await import('../core/message-listener/message-listener'))
}));
vi.mock('../pages/workspace/share/result/result.component', () => ({
  NotebookParagraphResultComponent: class {}
}));
vi.mock('../pages/workspace/notebook/paragraph/code-editor/code-editor.component', () => ({
  NotebookParagraphCodeEditorComponent: class {}
}));

import { NotebookParagraphComponent } from '../pages/workspace/notebook/paragraph/paragraph.component';
import { NgZService } from './ng-z.service';

const disposables: Array<() => void> = [];
afterEach(() => disposables.splice(0).forEach(dispose => dispose()));

const setup = () => {
  const service = new NgZService();
  const updates = new Subject<AngularObjectUpdate>();
  const removals = new Subject<AngularObjectRemove>();
  const message = {
    receive: (op: OP) =>
      op === OP.ANGULAR_OBJECT_UPDATE ? updates : op === OP.ANGULAR_OBJECT_REMOVE ? removals : EMPTY,
    receiveEnvelope: () => EMPTY,
    angularObjectUpdate: vi.fn(),
    angularObjectClientBind: vi.fn(),
    angularObjectClientUnbind: vi.fn()
  };
  const createParagraph = (id: string, noteId = 'note', revisionView = false) => {
    const component = new NotebookParagraphComponent(
      message as never,
      {} as never,
      new ElementRef(document.createElement('div')),
      { set: vi.fn() } as never,
      {} as never,
      { forkByElement: () => ({ bindShortcut: () => EMPTY }) } as never,
      { isParagraphRunning: () => false, isEntireNoteRunning: () => false } as never,
      { markForCheck: vi.fn() } as never,
      service
    );
    component.paragraph = {
      id,
      text: '',
      status: 'FINISHED',
      config: {},
      settings: { params: {}, forms: {} }
    } as ParagraphItem;
    component.note = { id: noteId, paragraphs: [component.paragraph], config: {} } as NonNullable<Note['note']>;
    component.revisionView = revisionView;
    component.notebookParagraphResultComponents = new QueryList();
    component.ngOnInit();
    disposables.push(() => component.ngOnDestroy());
    return component;
  };
  const a = createParagraph('A');
  const b = createParagraph('B');
  disposables.push(() => service.ngOnDestroy());
  const update = (value: unknown, paragraphId?: string, group = 'spark') =>
    updates.next({
      noteId: 'note',
      paragraphId,
      interpreterGroupId: group,
      angularObject: { name: 'controlValue', object: value, noteId: 'note', paragraphId }
    });
  const bind = (paragraphId: string) => {
    const context: Record<string, unknown> = {};
    const release = service.bindParagraphContext(paragraphId, context);
    return { context, release };
  };
  return { service, updates, removals, message, createParagraph, a, b, update, bind };
};

describe('Paragraph Angular object binding', () => {
  it('retains initial values received by paragraph listeners until outputs are created', () => {
    const { update, bind } = setup();
    update('initial');
    update('latest');
    expect(bind('A').context.controlValue).toBe('latest');
    expect(bind('B').context.controlValue).toBe('latest');
  });

  it('updates multiple outputs and hydrates replacements without retaining destroyed contexts', () => {
    const { update, bind } = setup();
    const first = bind('A');
    const second = bind('A');
    update('initial');
    first.release();
    update('next');
    expect(first.context.controlValue).toBe('initial');
    expect(second.context.controlValue).toBe('next');
    expect(bind('A').context.controlValue).toBe('next');
  });

  it('keeps paragraph overrides isolated from notebook updates and restores fallback after removal', () => {
    const { update, bind, removals } = setup();
    update('private', 'A');
    update('shared');
    const a = bind('A').context;
    const b = bind('B').context;
    update('next-shared');
    expect(a.controlValue).toBe('private');
    expect(b.controlValue).toBe('next-shared');
    removals.next({ noteId: 'note', paragraphId: 'A', name: 'controlValue', interpreterGroupId: 'spark' });
    expect(a.controlValue).toBe('next-shared');
  });

  it.each(['top-level', 'nested'] as const)('handles %s notebook removals without resurrecting values', shape => {
    const { update, removals, bind } = setup();
    update('initial');
    const a = bind('A');
    const b = bind('B');
    removals.next(
      shape === 'top-level'
        ? { noteId: 'note', name: 'controlValue', interpreterGroupId: 'spark' }
        : {
            noteId: 'note',
            interpreterGroupId: 'spark',
            angularObject: { name: 'controlValue', object: 'initial', noteId: 'note' }
          }
    );
    expect(a.context.controlValue).toBeUndefined();
    expect(b.context.controlValue).toBeUndefined();
    a.release();
    expect(bind('A').context.controlValue).toBeUndefined();
  });

  it('retains deletion received before output creation', () => {
    const { update, removals, bind } = setup();
    update('initial');
    removals.next({ noteId: 'note', name: 'controlValue', interpreterGroupId: 'spark' });
    expect(bind('A').context.controlValue).toBeUndefined();
  });

  it('rejects foreign-note and different-paragraph messages in the paragraph listener', () => {
    const { updates, removals, update, bind } = setup();
    update('private', 'A');
    const a = bind('A').context;
    updates.next({
      noteId: 'other',
      paragraphId: 'A',
      interpreterGroupId: 'spark',
      angularObject: { name: 'controlValue', object: 'foreign', noteId: 'other', paragraphId: 'A' }
    });
    removals.next({ noteId: 'other', paragraphId: 'A', name: 'controlValue' });
    update('other-paragraph', 'B');
    removals.next({ noteId: 'note', paragraphId: 'B', name: 'controlValue', interpreterGroupId: 'spark' });
    expect(a.controlValue).toBe('private');
  });

  it('uses the existing paragraph outbound subscriber to update the original notebook model only once', () => {
    const { update, bind, service, message } = setup();
    update('initial', undefined, 'original-group');
    const a = bind('A').context;
    const b = bind('B').context;
    service.setContextValue('controlValue', false, 'A');
    expect(a.controlValue).toBe(false);
    expect(b.controlValue).toBe(false);
    expect(message.angularObjectUpdate).toHaveBeenCalledExactlyOnceWith(
      'note',
      null,
      'controlValue',
      false,
      'original-group'
    );
    expect(message.angularObjectClientBind).not.toHaveBeenCalled();
    update('remote', undefined, 'original-group');
    expect(a.controlValue).toBe('remote');
    expect(message.angularObjectUpdate).toHaveBeenCalledTimes(1);
  });

  it('preserves paragraph scope and interpreter group for edits without updating the notebook fallback', () => {
    const { update, bind, service, message } = setup();
    update('shared', undefined, 'shared-group');
    update('private', 'A', 'private-group');
    const a = bind('A').context;
    const b = bind('B').context;
    service.setContextValue('controlValue', 0, 'A');
    expect(a.controlValue).toBe(0);
    expect(b.controlValue).toBe('shared');
    expect(message.angularObjectUpdate).toHaveBeenCalledExactlyOnceWith(
      'note',
      'A',
      'controlValue',
      0,
      'private-group'
    );
  });

  it('keeps a client-created paragraph value ahead of later shared updates and edits its original scope', () => {
    const { update, bind, service, message } = setup();
    service.setContextValue('controlValue', 'client-private', 'A');
    update('shared');
    const a = bind('A').context;
    expect(a.controlValue).toBe('client-private');
    expect(bind('B').context.controlValue).toBe('shared');
    service.setContextValue('controlValue', 'edited-private', 'A');
    expect(a.controlValue).toBe('edited-private');
    expect(message.angularObjectClientBind).toHaveBeenLastCalledWith('note', 'controlValue', 'edited-private', 'A');
    expect(message.angularObjectUpdate).not.toHaveBeenCalled();
  });

  it('removes a client-created value even when another scope shares its name', () => {
    const { service, update, bind, removals } = setup();
    service.setContextValue('controlValue', 'client-private', 'A');
    update('shared');
    const a = bind('A').context;
    removals.next({ noteId: 'note', paragraphId: 'A', name: 'controlValue', interpreterGroupId: 'spark' });
    expect(a.controlValue).toBe('shared');
    removals.next({ noteId: 'note', name: 'controlValue', interpreterGroupId: 'spark' });
    expect(a.controlValue).toBeUndefined();
    expect(bind('A').context.controlValue).toBeUndefined();
  });

  it('reconciles a server snapshot without losing live output registrations or retaining removed values', () => {
    const { service, update, bind } = setup();
    update('old-server-value');
    const a = bind('A').context;
    service.clearNoteContextValues('note');
    expect(a.controlValue).toBeUndefined();
    expect(bind('B').context.controlValue).toBeUndefined();
    update('new-server-value');
    expect(a.controlValue).toBe('new-server-value');
  });

  it('binds existing shared values into a newly registered paragraph without a new server event', () => {
    const { update, createParagraph, bind, service } = setup();
    update('shared');
    createParagraph('C');
    expect(bind('C').context.controlValue).toBe('shared');
    service.removeParagraphBindings('C');
    expect(bind('A').context.controlValue).toBe('shared');
  });

  it.each([undefined, null])('treats a top-level noteId=%j removal as global, not notebook scoped', noteId => {
    const { updates, removals, update, bind } = setup();
    updates.next({
      noteId: 'note',
      interpreterGroupId: 'spark',
      angularObject: { name: 'controlValue', object: 'global' }
    });
    update('notebook');
    const a = bind('A').context;
    removals.next({ noteId, name: 'controlValue', interpreterGroupId: 'spark' });
    expect(a.controlValue).toBe('notebook');
    removals.next({ noteId: 'note', name: 'controlValue', interpreterGroupId: 'spark' });
    expect(a.controlValue).toBeUndefined();
  });

  it('preserves the original identity even when the initial bound value is undefined', () => {
    const { service, update, bind, message } = setup();
    update(undefined);
    expect(bind('A').context.controlValue).toBeUndefined();
    service.setContextValue('controlValue', 'edited', 'A');
    expect(message.angularObjectUpdate).toHaveBeenCalledExactlyOnceWith(
      'note',
      null,
      'controlValue',
      'edited',
      'spark'
    );
    expect(message.angularObjectClientBind).not.toHaveBeenCalled();
  });

  it('supports local z.set bindings in revision paragraphs without depending on NOTE messages', () => {
    const { createParagraph, service, bind, message } = setup();
    createParagraph('revision', 'revision-note', true);
    service.setContextValue('localValue', 'revision-input', 'revision');
    expect(bind('revision').context.localValue).toBe('revision-input');
    expect(message.angularObjectClientBind).toHaveBeenCalledExactlyOnceWith(
      'revision-note',
      'localValue',
      'revision-input',
      'revision'
    );
  });

  it('removes only the named interpreter-group binding and keeps the surviving model editable', () => {
    const { update, removals, bind, service, message } = setup();
    update('first-model', 'A', 'group-one');
    update('second-model', 'A', 'group-two');
    const context = bind('A').context;

    removals.next({ noteId: 'note', paragraphId: 'A', name: 'controlValue', interpreterGroupId: 'group-one' });
    expect(context.controlValue).toBe('second-model');
    service.setContextValue('controlValue', 'edited-survivor', 'A');
    expect(message.angularObjectUpdate).toHaveBeenCalledExactlyOnceWith(
      'note',
      'A',
      'controlValue',
      'edited-survivor',
      'group-two'
    );

    removals.next({ noteId: 'note', paragraphId: 'A', name: 'controlValue' });
    expect(context.controlValue).toBeUndefined();
    expect(bind('A').context.controlValue).toBeUndefined();
  });

  it('does not guess the interpreter group when a removal matches multiple model identities', () => {
    const { update, removals, bind } = setup();
    update('first-model', 'A', 'group-one');
    update('second-model', 'A', 'group-two');
    const context = bind('A').context;
    const originalValue = context.controlValue;

    removals.next({ noteId: 'note', paragraphId: 'A', name: 'controlValue' });
    expect(context.controlValue).toBe(originalValue);
    removals.next({ noteId: 'note', paragraphId: 'A', name: 'controlValue', interpreterGroupId: 'group-one' });
    expect(context.controlValue).toBe('second-model');
    removals.next({ noteId: 'note', paragraphId: 'A', name: 'controlValue', interpreterGroupId: 'group-two' });
    expect(context.controlValue).toBeUndefined();
  });

  it('isolates retained values, clearing, and outbound edits between notebooks', () => {
    const { createParagraph, update, updates, bind, service, message } = setup();
    createParagraph('other-paragraph', 'other-note');
    update('first-notebook');
    updates.next({
      noteId: 'other-note',
      interpreterGroupId: 'other-group',
      angularObject: { name: 'controlValue', object: 'second-notebook', noteId: 'other-note' }
    });
    const first = bind('A').context;
    const second = bind('other-paragraph').context;
    expect(first.controlValue).toBe('first-notebook');
    expect(second.controlValue).toBe('second-notebook');

    service.clearNoteContextValues('note');
    service.setContextValue('controlValue', 'edited-second', 'other-paragraph');
    expect(first.controlValue).toBeUndefined();
    expect(second.controlValue).toBe('edited-second');
    expect(message.angularObjectUpdate).toHaveBeenCalledExactlyOnceWith(
      'other-note',
      null,
      'controlValue',
      'edited-second',
      'other-group'
    );
  });
});
