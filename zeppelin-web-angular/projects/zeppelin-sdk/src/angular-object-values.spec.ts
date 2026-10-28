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

import { afterEach, assertType, describe, expect, expectTypeOf, it, vi } from 'vitest';

import { OP } from './interfaces/message-operator.interface';
import {
  AngularObjectRemove,
  AngularObjectUpdate,
  AngularObjectUpdated
} from './interfaces/message-paragraph.interface';
import { Message } from './message';

afterEach(() => vi.restoreAllMocks());

it('accepts arbitrary values through the public Angular object helpers', () => {
  expectTypeOf<Parameters<Message['angularObjectUpdate']>[3]>().toEqualTypeOf<unknown>();
  expectTypeOf<Parameters<Message['angularObjectClientBind']>[2]>().toEqualTypeOf<unknown>();
});

it('accepts notebook and global wire scopes without requiring Gson-omitted fields', () => {
  const updates: AngularObjectUpdate[] = [
    {
      noteId: 'route-note',
      interpreterGroupId: 'group',
      angularObject: { name: 'value', object: false, noteId: 'route-note' }
    },
    {
      noteId: 'route-note',
      paragraphId: null,
      interpreterGroupId: 'group',
      angularObject: { name: 'value', object: false, noteId: null, paragraphId: null }
    },
    {
      noteId: 'route-note',
      interpreterGroupId: 'group',
      angularObject: { name: 'value', object: false }
    }
  ];
  const removals: AngularObjectRemove[] = [
    { noteId: 'route-note', name: 'value', interpreterGroupId: 'group' },
    { noteId: 'route-note', paragraphId: null, name: 'value', interpreterGroupId: 'group' },
    { name: 'value', interpreterGroupId: 'group' },
    { noteId: null, name: 'value', interpreterGroupId: 'group' },
    { ...updates[0], angularObject: updates[0].angularObject },
    { ...updates[1], angularObject: updates[1].angularObject },
    { ...updates[2], angularObject: updates[2].angularObject }
  ];
  const outgoing: AngularObjectUpdated[] = [
    { noteId: 'route-note', name: 'value', value: false, interpreterGroupId: 'group' },
    { noteId: 'route-note', paragraphId: null, name: 'value', value: false, interpreterGroupId: 'group' }
  ];

  assertType<AngularObjectUpdate[]>(updates);
  assertType<AngularObjectRemove[]>(removals);
  assertType<AngularObjectUpdated[]>(outgoing);
  expectTypeOf<Parameters<Message['angularObjectUpdate']>[1]>().toEqualTypeOf<string | null | undefined>();
});

describe('Angular object send values', () => {
  it.each([false, 42, null, ['a', false], { count: 0, enabled: false }])(
    'preserves %j in update and bind payloads',
    value => {
      const message = new Message();
      const send = vi.spyOn(message, 'send').mockReturnValue('message-id');

      message.angularObjectUpdate('note', 'paragraph', 'value', value, 'group');
      message.angularObjectClientBind('note', 'value', value, 'paragraph');

      expect(send).toHaveBeenNthCalledWith(1, OP.ANGULAR_OBJECT_UPDATED, {
        noteId: 'note',
        paragraphId: 'paragraph',
        name: 'value',
        value,
        interpreterGroupId: 'group'
      });
      expect(send).toHaveBeenNthCalledWith(2, OP.ANGULAR_OBJECT_CLIENT_BIND, {
        noteId: 'note',
        name: 'value',
        value,
        paragraphId: 'paragraph'
      });
      expect(
        JSON.parse(JSON.stringify(send.mock.calls)).map(([, data]: [OP, { value: unknown }]) => data.value)
      ).toEqual([value, value]);
    }
  );
});
