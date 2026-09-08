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

import { beforeEach, describe, expect, it, vi } from 'vitest';

import { exportFile } from './exportFile';

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
    expect([...bytes.subarray(0, 3)]).toEqual(UTF8_BOM);
    expect(new TextDecoder().decode(bytes.subarray(3))).toBe('name,city\nalice,서울');
    expect(saveAs.mock.calls[0][1]).toBe('export.csv');
  });

  it('does not export an empty table', async () => {
    await exportFile({ columnNames: ['name'], rows: [] }, 'csv');

    expect(saveAs).not.toHaveBeenCalled();
  });
});
