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
import { publishedSymbol, MessageListener, ParagraphBase, Published } from '@zeppelin/core';
import {
  MessageReceiveDataTypeMap,
  OP,
  ParagraphConfigResult,
  ParagraphItem,
  ParagraphIResultsMsgItem
} from '@zeppelin/sdk';
import { HeliumService, MessageService, NgZService, NoteStatusService } from '@zeppelin/services';
import { SpellResult } from '@zeppelin/spell/spell-result';
import { isNil } from 'lodash';
import { NotebookParagraphResultComponent } from '../../share/result/result.component';

@Component({
  selector: 'zeppelin-publish-paragraph',
  templateUrl: './paragraph.component.html',
  styleUrls: ['./paragraph.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PublishedParagraphComponent extends ParagraphBase implements Published, OnInit {
  readonly [publishedSymbol] = true;

  noteId: string | null = null;
  paragraphId: string | null = null;

  @ViewChildren(NotebookParagraphResultComponent) notebookParagraphResultComponents!: QueryList<
    NotebookParagraphResultComponent
  >;

  constructor(
    public messageService: MessageService,
    private activatedRoute: ActivatedRoute,
    private heliumService: HeliumService,
    noteStatusService: NoteStatusService,
    ngZService: NgZService,
    cdr: ChangeDetectorRef
  ) {
    super(messageService, noteStatusService, ngZService, cdr);
    this.activatedRoute.params.subscribe(params => {
      if (typeof params.noteId !== 'string') {
        throw new Error(`noteId path parameter should be string, but got ${typeof params.noteId} instead.`);
      }
      this.noteId = params.noteId;
      this.paragraphId = params.paragraphId!;
      this.messageService.getNote(params.noteId);
    });
  }

  ngOnInit() {}

  @MessageListener(OP.NOTE)
  getNote(data: MessageReceiveDataTypeMap[OP.NOTE]) {
    const note = data.note;
    if (!isNil(note)) {
      this.paragraph = note.paragraphs.find(p => p.id === this.paragraphId);
      if (this.paragraph) {
        this.setResults(this.paragraph);
        this.originalText = this.paragraph.text;
        this.initializeDefault(this.paragraph.config, this.paragraph.settings);
      }
    }
    this.cdr.markForCheck();
  }

  trackByIndexFn(index: number) {
    return index;
  }

  setResults(paragraph: ParagraphItem) {
    if (paragraph.results) {
      this.results = paragraph.results.msg;
      this.configs = paragraph.config.results;
    }
    if (!paragraph.config) {
      paragraph.config = {};
    }
  }

  changeColWidth(needCommit: boolean, updateResult?: boolean): void {
    // noop
  }

  runParagraph(): void {
    if (!this.paragraph) {
      throw new Error('paragraph is not defined');
    }
    const text = this.paragraph.text;
    if (text && !this.isParagraphRunning) {
      const magic = SpellResult.extractMagic(this.paragraph.text);
      if (magic && this.heliumService.getSpellByMagic(magic)) {
        this.runParagraphUsingSpell(text, magic, false);
      } else {
        this.runParagraphUsingBackendInterpreter(text);
      }
    }
  }

  updateParagraphResult(resultIndex: number, config: ParagraphConfigResult, result: ParagraphIResultsMsgItem): void {
    const resultComponent = this.notebookParagraphResultComponents.toArray()[resultIndex];
    if (resultComponent) {
      resultComponent.updateResult(config, result);
    }
  }
}
