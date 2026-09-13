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

import { expect, expectTypeOf, it } from 'vitest';

import { EditorSettingReceived, ImportNote, Note } from './message-notebook.interface';
import { AngularObjectRemove, ImportParagraphItem, ParagraphItem } from './message-paragraph.interface';

it('separates received wire fields from backward-compatible import input', () => {
  expectTypeOf<ParagraphItem>().toHaveProperty('progress').toEqualTypeOf<number>();
  expectTypeOf<NonNullable<Note['note']>>().toHaveProperty('version').toEqualTypeOf<string | undefined>();
  expectTypeOf<EditorSettingReceived['editor']>().toHaveProperty('completionKey').toEqualTypeOf<string | undefined>();
  expectTypeOf<AngularObjectRemove>().toHaveProperty('angularObject').toEqualTypeOf<
    | {
        name: string;
        object: unknown;
        noteId: string;
        paragraphId: string;
      }
    | undefined
  >();
  expectTypeOf<AngularObjectRemove>().toHaveProperty('interpreterGroupId').toEqualTypeOf<string | undefined>();
  const legacyImportParagraph = {
    text: '%md legacy import',
    user: 'anonymous',
    dateUpdated: '2026-09-14T00:00:00.000Z',
    config: {},
    settings: { params: {}, forms: {} },
    apps: [],
    progressUpdateIntervalMs: 500,
    jobName: 'paragraph',
    id: 'paragraph-1',
    dateCreated: '2026-09-14T00:00:00.000Z',
    status: 'READY',
    aborted: false,
    lineNumbers: false,
    fontSize: 9
  } satisfies ImportParagraphItem;
  const importWithoutVersion: ImportNote = {
    note: {
      paragraphs: [legacyImportParagraph],
      name: 'Imported note',
      id: 'imported-note',
      path: '/Imported note',
      defaultInterpreterGroup: '',
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
    }
  };

  expectTypeOf<NonNullable<Note['note']>>().toHaveProperty('version').toEqualTypeOf<string | undefined>();
  expectTypeOf<ImportNote['note']>().toHaveProperty('version').toEqualTypeOf<string | undefined>();
  expectTypeOf<ImportNote['note']['paragraphs'][number]>()
    .toHaveProperty('progress')
    .toEqualTypeOf<number | undefined>();
  expect(importWithoutVersion.note).not.toHaveProperty('version');
  expect(importWithoutVersion.note.paragraphs[0]).not.toHaveProperty('progress');
});

it('accepts the personalized GET_NOTE response without a version', () => {
  // NotebookService.getNote returns Note.getUserNote for personalized notebooks.
  // That copy is constructed with Note(), so its nullable version is omitted by Message serialization.
  const personalizedNote: Note = {
    note: {
      paragraphs: [],
      name: 'Personalized note',
      id: 'personalized-note',
      path: '/Personalized note',
      defaultInterpreterGroup: '',
      noteParams: {},
      noteForms: {},
      angularObjects: {},
      config: {
        releaseresource: false,
        isZeppelinNotebookCronEnable: false,
        looknfeel: 'default',
        personalizedMode: 'true'
      },
      info: {}
    }
  };

  expectTypeOf<NonNullable<Note['note']>['version']>().toEqualTypeOf<string | undefined>();
  expect(personalizedNote.note).not.toHaveProperty('version');
});
