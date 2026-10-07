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

import { afterEach, describe, expect, expectTypeOf, it, vi } from 'vitest';

import { OP } from './interfaces/message-operator.interface';
import { Message } from './message';

afterEach(() => vi.restoreAllMocks());

it('accepts arbitrary values through the public Angular object helpers', () => {
  expectTypeOf<Parameters<Message['angularObjectUpdate']>[3]>().toEqualTypeOf<unknown>();
  expectTypeOf<Parameters<Message['angularObjectClientBind']>[2]>().toEqualTypeOf<unknown>();
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
