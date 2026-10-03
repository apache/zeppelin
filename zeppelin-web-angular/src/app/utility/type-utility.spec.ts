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

import { isRecord } from './type-utility';

describe('isRecord', () => {
  it('accepts a plain object, empty or not', () => {
    expect(isRecord({})).toBe(true);
    expect(isRecord({ a: 1 })).toBe(true);
  });

  it('accepts an object without a prototype', () => {
    expect(isRecord(Object.create(null))).toBe(true);
  });

  it('rejects null even though typeof null is "object"', () => {
    expect(isRecord(null)).toBe(false);
  });

  it('rejects arrays, empty or not', () => {
    expect(isRecord([])).toBe(false);
    expect(isRecord([1, 2])).toBe(false);
  });

  it.each([
    ['undefined', undefined],
    ['a string', 'text'],
    ['a number', 0],
    ['a boolean', false],
    ['a symbol', Symbol('s')],
    ['a function', () => ({})]
  ])('rejects %s', (_label, value) => {
    expect(isRecord(value)).toBe(false);
  });
});
