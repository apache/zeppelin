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

import { diff_match_patch as DiffMatchPatch } from 'diff-match-patch';
import { describe, expect, it } from 'vitest';

import { textChecksum } from '@zeppelin/sdk';

import { makeParagraphPatch } from './paragraph-patch';

describe('makeParagraphPatch', () => {
  it('builds a patch that clears the paragraph', () => {
    const dmp = new DiffMatchPatch();

    const { patch, originalText } = makeParagraphPatch(dmp, 'abc', '');

    // without this patch the collaborating client keeps the previous text
    expect(dmp.patch_apply(dmp.patch_fromText(patch), 'abc')[0]).toBe('');
    expect(originalText).toBe('');
  });

  it('rejects text that was never set', () => {
    expect(() => makeParagraphPatch(new DiffMatchPatch(), 'abc', undefined)).toThrow('dirtyText is required');
  });

  it('carries checksums of the text before and after the patch', () => {
    const { baseChecksum, afterChecksum } = makeParagraphPatch(new DiffMatchPatch(), 'alpha', 'alpha!');

    // a receiver compares these against its own text to tell an applied patch from a dropped or
    // misapplied one, so they have to describe the sender's text on both sides of the edit
    expect(baseChecksum).toBe(textChecksum('alpha'));
    expect(afterChecksum).toBe(textChecksum('alpha!'));
    expect(baseChecksum).not.toBe(afterChecksum);
  });

  it('treats missing original text as empty when checksumming', () => {
    const { baseChecksum } = makeParagraphPatch(new DiffMatchPatch(), undefined, 'alpha');

    // the server checksums "" for a paragraph it has no text for, so the first patch must agree
    expect(baseChecksum).toBe(textChecksum(''));
  });
});
