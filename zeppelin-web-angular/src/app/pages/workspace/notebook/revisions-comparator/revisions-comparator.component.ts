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

import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnDestroy, OnInit } from '@angular/core';
import * as DiffMatchPatch from 'diff-match-patch';
import { Subscription } from 'rxjs';

import { NoteRevisionForCompareReceived, OP, ParagraphItem, RevisionListItem } from '@zeppelin/sdk';
import { MessageService } from '@zeppelin/services';

interface DiffSegment {
  type: 'insert' | 'delete' | 'equal';
  text: string;
}

interface MergedParagraphDiff {
  paragraph: ParagraphItem;
  firstString: string;
  type: 'added' | 'deleted' | 'compared';
  segments?: DiffSegment[];
  identical?: boolean;
}

@Component({
  selector: 'zeppelin-notebook-revisions-comparator',
  templateUrl: './revisions-comparator.component.html',
  styleUrls: ['./revisions-comparator.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [DatePipe]
})
export class NotebookRevisionsComparatorComponent implements OnInit, OnDestroy {
  @Input() noteRevisions: RevisionListItem[] = [];
  @Input() noteId!: string;

  firstNoteRevisionForCompare: NoteRevisionForCompareReceived | null = null;
  secondNoteRevisionForCompare: NoteRevisionForCompareReceived | null = null;
  currentFirstRevisionLabel = 'Choose...';
  currentSecondRevisionLabel = 'Choose...';
  mergeNoteRevisionsDiff: MergedParagraphDiff[] = [];
  currentParagraphDiffDisplay: MergedParagraphDiff | null = null;
  selectedFirstRevisionId: string | null = null;
  selectedSecondRevisionId: string | null = null;
  private subscription: Subscription | null = null;
  private dmp = new DiffMatchPatch();

  get sortedRevisions(): RevisionListItem[] {
    return [...this.noteRevisions].sort((a, b) => (b.time || 0) - (a.time || 0));
  }

  constructor(
    private messageService: MessageService,
    private cdr: ChangeDetectorRef,
    private datePipe: DatePipe
  ) {}

  ngOnInit(): void {
    this.subscription = this.messageService
      .receive(OP.NOTE_REVISION_FOR_COMPARE)
      .subscribe((data: NoteRevisionForCompareReceived) => {
        if (data.note && data.position) {
          if (data.position === 'first') {
            this.firstNoteRevisionForCompare = data;
          } else {
            this.secondNoteRevisionForCompare = data;
          }

          if (
            this.firstNoteRevisionForCompare !== null &&
            this.secondNoteRevisionForCompare !== null &&
            this.firstNoteRevisionForCompare.revisionId !== this.secondNoteRevisionForCompare.revisionId
          ) {
            this.compareRevisions();
          }
          this.cdr.markForCheck();
        }
      });
  }

  getNoteRevisionForReview(revision: RevisionListItem, position: 'first' | 'second'): void {
    if (!revision) {
      return;
    }
    if (position === 'first') {
      this.currentFirstRevisionLabel = revision.message;
      this.selectedFirstRevisionId = revision.id;
    } else {
      this.currentSecondRevisionLabel = revision.message;
      this.selectedSecondRevisionId = revision.id;
    }
    this.messageService.noteRevisionForCompare(this.noteId, revision.id, position);
  }

  onFirstRevisionSelect(revisionId: string): void {
    const revision = this.noteRevisions.find(r => r.id === revisionId);
    if (revision) {
      this.getNoteRevisionForReview(revision, 'first');
    }
  }

  onSecondRevisionSelect(revisionId: string): void {
    const revision = this.noteRevisions.find(r => r.id === revisionId);
    if (revision) {
      this.getNoteRevisionForReview(revision, 'second');
    }
  }

  onRevisionRowClick(index: number): void {
    const sorted = this.sortedRevisions;
    if (index < sorted.length - 1) {
      this.getNoteRevisionForReview(sorted[index + 1], 'first');
      this.getNoteRevisionForReview(sorted[index], 'second');
    }
  }

  compareRevisions(): void {
    if (!this.firstNoteRevisionForCompare || !this.secondNoteRevisionForCompare) {
      return;
    }
    const baseParagraphs = this.secondNoteRevisionForCompare.note?.paragraphs || [];
    const compareParagraphs = this.firstNoteRevisionForCompare.note?.paragraphs || [];
    const paragraphDiffs: MergedParagraphDiff[] = [];

    for (const p1 of baseParagraphs) {
      const p2 = compareParagraphs.find((p: ParagraphItem) => p.id === p1.id) || null;
      if (p2 === null) {
        paragraphDiffs.push({
          paragraph: p1,
          firstString: (p1.text || '').split('\n')[0],
          type: 'added'
        });
      } else {
        const text1 = p1.text || '';
        const text2 = p2.text || '';
        const diffResult = this.buildLineDiff(text1, text2);
        paragraphDiffs.push({
          paragraph: p1,
          segments: diffResult.segments,
          identical: diffResult.identical,
          firstString: (p1.text || '').split('\n')[0],
          type: 'compared'
        });
      }
    }

    for (const p2 of compareParagraphs) {
      const p1 = baseParagraphs.find((p: ParagraphItem) => p.id === p2.id) || null;
      if (p1 === null) {
        paragraphDiffs.push({
          paragraph: p2,
          firstString: (p2.text || '').split('\n')[0],
          type: 'deleted'
        });
      }
    }

    this.mergeNoteRevisionsDiff = paragraphDiffs;

    if (this.currentParagraphDiffDisplay !== null) {
      this.changeCurrentParagraphDiffDisplay(this.currentParagraphDiffDisplay.paragraph.id);
    }
  }

  changeCurrentParagraphDiffDisplay(paragraphId: string): void {
    const found = this.mergeNoteRevisionsDiff.find(p => p.paragraph.id === paragraphId);
    this.currentParagraphDiffDisplay = found || null;
  }

  formatRevisionDate(time: number | undefined): string {
    if (!time) {
      return '';
    }
    return this.datePipe.transform(time * 1000, 'MMMM d yyyy, h:mm:ss a') || '';
  }

  private buildLineDiff(text1: string, text2: string): { segments: DiffSegment[]; identical: boolean } {
    const { chars1, chars2, lineArray } = this.dmp.diff_linesToChars_(text1, text2);
    const diffs = this.dmp.diff_main(chars1, chars2, false);
    this.dmp.diff_charsToLines_(diffs, lineArray);

    let identical = true;
    const segments: DiffSegment[] = [];

    for (const [op, text] of diffs) {
      if (op === DiffMatchPatch.DIFF_INSERT) {
        segments.push({ type: 'insert', text });
        identical = false;
      } else if (op === DiffMatchPatch.DIFF_DELETE) {
        segments.push({ type: 'delete', text });
        identical = false;
      } else {
        segments.push({ type: 'equal', text });
      }
    }

    return { segments, identical };
  }

  ngOnDestroy(): void {
    if (this.subscription) {
      this.subscription.unsubscribe();
    }
  }
}
