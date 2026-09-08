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

import { checkAndReplaceCarriageReturn } from './textUtils';

describe('checkAndReplaceCarriageReturn', () => {
  it('leaves text without carriage returns untouched', () => {
    expect(checkAndReplaceCarriageReturn('plain\ntext')).toBe('plain\ntext');
  });

  it('normalises CRLF to LF', () => {
    expect(checkAndReplaceCarriageReturn('a\r\nb')).toBe('a\nb');
  });

  it('overwrites the start of the line, the way a terminal progress bar does', () => {
    expect(checkAndReplaceCarriageReturn('12345\rab')).toBe('ab345');
  });

  it('keeps the longer previous content visible behind a shorter overwrite', () => {
    expect(checkAndReplaceCarriageReturn('loading...\rok')).toBe('okading...');
  });

  it('drops the previous content when the overwrite is at least as long', () => {
    expect(checkAndReplaceCarriageReturn('ab\r1234')).toBe('1234');
  });

  it('rewrites only the lines that contain a carriage return', () => {
    expect(checkAndReplaceCarriageReturn('keep\n12345\rab\nkeep too')).toBe('keep\nab345\nkeep too');
  });

  it('returns an empty string unchanged', () => {
    expect(checkAndReplaceCarriageReturn('')).toBe('');
  });
});
