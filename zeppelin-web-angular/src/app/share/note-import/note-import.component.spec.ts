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

import { HttpBackend } from '@angular/common/http';
import { ChangeDetectorRef } from '@angular/core';
import { ConfigurationService, MessageService } from '@zeppelin/services';
import { NzModalRef } from 'ng-zorro-antd/modal';
import { NEVER } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { NoteImportComponent } from './note-import.component';

const createComponent = () => {
  const importNote = vi.fn();
  const component = new NoteImportComponent(
    { importNote, receive: () => NEVER } as unknown as MessageService,
    {} as ConfigurationService,
    { markForCheck: vi.fn() } as unknown as ChangeDetectorRef,
    {} as NzModalRef,
    {} as HttpBackend
  );
  return { component, importNote };
};

const jupyterNote = () => ({
  cells: [{ cell_type: 'markdown', source: ['# Imported notebook'], metadata: {} }],
  metadata: { title: 'Original Jupyter title', kernelspec: { display_name: 'Python 3' } },
  nbformat: 4,
  nbformat_minor: 5
});

describe('NoteImportComponent', () => {
  it.each(['file', 'url'])('admits Jupyter %s input without replacing its title with the kernel name', source => {
    const { component, importNote } = createComponent();
    const note = jupyterNote();

    component.processImportJson(source === 'file' ? JSON.stringify(note) : note);

    expect(component.errorText).toBeUndefined();
    expect(importNote).toHaveBeenCalledExactlyOnceWith(note);
    expect(component.noteImportName).toBeUndefined();
  });

  it('uses Import As in preference to a Jupyter name and title', () => {
    const { component, importNote } = createComponent();
    const note = { ...jupyterNote(), name: 'Original name' };
    component.noteImportName = 'Folder/Chosen name';

    component.processImportJson(note);

    expect(importNote).toHaveBeenCalledExactlyOnceWith({ ...note, name: 'Folder/Chosen name' });
  });

  it('leaves default naming to the server when kernel metadata and title are absent', () => {
    const { component, importNote } = createComponent();
    const note = { ...jupyterNote(), metadata: {} };
    const expectedNote = structuredClone(note);

    component.processImportJson(note);

    expect(component.errorText).toBeUndefined();
    expect(importNote).toHaveBeenCalledExactlyOnceWith(expectedNote);
    expect(component.noteImportName).toBeUndefined();
  });

  it('preserves the original Zeppelin name unless Import As overrides it', () => {
    const { component, importNote } = createComponent();
    const note = { name: 'Original Zeppelin name', paragraphs: [{ text: '%md Existing note' }] };

    component.processImportJson(JSON.stringify(note));

    expect(component.noteImportName).toBe(note.name);
    expect(importNote).toHaveBeenCalledExactlyOnceWith(note);
  });

  it.each([
    null,
    [],
    { name: 'Not a notebook' },
    { paragraphs: [] },
    { cells: [], metadata: {} },
    { cells: 'not an array', metadata: {} },
    { cells: [{}] },
    { cells: [{}], metadata: null },
    { cells: [{}], metadata: [] },
    { cells: 'invalid', paragraphs: [{ text: '%md Not the server-selected format' }] }
  ])('rejects invalid or empty notebook input: %j', note => {
    const { component, importNote } = createComponent();

    component.processImportJson(note);

    expect(component.errorText).toBe('Invalid JSON');
    expect(importNote).not.toHaveBeenCalled();
  });

  it('reports malformed JSON without sending an import request', () => {
    const { component, importNote } = createComponent();

    component.processImportJson('{');

    expect(component.errorText).toBe('JSON parse exception');
    expect(importNote).not.toHaveBeenCalled();
  });
});
