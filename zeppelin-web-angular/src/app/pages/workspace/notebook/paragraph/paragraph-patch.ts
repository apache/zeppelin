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

import * as DiffMatchPatch from 'diff-match-patch';

/**
 * Builds the patch a collaborating client sends after an edit. An empty string is a valid
 * paragraph state, so only text that was never set is rejected.
 */
export function makeParagraphPatch(
  diffMatchPatch: DiffMatchPatch,
  originalText: string | undefined,
  dirtyText: string | undefined
): { patch: string; originalText: string } {
  if (dirtyText === undefined) {
    throw new Error('dirtyText is required');
  }
  const previousText = originalText ? originalText : '';
  return {
    patch: diffMatchPatch.patch_make(previousText, dirtyText).toString(),
    originalText: dirtyText
  };
}
