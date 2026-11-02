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

import { ChangeDetectorRef } from '@angular/core';
import { NzModalRef } from 'ng-zorro-antd/modal';
import { EMPTY } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { InterpreterItem, Note } from '@zeppelin/sdk';
import { MessageService, NoteListService } from '@zeppelin/services';

import { NoteCreateComponent } from './note-create.component';

const interpreter = (name: string): InterpreterItem => ({
  id: name,
  name,
  group: name,
  properties: {},
  status: 'READY',
  interpreterGroup: [],
  dependencies: [],
  option: {
    remote: true,
    port: -1,
    isExistingProcess: false,
    setPermission: false,
    owners: [],
    isUserImpersonate: false
  }
});

const sourceNote = (name: string): NonNullable<Note['note']> => ({
  id: 'source-note',
  name,
  path: `/${name}`,
  paragraphs: [],
  defaultInterpreterGroup: 'spark',
  noteParams: {},
  noteForms: {},
  angularObjects: {},
  config: {
    releaseresource: false,
    isZeppelinNotebookCronEnable: false,
    looknfeel: 'default',
    personalizedMode: 'false'
  },
  info: {}
});

const createComponent = (notePaths: string[] = [], data: ConstructorParameters<typeof NoteCreateComponent>[4] = {}) => {
  const markForCheck = vi.fn();
  const component = new NoteCreateComponent(
    { receive: () => EMPTY, getInterpreterSettings: vi.fn() } as unknown as MessageService,
    { markForCheck } as unknown as ChangeDetectorRef,
    { notes: { flatList: notePaths.map(path => ({ path })) } } as unknown as NoteListService,
    {} as NzModalRef,
    data
  );
  return { component, markForCheck };
};

describe('NoteCreateComponent', () => {
  let component: NoteCreateComponent;

  afterEach(() => component?.ngOnDestroy());

  it('leaves the default unset when no interpreters are available', () => {
    const fixture = createComponent();
    component = fixture.component;

    component.getInterpreterSettings({ interpreterSettings: [] });

    expect(component.defaultInterpreter).toBeUndefined();
    expect(fixture.markForCheck).toHaveBeenCalled();
  });

  it('selects the first available interpreter', () => {
    ({ component } = createComponent());

    component.getInterpreterSettings({ interpreterSettings: [interpreter('spark'), interpreter('python')] });

    expect(component.defaultInterpreter).toBe('spark');
  });

  it('clears an unavailable selection and selects from a later nonempty response', () => {
    ({ component } = createComponent());
    component.getInterpreterSettings({ interpreterSettings: [interpreter('spark')] });

    component.getInterpreterSettings({ interpreterSettings: [] });

    expect(component.defaultInterpreter).toBeUndefined();

    component.getInterpreterSettings({ interpreterSettings: [interpreter('python')] });

    expect(component.defaultInterpreter).toBe('python');
  });

  describe('new note naming', () => {
    it.each([
      { path: '', expected: 'Untitled Note 1' },
      { path: '/Projects/Reports', expected: '/Projects/Reports/Untitled Note 1' }
    ])('initializes the note name for path "$path"', ({ path, expected }) => {
      ({ component } = createComponent([], { path }));

      component.ngOnInit();

      expect(component.noteName).toBe(expected);
    });

    it('uses the highest existing note number regardless of list order', () => {
      ({ component } = createComponent(
        ['/Untitled Note 8', '/Untitled Note 2', '/Untitled Note 5', '/Other 99', '/Untitled Note 20 draft'],
        { path: '' }
      ));

      component.ngOnInit();

      expect(component.noteName).toBe('Untitled Note 9');
    });
  });

  describe('clone note naming', () => {
    it('adds a copy number to an unnumbered source name', () => {
      ({ component } = createComponent(['Sales Report', 'Other 1'], { cloneNote: sourceNote('Sales Report') }));

      component.ngOnInit();

      expect(component.noteName).toBe('Sales Report 1');
    });

    it('increments past existing copies without appending a second number', () => {
      ({ component } = createComponent(['Sales Report 7', 'Sales Report 2', 'Sales Report 4', 'Other 99'], {
        cloneNote: sourceNote('Sales Report 2')
      }));

      component.ngOnInit();

      expect(component.noteName).toBe('Sales Report 8');
    });

    it('matches regex metacharacters in source names literally', () => {
      ({ component } = createComponent(['Revenue (USD) 2', 'Revenue (USD) 4', 'Revenue USD 9'], {
        cloneNote: sourceNote('Revenue (USD) 2')
      }));

      component.ngOnInit();

      expect(component.noteName).toBe('Revenue (USD) 5');
    });
  });
});
