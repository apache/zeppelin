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

import { ChangeDetectorRef, SimpleChange } from '@angular/core';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzModalService } from 'ng-zorro-antd/modal';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { Permissions } from '@zeppelin/interfaces';
import { SecurityService, TicketService } from '@zeppelin/services';

import { NotebookPermissionsComponent } from './permissions.component';

const savedPermissions = (): Permissions => ({
  owners: ['alice'],
  writers: ['bob'],
  runners: ['carol'],
  readers: ['dave']
});

const createComponent = (permissions: Permissions) => {
  const setPermissions = vi.fn(() => of(undefined));
  const component = new NotebookPermissionsComponent(
    { setPermissions } as unknown as SecurityService,
    { markForCheck: vi.fn() } as unknown as ChangeDetectorRef,
    { success: vi.fn() } as unknown as NzMessageService,
    { ticket: { principal: 'alice' } } as unknown as TicketService,
    { create: vi.fn() } as unknown as NzModalService
  );
  component.noteId = 'note-1';
  component.permissions = permissions;
  component.ngOnInit();
  return { component, setPermissions };
};

const editEveryList = (component: NotebookPermissionsComponent) => {
  component.draftPermissions.owners = [];
  component.draftPermissions.writers.push('eve');
  component.draftPermissions.runners = ['frank'];
  component.draftPermissions.readers.pop();
};

describe('NotebookPermissionsComponent cancel', () => {
  it('leaves the parent permissions unchanged when edits are cancelled', () => {
    const parent = savedPermissions();
    const { component, setPermissions } = createComponent(parent);

    editEveryList(component);
    component.closePermissions();

    expect(parent).toEqual(savedPermissions());
    expect(setPermissions).not.toHaveBeenCalled();
  });

  it('shows the saved values when the panel is reopened after cancel', () => {
    const parent = savedPermissions();
    const { component: first } = createComponent(parent);
    editEveryList(first);
    first.closePermissions();

    const { component: reopened } = createComponent(parent);

    expect(reopened.draftPermissions).toEqual(savedPermissions());
  });

  it('restores the draft from the saved values on reset', () => {
    const { component } = createComponent(savedPermissions());
    editEveryList(component);

    component.resetPermissions();

    expect(component.draftPermissions).toEqual(savedPermissions());
  });

  it('refreshes the draft when new saved permissions arrive', () => {
    const { component } = createComponent(savedPermissions());
    editEveryList(component);
    const next: Permissions = { owners: ['zoe'], writers: [], runners: [], readers: [] };

    component.permissions = next;
    component.ngOnChanges({ permissions: new SimpleChange(undefined, next, false) });

    expect(component.draftPermissions).toEqual(next);
  });
});

describe('NotebookPermissionsComponent save', () => {
  it('persists all four edited lists and reports them to the parent', () => {
    const parent = savedPermissions();
    const { component, setPermissions } = createComponent(parent);
    const saved = vi.fn();
    component.permissionsSaved.subscribe(saved);
    const expected: Permissions = { owners: ['alice'], writers: ['bob', 'eve'], runners: ['frank'], readers: [] };

    component.draftPermissions = { ...expected, writers: [...expected.writers] };
    component.savePermissions();

    expect(setPermissions).toHaveBeenCalledWith('note-1', expected);
    expect(saved).toHaveBeenCalledWith(expected);
    expect(parent).toEqual(savedPermissions());
  });
});
