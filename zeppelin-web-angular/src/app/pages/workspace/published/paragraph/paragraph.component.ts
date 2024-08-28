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
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, QueryList, ViewChildren } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MessageListener, ParagraphBase } from '@zeppelin/core';
import { publishedSymbol, Published } from '@zeppelin/core/paragraph-base/published';
import { NotebookParagraphResultComponent } from '@zeppelin/pages/workspace/share/result/result.component';
import { MessageReceiveDataTypeMap, Note, OP } from '@zeppelin/sdk';
import { HeliumService, MessageService, NgZService, NoteStatusService } from '@zeppelin/services';
import { SpellResult } from '@zeppelin/spell/spell-result';
import { isNil } from 'lodash';

@Component({
  selector: 'zeppelin-publish-paragraph',
  templateUrl: './paragraph.component.html',
  styleUrls: ['./paragraph.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PublishedParagraphComponent extends ParagraphBase implements Published, OnInit {
  readonly [publishedSymbol] = true;

  noteId: string;
  paragraphId: string;

  @ViewChildren(NotebookParagraphResultComponent) notebookParagraphResultComponents: QueryList<
    NotebookParagraphResultComponent
  >;

  constructor(
    public messageService: MessageService,
    noteStatusService: NoteStatusService,
    ngZService: NgZService,
    cdr: ChangeDetectorRef,
    private activatedRoute: ActivatedRoute,
    private heliumService: HeliumService
  ) {
    super(messageService, noteStatusService, ngZService, cdr);
    this.activatedRoute.params.subscribe(params => {
      this.noteId = params.noteId;
      this.paragraphId = params.paragraphId;
      this.messageService.getNote(this.noteId);
    });
  }

  ngOnInit() {}

  @MessageListener(OP.NOTE)
  getNote(data: MessageReceiveDataTypeMap[OP.NOTE]) {
    const note = data.note;
    if (!isNil(note)) {
      this.paragraph = (note as Note['note']).paragraphs.find(p => p.id === this.paragraphId);
      if (this.paragraph) {
        this.setResults();
        this.originalText = this.paragraph.text;
        this.initializeDefault(this.paragraph.config);
      }
    }
    this.cdr.markForCheck();
  }

  trackByIndexFn(index: number) {
    return index;
  }

  setResults() {
    if (this.paragraph.results) {
      this.results = this.paragraph.results.msg;
      this.configs = this.paragraph.config.results;
    }
    if (!this.paragraph.config) {
      this.paragraph.config = {};
    }
  }

  changeColWidth(needCommit: boolean, updateResult?: boolean): void {
    // noop
  }

  runParagraph(): void {
    const text = this.paragraph.text;
    if (text && !this.isParagraphRunning) {
      const magic = SpellResult.extractMagic(this.paragraph.text);
      if (this.heliumService.getSpellByMagic(magic)) {
        this.runParagraphUsingSpell(text, magic, false);
      } else {
        this.runParagraphUsingBackendInterpreter(text);
      }
    }
  }
}
