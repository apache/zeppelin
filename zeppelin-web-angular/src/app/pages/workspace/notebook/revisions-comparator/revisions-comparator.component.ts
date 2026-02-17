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
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import * as DiffMatchPatch from 'diff-match-patch';
import { Subscription } from 'rxjs';

import { NoteRevisionForCompareReceived, OP, ParagraphItem, RevisionListItem } from '@zeppelin/sdk';
import { MessageService } from '@zeppelin/services';

interface MergedParagraphDiff {
  paragraph: ParagraphItem;
  firstString: string;
  type: 'added' | 'deleted' | 'compared';
  diff?: SafeHtml;
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
  mergeNoteRevisionsForCompare: MergedParagraphDiff[] = [];
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
    private datePipe: DatePipe,
    private sanitizer: DomSanitizer
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
    const paragraphs1 = this.firstNoteRevisionForCompare.note?.paragraphs || [];
    const paragraphs2 = this.secondNoteRevisionForCompare.note?.paragraphs || [];
    const merge: MergedParagraphDiff[] = [];

    for (const p1 of paragraphs1) {
      const p2 = paragraphs2.find((p: ParagraphItem) => p.id === p1.id) || null;
      if (p2 === null) {
        merge.push({
          paragraph: p1,
          firstString: (p1.text || '').split('\n')[0],
          type: 'deleted'
        });
      } else {
        const text1 = p1.text || '';
        const text2 = p2.text || '';
        const diffHtml = this.buildLineDiffHtml(text1, text2);
        merge.push({
          paragraph: p1,
          diff: diffHtml.html,
          identical: diffHtml.identical,
          firstString: (p1.text || '').split('\n')[0],
          type: 'compared'
        });
      }
    }

    for (const p2 of paragraphs2) {
      const p1 = paragraphs1.find((p: ParagraphItem) => p.id === p2.id) || null;
      if (p1 === null) {
        merge.push({
          paragraph: p2,
          firstString: (p2.text || '').split('\n')[0],
          type: 'added'
        });
      }
    }

    merge.sort((a, b) => {
      const order = { added: 0, deleted: 1, compared: 2 };
      return order[a.type] - order[b.type];
    });

    this.mergeNoteRevisionsForCompare = merge;

    if (this.currentParagraphDiffDisplay !== null) {
      this.changeCurrentParagraphDiffDisplay(this.currentParagraphDiffDisplay.paragraph.id);
    }
  }

  changeCurrentParagraphDiffDisplay(paragraphId: string): void {
    const found = this.mergeNoteRevisionsForCompare.find(p => p.paragraph.id === paragraphId);
    this.currentParagraphDiffDisplay = found || null;
  }

  formatRevisionDate(time: number | undefined): string {
    if (!time) {
      return '';
    }
    return this.datePipe.transform(time * 1000, 'MMMM d yyyy, h:mm:ss a') || '';
  }

  private buildLineDiffHtml(text1: string, text2: string): { html: SafeHtml; identical: boolean } {
    const a = this.dmp.diff_linesToChars_(text1, text2);
    const diffs = this.dmp.diff_main(a.chars1, a.chars2, false);
    this.dmp.diff_charsToLines_(diffs, a.lineArray);

    let identical = true;
    let html = '';

    for (const [op, text] of diffs) {
      let str = text;
      if (str.length > 0 && str[str.length - 1] !== '\n') {
        str = `${str}\n`;
      }
      const escaped = this.escapeHtml(str);
      if (op === DiffMatchPatch.DIFF_INSERT) {
        html += `<span class="color-green-row">${escaped}</span>`;
        identical = false;
      } else if (op === DiffMatchPatch.DIFF_DELETE) {
        html += `<span class="color-red-row">${escaped}</span>`;
        identical = false;
      } else {
        html += `<span class="color-black">${escaped}</span>`;
      }
    }

    return { html: this.sanitizer.bypassSecurityTrustHtml(html), identical };
  }

  private escapeHtml(text: string): string {
    const div = document.createElement('div');
    div.appendChild(document.createTextNode(text));
    return div.innerHTML;
  }

  ngOnDestroy(): void {
    if (this.subscription) {
      this.subscription.unsubscribe();
    }
  }
}
