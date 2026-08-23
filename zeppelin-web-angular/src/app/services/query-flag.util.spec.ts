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

import { describe, expect, it } from 'vitest';
import { parseBooleanFlag } from './query-flag.util';

describe('parseBooleanFlag', () => {
  it.each([
    ['', true],
    ['true', true],
    ['false', false],
    [undefined, null],
    [null, null],
    ['1', null],
    ['TRUE', null]
  ])('parses %s as %s', (value, expected) => {
    expect(parseBooleanFlag(value)).toBe(expected);
  });
});
