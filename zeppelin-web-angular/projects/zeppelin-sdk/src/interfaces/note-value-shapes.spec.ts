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

import { assertType, expectTypeOf, it } from 'vitest';

import { Note, NoteAngularObject, NoteAngularObjects, NoteInfo, NoteParams } from './message-notebook.interface';
import { DynamicFormParams } from './message-paragraph.interface';

type NoteValueFields = Pick<NonNullable<Note['note']>, 'noteParams' | 'angularObjects' | 'info'>;

it('matches the Note parameter, angular-object list and metadata maps', () => {
  expectTypeOf<NoteParams>().toEqualTypeOf<DynamicFormParams>();
  expectTypeOf<NoteAngularObjects[string]>().toEqualTypeOf<NoteAngularObject[]>();
  expectTypeOf<NoteAngularObject>().toEqualTypeOf<{
    name: string;
    object?: unknown;
    noteId?: string;
    paragraphId?: string;
  }>();
  expectTypeOf<NoteInfo[string]>().toEqualTypeOf<unknown>();
  assertType<NoteAngularObjects>({ spark: [{ name: 'empty' }] });
});

it('accepts Note values with structured parameters and omitted object or scope fields', () => {
  assertType<NoteValueFields>({
    noteParams: { count: 0, enabled: false, selected: ['a', { id: 42 }] },
    angularObjects: {
      spark: [
        { name: 'global', object: { nested: [1, false] } },
        { name: 'note', object: 42, noteId: 'note' },
        { name: 'paragraph', object: false, noteId: 'note', paragraphId: 'paragraph' },
        { name: 'empty' }
      ]
    },
    info: { isRunning: false, startTime: '2026-01-01T00:00:00Z', custom: { nested: [1, true] } }
  });
});
