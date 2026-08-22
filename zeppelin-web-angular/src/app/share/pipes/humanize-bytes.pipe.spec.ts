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

import { HumanizeBytesPipe } from './humanize-bytes.pipe';

describe('HumanizeBytesPipe', () => {
  const pipe = new HumanizeBytesPipe();

  it('renders a dash for null and undefined', () => {
    expect(pipe.transform(null)).toBe('-');
    expect(pipe.transform(undefined)).toBe('-');
  });

  it('renders a dash for a string that is not a number', () => {
    expect(pipe.transform('not a number')).toBe('-');
  });

  it('accepts a numeric string', () => {
    expect(pipe.transform('2048')).toBe('2.00 KB');
  });

  it('reports values below 1000 as raw bytes', () => {
    expect(pipe.transform(0)).toBe('0 B');
    expect(pipe.transform(999)).toBe('999 B');
  });

  it('switches from bytes to KB at 1000 but divides by 1024', () => {
    // 1000 is already reported in KB even though it is below 1024, so the first kilobyte value rendered is less than 1.
    expect(pipe.transform(1000)).toBe('0.98 KB');
    expect(pipe.transform(1024)).toBe('1.00 KB');
  });

  it('climbs to the next unit once the value reaches 1000 times the base', () => {
    expect(pipe.transform(1024 * 1024)).toBe('1.00 MB');
    expect(pipe.transform(1024 * 1024 * 1024)).toBe('1.00 GB');
  });

  it('uses two decimals below the base and three significant digits above it', () => {
    expect(pipe.transform(1536)).toBe('1.50 KB');
  });
});
