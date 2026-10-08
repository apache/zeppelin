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

import { assertType, describe, expect, it } from 'vitest';

import { getAngularObjectRemovalName } from './angular-object';
import { AngularObjectRemove } from './interfaces/message-paragraph.interface';

describe('Angular object removal compatibility', () => {
  const context = { noteId: 'note', paragraphId: 'paragraph' };

  it('reads the interpreter removal name', () => {
    const data = { ...context, name: 'value' } satisfies AngularObjectRemove;
    expect(getAngularObjectRemovalName(data)).toBe('value');
  });

  it('reads the client-unbind object name without a top-level name', () => {
    const data = { ...context, angularObject: { name: 'value', object: 42 }, interpreterGroupId: 'group' };
    assertType<AngularObjectRemove>(data);
    expect(getAngularObjectRemovalName(data)).toBe('value');
  });

  it('prefers the top-level name when both are supplied', () => {
    expect(
      getAngularObjectRemovalName({
        ...context,
        name: 'top',
        angularObject: { name: 'nested', object: null }
      })
    ).toBe('top');
  });
});
