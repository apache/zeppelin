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

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { SaveAsService } from './save-as.service';

/**
 * Returns the bytes the service handed to createObjectURL. Blob.text() decodes and strips a
 * leading BOM, so the raw bytes are the only way to tell whether one was written.
 */
async function downloadedBytes(saveAs: () => void): Promise<Uint8Array> {
  let saved: Blob | undefined;
  // the service goes through window.URL, which is not the same object vi.stubGlobal replaces
  vi.spyOn(window.URL, 'createObjectURL').mockImplementation((blob: Blob | MediaSource) => {
    saved = blob as Blob;
    return 'blob:url';
  });
  vi.spyOn(window.URL, 'revokeObjectURL').mockImplementation(() => undefined);

  saveAs();

  expect(saved).toBeDefined();
  return new Uint8Array(await (saved as Blob).arrayBuffer());
}

const UTF8_BOM = [0xef, 0xbb, 0xbf];

function hasBom(bytes: Uint8Array): boolean {
  return UTF8_BOM.every((byte, index) => bytes[index] === byte);
}

describe('SaveAsService', () => {
  let service: SaveAsService;

  beforeEach(() => {
    service = new SaveAsService();
    // jsdom has no navigation, so the anchor click must not actually do anything
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('leaves a JSON export without a BOM', async () => {
    const content = '{"paragraphs":[{"text":"한글 テスト 中文 🎉"}]}';

    const bytes = await downloadedBytes(() => service.saveAs(content, 'note', 'zpln'));

    // a BOM here makes strict JSON parsers such as Python's json or nbformat reject the file
    expect(hasBom(bytes)).toBe(false);
    const text = new TextDecoder().decode(bytes);
    expect(text).toBe(content);
    expect(JSON.parse(text)).toEqual(JSON.parse(content));
  });

  it('prepends a BOM when the caller asks for one', async () => {
    const content = 'name,value\n한글,1\n';

    const bytes = await downloadedBytes(() => service.saveAs(content, 'result', 'csv', true));

    // Excel needs the BOM to read the CSV as UTF-8 (ZEPPELIN-672)
    expect(hasBom(bytes)).toBe(true);
    expect(new TextDecoder().decode(bytes.subarray(UTF8_BOM.length))).toBe(content);
  });
});
