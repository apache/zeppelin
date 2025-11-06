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
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  OnDestroy,
  QueryList,
  TemplateRef,
  ViewChild,
  ViewChildren
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { publishedSymbol, MessageListener, ParagraphBase, Published } from '@zeppelin/core';
import {
  MessageReceiveDataTypeMap,
  OP,
  ParagraphConfigResult,
  ParagraphItem,
  ParagraphIResultsMsgItem
} from '@zeppelin/sdk';
import { HeliumService, MessageService, NgZService, NoteStatusService } from '@zeppelin/services';
import { SpellResult } from '@zeppelin/spell';
import { isNil } from 'lodash';
import { NzModalService } from 'ng-zorro-antd/modal';
import { NotebookParagraphResultComponent } from '../../share/result/result.component';
import { environment } from '../../../../../environments/environment';

@Component({
  selector: 'zeppelin-publish-paragraph',
  templateUrl: './paragraph.component.html',
  styleUrls: ['./paragraph.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PublishedParagraphComponent extends ParagraphBase implements Published, OnDestroy {
  readonly [publishedSymbol] = true;

  noteId: string | null = null;
  paragraphId: string | null = null;
  previewCode: string = '';
  useReact = false;
  isLoading = true;
  error: string | null = null;

  @ViewChild('codePreviewModal', { static: true }) codePreviewModal!: TemplateRef<void>;
  @ViewChild('reactContainer', { static: false }) reactContainer!: ElementRef<HTMLDivElement>;
  @ViewChildren(NotebookParagraphResultComponent)
  notebookParagraphResultComponents!: QueryList<NotebookParagraphResultComponent>;

  constructor(
    public messageService: MessageService,
    private activatedRoute: ActivatedRoute,
    private heliumService: HeliumService,
    private router: Router,
    private nzModalService: NzModalService,
    noteStatusService: NoteStatusService,
    ngZService: NgZService,
    cdr: ChangeDetectorRef
  ) {
    super(messageService, noteStatusService, ngZService, cdr);
    this.activatedRoute.queryParams.subscribe(queryParams => {
      this.useReact = queryParams.react === 'true' || queryParams.react === '';
    });

    this.activatedRoute.params.subscribe(params => {
      if (typeof params.noteId !== 'string') {
        throw new Error(`noteId path parameter should be string, but got ${typeof params.noteId} instead.`);
      }
      this.noteId = params.noteId;
      this.paragraphId = params.paragraphId!;
      this.messageService.getNote(params.noteId);
    });
  }

  ngOnDestroy() {
    if (this.useReact) {
      this.cleanupReactWidget();
    }
  }

  @MessageListener(OP.NOTE)
  getNote(data: MessageReceiveDataTypeMap[OP.NOTE]) {
    const note = data.note;
    if (!isNil(note)) {
      this.paragraph = note.paragraphs.find(p => p.id === this.paragraphId);
      if (this.paragraph) {
        if (!this.paragraph.results) {
          this.showRunConfirmationModal();
        }
        if (this.useReact) {
          this.setResults(this.paragraph);
          this.isLoading = false;
          this.cdr.markForCheck();
          this.loadReactWidget();
          return;
        }

        this.setResults(this.paragraph);
        this.originalText = this.paragraph.text;
        this.initializeDefault(this.paragraph.config, this.paragraph.settings);
      } else {
        this.handleParagraphNotFound(note.name || this.noteId!, this.paragraphId!);
        return;
      }
    }
    this.cdr.markForCheck();
  }

  @MessageListener(OP.ERROR_INFO)
  handleError(data: MessageReceiveDataTypeMap[OP.ERROR_INFO]) {
    if (data.info && data.info.includes('404')) {
      this.handleNoteNotFound(this.noteId!);
    }
  }

  trackByIndexFn(index: number) {
    return index;
  }

  setResults(paragraph: ParagraphItem) {
    if (paragraph.results) {
      this.results = paragraph.results.msg || [];
      this.configs = paragraph.config.results || {};
    }
    if (!paragraph.config) {
      paragraph.config = {};
    }
  }

  changeColWidth(_needCommit: boolean, _updateResult?: boolean): void {
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

  private showRunConfirmationModal(): void {
    if (!this.paragraph) {
      return;
    }

    this.previewCode = this.paragraph.text || '';

    this.nzModalService.confirm({
      nzTitle: 'Run Paragraph?',
      nzContent: this.codePreviewModal,
      nzOkText: 'Run',
      nzCancelText: 'Cancel',
      nzWidth: 600,
      nzOnOk: () => this.runParagraph()
    });
  }

  private handleParagraphNotFound(noteName: string, paragraphId: string): void {
    this.router.navigate(['/']).then(() => {
      this.nzModalService.error({
        nzTitle: 'Paragraph Not Found',
        nzContent: `The paragraph "${paragraphId}" does not exist in notebook "${noteName}". You have been redirected to the home page.`,
        nzOkText: 'OK'
      });
    });
  }

  private handleNoteNotFound(noteId: string): void {
    this.router.navigate(['/']).then(() => {
      this.nzModalService.error({
        nzTitle: 'Notebook Not Found',
        nzContent: `The notebook "${noteId}" does not exist or you don't have permission to access it. You have been redirected to the home page.`,
        nzOkText: 'OK'
      });
    });
  }

  private loadReactWidget() {
    if (!this.reactContainer || !this.paragraph) {
      return;
    }

    const script = document.createElement('script');
    script.src = environment.reactRemoteEntryUrl;

    script.onload = async () => {
      // @ts-ignore
      const container = window.reactApp;
      if (!container) {
        throw new Error('window.reactApp not available');
      }

      const factory = await container.get('./PublishedParagraph');
      const { mount } = factory();

      if (!mount || typeof mount !== 'function') {
        throw new Error('mount function not found');
      }

      const mountPoint = this.reactContainer.nativeElement;
      const props = {
        paragraphId: this.paragraphId,
        noteId: this.noteId,
        results: this.paragraph?.results?.msg,
        config: this.paragraph?.config?.results
      };

      mount(mountPoint, props);
    };

    document.head.appendChild(script);
  }

  private cleanupReactWidget() {
    if (this.reactContainer?.nativeElement) {
      this.reactContainer.nativeElement.innerHTML = '';
    }
  }
}
