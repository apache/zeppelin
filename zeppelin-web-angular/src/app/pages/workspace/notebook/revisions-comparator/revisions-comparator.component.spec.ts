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

import { ChangeDetectorRef } from '@angular/core';
import { DatePipe } from '@angular/common';
import { describe, expect, it, vi } from 'vitest';

import { NoteRevisionForCompareReceived } from '@zeppelin/sdk';
import { MessageService } from '@zeppelin/services';

import { NotebookRevisionsComparatorComponent } from './revisions-comparator.component';

// The barrel pulls in monaco-editor; the comparator only needs the constructor token.
vi.mock('@zeppelin/services', () => ({ MessageService: class {} }));

interface ParagraphFixture {
  id: string;
  text: string;
}

const revision = (revisionId: string, paragraphs: ParagraphFixture[]): NoteRevisionForCompareReceived =>
  ({
    noteId: 'note',
    revisionId,
    position: '',
    note: { paragraphs }
  }) as unknown as NoteRevisionForCompareReceived;

const compare = (first: NoteRevisionForCompareReceived, second: NoteRevisionForCompareReceived) => {
  const component = new NotebookRevisionsComparatorComponent(
    {} as MessageService,
    {} as ChangeDetectorRef,
    new DatePipe('en-US')
  );
  component.firstNoteRevisionForCompare = first;
  component.secondNoteRevisionForCompare = second;
  component.compareRevisions();
  return component.mergeNoteRevisionsDiff;
};

const segmentTexts = (diff: ReturnType<typeof compare>[number], type: 'insert' | 'delete') =>
  (diff.segments || []).filter(s => s.type === type).map(s => s.text);

const older = revision('older', [{ id: 'p', text: 'line common\nold line' }]);
const newer = revision('newer', [{ id: 'p', text: 'line common\nnew line' }]);

describe('NotebookRevisionsComparatorComponent.compareRevisions', () => {
  it('marks removed lines as deleted and added lines as inserted for older --> newer', () => {
    const [diff] = compare(older, newer);

    expect(diff.type).toBe('compared');
    expect(diff.identical).toBe(false);
    expect(segmentTexts(diff, 'delete')).toEqual(['old line']);
    expect(segmentTexts(diff, 'insert')).toEqual(['new line']);
  });

  it('reverses the line diff when the revisions are selected in the opposite order', () => {
    const [diff] = compare(newer, older);

    expect(segmentTexts(diff, 'delete')).toEqual(['new line']);
    expect(segmentTexts(diff, 'insert')).toEqual(['old line']);
  });

  it('marks a paragraph with unchanged text as identical', () => {
    const [diff] = compare(older, revision('same', [{ id: 'p', text: 'line common\nold line' }]));

    expect(diff.type).toBe('compared');
    expect(diff.identical).toBe(true);
    expect(diff.segments?.every(s => s.type === 'equal')).toBe(true);
  });

  it('classifies a paragraph only in the second revision as added', () => {
    const diffs = compare(revision('first', []), revision('second', [{ id: 'new', text: 'added\nbody' }]));

    expect(diffs).toHaveLength(1);
    expect(diffs[0].type).toBe('added');
    expect(diffs[0].paragraph.id).toBe('new');
    expect(diffs[0].firstString).toBe('added');
  });

  it('classifies a paragraph only in the first revision as deleted', () => {
    const diffs = compare(revision('first', [{ id: 'gone', text: 'removed\nbody' }]), revision('second', []));

    expect(diffs).toHaveLength(1);
    expect(diffs[0].type).toBe('deleted');
    expect(diffs[0].paragraph.id).toBe('gone');
    expect(diffs[0].firstString).toBe('removed');
  });
});
