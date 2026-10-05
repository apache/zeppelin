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

import { parseTableData } from './tableUtils';

describe('parseTableData', () => {
  it('uses the first line as column names and splits the rest into rows on tabs', () => {
    expect(parseTableData('name\tcity\nalice\tSeoul\nbob\tBusan')).toEqual({
      columnNames: ['name', 'city'],
      rows: [
        ['alice', 'Seoul'],
        ['bob', 'Busan']
      ]
    });
  });

  it('returns no rows when the input is only a header line', () => {
    expect(parseTableData('name\tcity')).toEqual({ columnNames: ['name', 'city'], rows: [] });
  });

  it('ignores a trailing newline instead of producing an empty last row', () => {
    expect(parseTableData('name\tcity\nalice\tSeoul\n').rows).toEqual([['alice', 'Seoul']]);
  });

  it('keeps an empty cell between two tabs', () => {
    expect(parseTableData('a\tb\tc\n1\t\t3').rows).toEqual([['1', '', '3']]);
  });
});
