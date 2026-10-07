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

import * as XLSX from 'xlsx-js-style';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { exportFile } from './exportFile';
import type { TableData } from './tableUtils';

const saveAs = vi.fn();
vi.mock('file-saver', () => ({ saveAs: (...args: unknown[]) => saveAs(...args) }));

const UTF8_BOM = [0xef, 0xbb, 0xbf];

/** The saved bytes, since Blob.text() decodes and drops a leading BOM. */
async function savedBytes(): Promise<Uint8Array> {
  expect(saveAs).toHaveBeenCalledOnce();
  const blob = saveAs.mock.calls[0][0] as Blob;
  return new Uint8Array(await blob.arrayBuffer());
}

describe('exportFile', () => {
  beforeEach(() => {
    saveAs.mockClear();
  });

  it('prepends a UTF-8 BOM to the CSV so Excel reads it as UTF-8', async () => {
    await exportFile({ columnNames: ['name', 'city'], rows: [['alice', '서울']] }, 'csv');

    const bytes = await savedBytes();
    expect(Array.from(bytes.subarray(0, 3))).toEqual(UTF8_BOM);
    expect(new TextDecoder().decode(bytes.subarray(3))).toBe('name,city\nalice,서울');
    expect(saveAs.mock.calls[0][1]).toBe('export.csv');
  });

  it('joins the header and several rows with commas and newlines in the CSV', async () => {
    await exportFile(
      {
        columnNames: ['id', 'name'],
        rows: [
          ['1', 'alice'],
          ['2', 'bob']
        ]
      },
      'csv'
    );

    const bytes = await savedBytes();
    expect(new TextDecoder().decode(bytes.subarray(3))).toBe('id,name\n1,alice\n2,bob');
  });

  it('does not export an empty table', async () => {
    await exportFile({ columnNames: ['name'], rows: [] }, 'csv');

    expect(saveAs).not.toHaveBeenCalled();
  });

  it('does not export an empty table as xlsx either', async () => {
    await exportFile({ columnNames: ['name'], rows: [] }, 'xlsx');

    expect(saveAs).not.toHaveBeenCalled();
  });

  it('does not export when the table data has no rows at all', async () => {
    await exportFile({ columnNames: ['name'] } as unknown as TableData, 'csv');
    await exportFile(undefined as unknown as TableData, 'csv');

    expect(saveAs).not.toHaveBeenCalled();
  });

  it('saves xlsx as export.xlsx with the spreadsheet MIME type', async () => {
    await exportFile({ columnNames: ['name', 'city'], rows: [['alice', '서울']] }, 'xlsx');

    expect(saveAs).toHaveBeenCalledOnce();
    const blob = saveAs.mock.calls[0][0] as Blob;
    expect(blob.type).toContain('spreadsheetml.sheet');
    expect(saveAs.mock.calls[0][1]).toBe('export.xlsx');
  });

  it('writes the header and the rows to Sheet1 of the xlsx file', async () => {
    await exportFile(
      {
        columnNames: ['name', 'city'],
        rows: [
          ['alice', '서울'],
          ['bob', 'Busan']
        ]
      },
      'xlsx'
    );

    const bytes = await savedBytes();
    const workbook = XLSX.read(bytes, { type: 'array' });
    expect(workbook.SheetNames).toEqual(['Sheet1']);
    expect(XLSX.utils.sheet_to_json(workbook.Sheets['Sheet1'], { header: 1 })).toEqual([
      ['name', 'city'],
      ['alice', '서울'],
      ['bob', 'Busan']
    ]);
  });
});
