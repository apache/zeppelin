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

import { getKeywordPositions } from './get-keyword-positions';

// This function has no callers today, and neither does the line-map module it builds on.
// It is kept as a worked example because it has the shape the conventions describe (branches, boundaries, a regular expression), and writing the spec is what surfaced that it is unused.
describe('getKeywordPositions', () => {
  it('returns no positions when the keyword is absent', () => {
    expect(getKeywordPositions(['missing'], 'nothing to find here')).toEqual([]);
  });

  it('reports line and character for a match on the first line', () => {
    expect(getKeywordPositions(['world'], 'hello world')).toEqual([{ line: 0, character: 6, length: 5 }]);
  });

  it('counts lines from zero and restarts the character offset on each line', () => {
    expect(getKeywordPositions(['target'], 'first\nsecond target')).toEqual([{ line: 1, character: 7, length: 6 }]);
  });

  it('reports every occurrence of the same keyword', () => {
    expect(getKeywordPositions(['ab'], 'ab\nab')).toEqual([
      { line: 0, character: 0, length: 2 },
      { line: 1, character: 0, length: 2 }
    ]);
  });

  it('matches case-insensitively', () => {
    expect(getKeywordPositions(['SELECT'], 'select 1')).toEqual([{ line: 0, character: 0, length: 6 }]);
  });

  it('groups results by keyword rather than by position in the text', () => {
    expect(getKeywordPositions(['a', 'b'], 'b a')).toEqual([
      { line: 0, character: 2, length: 1 },
      { line: 0, character: 0, length: 1 }
    ]);
  });

  it('treats the keyword as a regular expression, not a literal', () => {
    // `new RegExp(keyword, 'ig')`: `.` matches any character, and the reported length is the keyword's, not the match's.
    expect(getKeywordPositions(['a.'], 'ab')).toEqual([{ line: 0, character: 0, length: 2 }]);
    expect(getKeywordPositions(['a+'], 'aaa')).toEqual([{ line: 0, character: 0, length: 2 }]);
  });

  it('returns an empty result for an empty keyword list', () => {
    expect(getKeywordPositions([], 'hello')).toEqual([]);
  });
});
