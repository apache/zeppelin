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
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
  QueryList,
  SimpleChanges,
  ViewChild,
  ViewChildren
} from '@angular/core';
import { merge, Observable, Subject } from 'rxjs';
import { map, takeUntil } from 'rxjs/operators';

import { NzModalService } from 'ng-zorro-antd/modal';

import { ParagraphBase } from '@zeppelin/core';
import { InterpreterBindingItem, Note, ParagraphConfigResult, ParagraphItem } from '@zeppelin/sdk';
import {
  HeliumService,
  MessageService,
  NgZService,
  NoteStatusService,
  NoteVarShareService,
  ParagraphActions,
  ShortcutsMap,
  ShortcutService
} from '@zeppelin/services';
import { SpellResult } from '@zeppelin/spell/spell-result';

import { NgTemplateAdapterService } from '@zeppelin/services/ng-template-adapter.service';
import { NzResizeEvent } from 'ng-zorro-antd/resizable';
import { NotebookParagraphResultComponent } from '../../share/result/result.component';
import { NotebookParagraphCodeEditorComponent } from './code-editor/code-editor.component';

type Mode = 'edit' | 'command';

@Component({
  selector: 'zeppelin-notebook-paragraph',
  templateUrl: './paragraph.component.html',
  styleUrls: ['./paragraph.component.less'],
  host: {
    tabindex: '-1',
    '(focusin)': 'onFocus()'
  },
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotebookParagraphComponent extends ParagraphBase implements OnInit, OnChanges, OnDestroy {
  @ViewChild(NotebookParagraphCodeEditorComponent, { static: false })
  notebookParagraphCodeEditorComponent: NotebookParagraphCodeEditorComponent;
  @ViewChildren(NotebookParagraphResultComponent) notebookParagraphResultComponents: QueryList<
    NotebookParagraphResultComponent
  >;
  @Input() paragraph: ParagraphItem;
  @Input() note: Note['note'];
  @Input() looknfeel: string;
  @Input() revisionView: boolean;
  @Input() select: boolean = false;
  @Input() index: number = -1;
  @Input() viewOnly: boolean;
  @Input() last: boolean;
  @Input() collaborativeMode = false;
  @Input() first: boolean;
  @Input() interpreterBindings: InterpreterBindingItem[] = [];
  @Output() readonly saveNoteTimer = new EventEmitter();
  @Output() readonly triggerSaveParagraph = new EventEmitter<string>();
  @Output() readonly selected = new EventEmitter<string>();
  @Output() readonly selectAtIndex = new EventEmitter<number>();

  private destroy$ = new Subject();
  private mode: Mode = 'command';
  waitConfirmFromEdit = false;

  switchMode(mode: Mode): void {
    if (mode === this.mode) {
      return;
    }
    this.mode = mode;
    if (mode === 'edit') {
      this.focusEditor();
    } else {
      this.blurEditor();
    }
  }

  textChanged(text: string) {
    this.dirtyText = text;
    this.paragraph.text = text;
    if (this.dirtyText !== this.originalText) {
      if (this.collaborativeMode) {
        this.sendPatch();
      } else {
        this.startSaveTimer();
      }
    }
  }

  sendPatch() {
    this.originalText = this.originalText ? this.originalText : '';
    const patch = this.diffMatchPatch.patch_make(this.originalText, this.dirtyText).toString();
    this.originalText = this.dirtyText;
    this.messageService.patchParagraph(this.paragraph.id, this.note.id, patch);
  }

  startSaveTimer() {
    this.saveNoteTimer.emit();
  }

  onFocus() {
    this.selected.emit(this.paragraph.id);
  }

  focusEditor() {
    this.paragraph.focus = true;
    this.saveParagraph();
    this.cdr.markForCheck();
  }

  blurEditor() {
    this.paragraph.focus = false;
    (this.host.nativeElement as HTMLElement).focus();
    this.saveParagraph();
    this.cdr.markForCheck();
  }

  onEditorFocus() {
    this.switchMode('edit');
  }

  onEditorBlur() {
    // Ignore events triggered by open the confirm box in edit mode
    if (!this.waitConfirmFromEdit) {
      this.switchMode('command');
    }
  }

  saveParagraph() {
    const dirtyText = this.paragraph.text;
    if (dirtyText === undefined || dirtyText === this.originalText) {
      return;
    }
    this.commitParagraph();
    this.originalText = dirtyText;
    this.dirtyText = undefined;
    this.cdr.markForCheck();
  }

  removeParagraph() {
    if (!this.isEntireNoteRunning) {
      if (this.note.paragraphs.length === 1) {
        this.nzModalService.warning({
          nzTitle: `Warning`,
          nzContent: `All the paragraphs can't be deleted`
        });
      } else {
        this.nzModalService.confirm({
          nzTitle: 'Delete Paragraph',
          nzContent: 'Do you want to delete this paragraph?',
          nzOnOk: () => {
            this.messageService.paragraphRemove(this.paragraph.id);
            this.cdr.markForCheck();
            // TODO(hsuanxyz) moveFocusToNextParagraph
          }
        });
      }
    }
  }

  runAllAbove() {
    const index = this.note.paragraphs.findIndex(p => p.id === this.paragraph.id);
    const toRunParagraphs = this.note.paragraphs.filter((p, i) => i < index);

    const paragraphs = toRunParagraphs.map(p => {
      return {
        id: p.id,
        title: p.title,
        paragraph: p.text,
        config: p.config,
        params: p.settings.params
      };
    });
    this.nzModalService.confirm({
      nzTitle: 'Run all above?',
      nzContent: 'Are you sure to run all above paragraphs?',
      nzOnOk: () => {
        this.messageService.runAllParagraphs(this.note.id, paragraphs);
      }
    });
    // TODO(hsuanxyz): save cursor
  }

  doubleClickParagraph() {
    if (this.paragraph.config.editorSetting.editOnDblClick && this.revisionView !== true) {
      this.paragraph.config.editorHide = false;
      this.paragraph.config.tableHide = true;
      // TODO(hsuanxyz): focus editor
    }
  }

  runAllBelowAndCurrent() {
    const index = this.note.paragraphs.findIndex(p => p.id === this.paragraph.id);
    const toRunParagraphs = this.note.paragraphs.filter((p, i) => i >= index);

    const paragraphs = toRunParagraphs.map(p => {
      return {
        id: p.id,
        title: p.title,
        paragraph: p.text,
        config: p.config,
        params: p.settings.params
      };
    });
    this.nzModalService
      .confirm({
        nzTitle: 'Run current and all below?',
        nzContent: 'Are you sure to run current and all below?',
        nzOnOk: () => {
          this.messageService.runAllParagraphs(this.note.id, paragraphs);
        }
      })
      .afterClose.pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.waitConfirmFromEdit = false;
      });
    // TODO(hsuanxyz): save cursor
  }

  cloneParagraph(position: string = 'below', newText?: string) {
    let newIndex = -1;
    for (let i = 0; i < this.note.paragraphs.length; i++) {
      if (this.note.paragraphs[i].id === this.paragraph.id) {
        // determine position of where to add new paragraph; default is below
        if (position === 'above') {
          newIndex = i;
        } else {
          newIndex = i + 1;
        }
        break;
      }
    }

    if (newIndex < 0 || newIndex > this.note.paragraphs.length) {
      return;
    }

    const config = this.paragraph.config;
    config.editorHide = false;

    this.messageService.copyParagraph(
      newIndex,
      this.paragraph.title,
      newText || this.paragraph.text,
      config,
      this.paragraph.settings.params
    );
  }

  runParagraphAfter(text: string) {
    this.originalText = text;
    this.dirtyText = undefined;

    if (this.paragraph.config.editorSetting.editOnDblClick) {
      this.paragraph.config.editorHide = true;
      this.paragraph.config.tableHide = false;
      this.commitParagraph();
    } else if (this.editorSetting.isOutputHidden && !this.paragraph.config.editorSetting.editOnDblClick) {
      // %md/%angular repl make output to be hidden by default after running
      // so should open output if repl changed from %md/%angular to another
      this.paragraph.config.editorHide = false;
      this.paragraph.config.tableHide = false;
      this.commitParagraph();
    }
    this.editorSetting.isOutputHidden = this.paragraph.config.editorSetting.editOnDblClick;
  }

  runParagraph(paragraphText?: string, propagated: boolean = false) {
    const text = paragraphText || this.paragraph.text;
    if (text && !this.isParagraphRunning) {
      const magic = SpellResult.extractMagic(text);

      if (this.heliumService.getSpellByMagic(magic)) {
        this.runParagraphUsingSpell(text, magic, propagated);
        this.runParagraphAfter(text);
      } else {
        const check = this.ngTemplateAdapterService.preCheck(text);
        if (!check) {
          this.runParagraphUsingBackendInterpreter(text);
          this.runParagraphAfter(text);
        } else {
          this.waitConfirmFromEdit = true;
          this.nzModalService
            .confirm({
              nzTitle: 'Do you want to migrate the Angular.js template?',
              nzContent:
                'The Angular.js template has been deprecated, please upgrade to Angular template.' +
                ' (<a href="https://angular.io/guide/ajs-quick-reference" target="_blank">more info</a>)',
              nzOnOk: () => {
                this.switchMode('command');
                this.ngTemplateAdapterService
                  .openMigrationDialog(check)
                  .pipe(takeUntil(this.destroy$))
                  .subscribe(newText => {
                    this.cloneParagraph('below', newText);
                  });
              }
            })
            .afterClose.pipe(takeUntil(this.destroy$))
            .subscribe(() => (this.waitConfirmFromEdit = false));
        }
      }
    }
  }

  insertParagraph(position: string) {
    if (this.revisionView === true) {
      return;
    }
    let newIndex = -1;
    for (let i = 0; i < this.note.paragraphs.length; i++) {
      if (this.note.paragraphs[i].id === this.paragraph.id) {
        // determine position of where to add new paragraph; default is below
        if (position === 'above') {
          newIndex = i;
        } else {
          newIndex = i + 1;
        }
        break;
      }
    }

    if (newIndex < 0 || newIndex > this.note.paragraphs.length) {
      return;
    }
    this.messageService.insertParagraph(newIndex);
    this.cdr.markForCheck();
  }

  setTitle(title: string) {
    this.paragraph.title = title;
    this.commitParagraph();
  }

  commitParagraph() {
    const {
      id,
      title,
      text,
      config,
      settings: { params }
    } = this.paragraph;
    this.messageService.commitParagraph(id, title, text, config, params, this.note.id);
    this.cdr.markForCheck();
  }

  moveUpParagraph() {
    const newIndex = this.note.paragraphs.findIndex(p => p.id === this.paragraph.id) - 1;
    if (newIndex < 0 || newIndex >= this.note.paragraphs.length) {
      return;
    }
    // save dirtyText of moving paragraphs.
    const prevParagraph = this.note.paragraphs[newIndex];
    // TODO(hsuanxyz): save pre paragraph?
    this.saveParagraph();
    this.triggerSaveParagraph.emit(prevParagraph.id);
    this.messageService.moveParagraph(this.paragraph.id, newIndex);
  }

  moveDownParagraph() {
    const newIndex = this.note.paragraphs.findIndex(p => p.id === this.paragraph.id) + 1;
    if (newIndex < 0 || newIndex >= this.note.paragraphs.length) {
      return;
    }
    // save dirtyText of moving paragraphs.
    const nextParagraph = this.note.paragraphs[newIndex];
    // TODO(hsuanxyz): save pre paragraph?
    this.saveParagraph();
    this.triggerSaveParagraph.emit(nextParagraph.id);
    this.messageService.moveParagraph(this.paragraph.id, newIndex);
  }

  changeColWidth(needCommit: boolean, updateResult = true) {
    if (needCommit) {
      this.commitParagraph();
    }
    if (this.notebookParagraphCodeEditorComponent) {
      this.notebookParagraphCodeEditorComponent.layout();
    }

    if (updateResult) {
      this.notebookParagraphResultComponents.forEach(comp => {
        comp.setGraphConfig();
      });
    }
  }

  onSizeChange(resize: NzResizeEvent) {
    this.paragraph.config.colWidth = resize.col;
    this.changeColWidth(true, false);
    this.cdr.markForCheck();
  }

  onConfigChange(configResult: ParagraphConfigResult, index: number) {
    this.paragraph.config.results[index] = configResult;
    this.commitParagraph();
  }

  setEditorHide(editorHide: boolean) {
    this.paragraph.config.editorHide = editorHide;
    this.cdr.markForCheck();
  }

  setTableHide(tableHide: boolean) {
    this.paragraph.config.tableHide = tableHide;
    this.cdr.markForCheck();
  }

  openSingleParagraph(paragraphId: string): void {
    const noteId = this.note.id;
    const redirectToUrl = `${location.protocol}//${location.host}${location.pathname}#/notebook/${noteId}/paragraph/${paragraphId}`;
    window.open(redirectToUrl);
  }

  trackByIndexFn(index: number) {
    return index;
  }

  constructor(
    noteStatusService: NoteStatusService,
    cdr: ChangeDetectorRef,
    ngZService: NgZService,
    private heliumService: HeliumService,
    public messageService: MessageService,
    private nzModalService: NzModalService,
    private noteVarShareService: NoteVarShareService,
    private shortcutService: ShortcutService,
    private host: ElementRef,
    private ngTemplateAdapterService: NgTemplateAdapterService
  ) {
    super(messageService, noteStatusService, ngZService, cdr);
  }

  ngOnInit() {
    const shortcutService = this.shortcutService.forkByElement(this.host.nativeElement);
    const observables: Array<
      Observable<{
        action: ParagraphActions;
        event: KeyboardEvent;
      }>
    > = [];
    Object.entries(ShortcutsMap).forEach(([action, keys]) => {
      const keysArr: string[] = Array.isArray(keys) ? keys : [keys];
      keysArr.forEach(key => {
        observables.push(
          shortcutService
            .bindShortcut({
              keybindings: key
            })
            .pipe(
              takeUntil(this.destroy$),
              map(({ event }) => {
                return {
                  event,
                  action: action as ParagraphActions
                };
              })
            )
        );
      });
    });

    merge<{
      action: ParagraphActions;
      event: KeyboardEvent;
    }>(...observables)
      .pipe(takeUntil(this.destroy$))
      .subscribe(({ action, event }) => {
        if (this.mode === 'command') {
          switch (action) {
            case ParagraphActions.InsertAbove:
              this.insertParagraph('above');
              break;
            case ParagraphActions.InsertBelow:
              this.insertParagraph('below');
              break;
            case ParagraphActions.SwitchEditorShow:
              this.setEditorHide(!this.paragraph.config.editorHide);
              this.commitParagraph();
              break;
            case ParagraphActions.SwitchOutputShow:
              this.setTableHide(!this.paragraph.config.tableHide);
              this.commitParagraph();
              break;
            case ParagraphActions.SwitchTitleShow:
              this.paragraph.config.title = !this.paragraph.config.title;
              this.commitParagraph();
              break;
            case ParagraphActions.SwitchLineNumber:
              this.paragraph.config.lineNumbers = !this.paragraph.config.lineNumbers;
              this.commitParagraph();
              break;
            case ParagraphActions.MoveToUp:
              event.preventDefault();
              this.moveUpParagraph();
              break;
            case ParagraphActions.MoveToDown:
              event.preventDefault();
              this.moveDownParagraph();
              break;
            case ParagraphActions.SwitchEnable:
              this.paragraph.config.enabled = !this.paragraph.config.enabled;
              this.commitParagraph();
              break;
            case ParagraphActions.ReduceWidth:
              this.paragraph.config.colWidth = Math.max(1, this.paragraph.config.colWidth - 1);
              this.cdr.markForCheck();
              this.changeColWidth(true);
              break;
            case ParagraphActions.IncreaseWidth:
              this.paragraph.config.colWidth = Math.min(12, this.paragraph.config.colWidth + 1);
              this.cdr.markForCheck();
              this.changeColWidth(true);
              break;
            case ParagraphActions.Delete:
              this.removeParagraph();
              break;
            case ParagraphActions.SelectAbove:
              event.preventDefault();
              this.selectAtIndex.emit(this.index - 1);
              break;
            case ParagraphActions.SelectBelow:
              event.preventDefault();
              this.selectAtIndex.emit(this.index + 1);
              break;
            default:
              break;
          }
        }
        switch (action) {
          case ParagraphActions.Link:
            this.openSingleParagraph(this.paragraph.id);
            break;
          case ParagraphActions.EditMode:
            if (this.mode === 'command') {
              event.preventDefault();
            }
            if (!this.paragraph.config.editorHide) {
              this.switchMode('edit');
            }
            break;
          case ParagraphActions.Run:
            event.preventDefault();
            this.runParagraph();
            break;
          case ParagraphActions.RunBelow:
            this.waitConfirmFromEdit = true;
            this.runAllBelowAndCurrent();
            break;
          case ParagraphActions.Cancel:
            event.preventDefault();
            this.cancelParagraph();
            break;
          default:
            break;
        }
      });
    this.setResults();
    this.originalText = this.paragraph.text;
    this.isEntireNoteRunning = this.noteStatusService.isEntireNoteRunning(this.note);
    this.isParagraphRunning = this.noteStatusService.isParagraphRunning(this.paragraph);
    this.noteVarShareService.set(this.paragraph.id + '_paragraphScope', this);
    this.initializeDefault(this.paragraph.config);
    this.ngZService
      .runParagraphAction()
      .pipe(takeUntil(this.destroy$))
      .subscribe(id => {
        if (id === this.paragraph.id) {
          this.runParagraph();
        }
      });
    this.ngZService
      .contextChanged()
      .pipe(takeUntil(this.destroy$))
      .subscribe(change => {
        if (change.paragraphId === this.paragraph.id && change.emit) {
          if (change.set) {
            this.messageService.angularObjectClientBind(this.note.id, change.key, change.value, change.paragraphId);
          } else {
            this.messageService.angularObjectClientUnbind(this.note.id, change.key, change.paragraphId);
          }
        }
      });
  }

  ngOnChanges(changes: SimpleChanges): void {
    const { index, select } = changes;
    if (
      (index && index.currentValue !== index.previousValue && this.select) ||
      (select && select.currentValue === true && select.previousValue !== true)
    ) {
      if (this.host.nativeElement) {
        setTimeout(() => {
          if (this.mode === 'command') {
            (this.host.nativeElement as HTMLElement).focus();
          }
        });
      }
    }
  }

  getElement(): HTMLElement {
    return this.host && this.host.nativeElement;
  }

  ngOnDestroy(): void {
    super.ngOnDestroy();
  }
}
