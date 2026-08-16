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
import { describe, expect, it, vi } from 'vitest';

import { NotebookParagraphComponent } from './paragraph.component';

/**
 * sendPatch() only reads the text fields and sends through messageService, so the component is
 * built without its constructor to keep the test free of the surrounding Angular dependencies.
 */
function createParagraphComponent(originalText: string) {
  const patchParagraph = vi.fn();
  const component = Object.create(NotebookParagraphComponent.prototype) as NotebookParagraphComponent;

  Object.assign(component, {
    diffMatchPatch: new DiffMatchPatch(),
    messageService: { patchParagraph },
    note: { id: 'note-1' },
    paragraph: { id: 'paragraph-1' },
    originalText
  });

  return { component, patchParagraph };
}

describe('NotebookParagraphComponent', () => {
  it('sends a patch when the paragraph is cleared', () => {
    const { component, patchParagraph } = createParagraphComponent('abc');
    component.dirtyText = '';

    component.sendPatch();

    expect(patchParagraph).toHaveBeenCalledOnce();
    const [paragraphId, noteId, patch] = patchParagraph.mock.calls[0];
    expect(paragraphId).toBe('paragraph-1');
    expect(noteId).toBe('note-1');
    // the patch has to carry the deletion, otherwise collaborators keep the old text
    const dmp = new DiffMatchPatch();
    expect(dmp.patch_apply(dmp.patch_fromText(patch), 'abc')[0]).toBe('');
    expect(component.originalText).toBe('');
  });

  it('still rejects text that was never set', () => {
    const { component, patchParagraph } = createParagraphComponent('abc');
    component.dirtyText = undefined;

    expect(() => component.sendPatch()).toThrow('dirtyText is required');
    expect(patchParagraph).not.toHaveBeenCalled();
  });
});
