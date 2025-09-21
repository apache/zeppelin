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
  AfterViewInit,
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
import { editor as MonacoEditor } from 'monaco-editor';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { NzModalService } from 'ng-zorro-antd/modal';

import { ParagraphBase } from '@zeppelin/core';
import {
  InterpreterBindingItem,
  Note,
  ParagraphConfigResult,
  ParagraphItem,
  ParagraphIResultsMsgItem
} from '@zeppelin/sdk';
import {
  HeliumService,
  MessageService,
  NgZService,
  NoteStatusService,
  NoteVarShareService,
  ShortcutService
} from '@zeppelin/services';
import { SpellResult } from '@zeppelin/spell';

import {
  AngularKeyboardEventHandler,
  KeyBinder,
  NullableKeyboardEvent,
  ParagraphActions,
  ParagraphActionToHandlerName
} from '@zeppelin/key-binding';
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
export class NotebookParagraphComponent extends ParagraphBase
  implements OnInit, OnChanges, OnDestroy, AfterViewInit, AngularKeyboardEventHandler {
  @ViewChild(NotebookParagraphCodeEditorComponent, { static: false })
  notebookParagraphCodeEditorComponent?: NotebookParagraphCodeEditorComponent;
  @ViewChildren(NotebookParagraphResultComponent) notebookParagraphResultComponents!: QueryList<
    NotebookParagraphResultComponent
  >;
  @Input() paragraph!: ParagraphItem;
  @Input() note!: Exclude<Note['note'], undefined>;
  @Input() looknfeel!: string;
  @Input() revisionView!: boolean;
  @Input() select: boolean = false;
  @Input() scrolled: boolean = false;
  @Input() index: number = -1;
  @Input() viewOnly!: boolean;
  @Input() last!: boolean;
  @Input() collaborativeMode = false;
  @Input() first!: boolean;
  @Input() interpreterBindings: InterpreterBindingItem[] = [];
  @Output() readonly saveNoteTimer = new EventEmitter();
  @Output() readonly triggerSaveParagraph = new EventEmitter<string>();
  @Output() readonly selected = new EventEmitter<string>();
  @Output() readonly selectAtIndex = new EventEmitter<number>();
  @Output() readonly searchCode = new EventEmitter();

  private destroy$ = new Subject();

  private mode: Mode = 'command';
  waitConfirmFromEdit = false;

  private keyBinderService: KeyBinder;

  updateParagraphResult(resultIndex: number, config: ParagraphConfigResult, result: ParagraphIResultsMsgItem): void {
    const resultComponent = this.notebookParagraphResultComponents.toArray()[resultIndex];
    if (resultComponent) {
      resultComponent.updateResult(config, result);
    }
  }

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
    if (!this.dirtyText) {
      throw new Error('dirtyText is required');
    }
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

  toggleEditorShow() {
    this.setEditorHide(!this.paragraph.config.editorHide);
    this.commitParagraph();
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
        this.nzModalService
          .confirm({
            nzTitle: 'Delete Paragraph',
            nzContent: 'Do you want to delete this paragraph?',
            nzAutofocus: null,
            nzOnOk: () => true
          })
          .afterClose.pipe(takeUntil(this.destroy$))
          .subscribe(result => {
            // In the modal, clicking "Cancel" makes result undefined.
            // Clicking "OK" makes result defined and passes the condition below.
            if (result) {
              this.messageService.paragraphRemove(this.paragraph.id);
              this.cdr.markForCheck();
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
    this.nzModalService
      .confirm({
        nzTitle: 'Run all above?',
        nzContent: 'Are you sure to run all above paragraphs?',
        nzOnOk: () => {
          this.messageService.runAllParagraphs(this.note.id, paragraphs);
        }
      })
      .afterClose.pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.waitConfirmFromEdit = false;
        this.notebookParagraphCodeEditorComponent?.setRestorePosition();
      });
  }

  doubleClickParagraph() {
    if (!this.paragraph.config.editorSetting) {
      throw new Error('editorSetting is required');
    }
    if (this.paragraph.config.editorSetting.editOnDblClick && this.revisionView !== true) {
      this.paragraph.config.editorHide = false;
      this.paragraph.config.tableHide = true;
      this.focusEditor();
      this.cdr.detectChanges();
      this.notebookParagraphCodeEditorComponent?.setCursorPositionToEnd();
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
        this.notebookParagraphCodeEditorComponent?.setRestorePosition();
      });
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
    if (!this.paragraph.config.editorSetting) {
      throw new Error('editorSetting is required');
    }
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

      if (magic && this.heliumService.getSpellByMagic(magic)) {
        this.runParagraphUsingSpell(text, magic, propagated);
        this.runParagraphAfter(text);
      } else {
        this.runParagraphUsingBackendInterpreter(text);
        this.runParagraphAfter(text);
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

  setEditorHide(editorHide: boolean) {
    this.paragraph.config.editorHide = editorHide;
    this.cdr.markForCheck();
  }

  editorHideChange(event: boolean) {
    this.setEditorHide(event);
    this.commitParagraph();
  }

  setTitleShow(titleShow: boolean) {
    this.paragraph.config.title = titleShow;
    this.cdr.markForCheck();
  }

  titleShowChange(event: boolean) {
    this.setTitleShow(event);
    this.commitParagraph();
  }

  setEnabled(enabled: boolean) {
    this.paragraph.config.enabled = enabled;
    this.cdr.markForCheck();
  }

  enabledChange(event: boolean) {
    this.setEnabled(event);
    this.commitParagraph();
  }

  setTableHide(tableHide: boolean) {
    this.paragraph.config.tableHide = tableHide;
    this.cdr.markForCheck();
  }

  tableHideChange(event: boolean) {
    this.setTableHide(event);
    this.commitParagraph();
  }

  setRunOnSelection(runOnSelection: boolean) {
    this.paragraph.config.runOnSelectionChange = runOnSelection;
    this.cdr.markForCheck();
  }

  runOnSelectionChange(event: boolean) {
    this.setRunOnSelection(event);
    this.commitParagraph();
  }

  setShowLineNumbers(showLineNumbers: boolean) {
    this.paragraph.config.lineNumbers = showLineNumbers;
    this.cdr.markForCheck();
  }

  showLineNumbersChange(event: boolean) {
    this.setShowLineNumbers(event);
    this.commitParagraph();
  }

  colWidthChange(colWidth: number, needCommit: boolean, updateResult = true) {
    this.paragraph.config.colWidth = colWidth;
    this.changeColWidth(needCommit, updateResult);
  }

  fontSizeChange(fontSize: number) {
    this.paragraph.config.fontSize = fontSize;
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

  moveParagraphUp() {
    const newIndex = this.note.paragraphs.findIndex(p => p.id === this.paragraph.id) - 1;
    if (newIndex < 0 || newIndex >= this.note.paragraphs.length) {
      return;
    }
    this.messageService.moveParagraph(this.paragraph.id, newIndex);
  }

  moveParagraphDown() {
    const newIndex = this.note.paragraphs.findIndex(p => p.id === this.paragraph.id) + 1;
    if (newIndex < 0 || newIndex >= this.note.paragraphs.length) {
      return;
    }
    this.messageService.moveParagraph(this.paragraph.id, newIndex);
  }

  clearParagraphOutput() {
    if (!this.isEntireNoteRunning) {
      this.messageService.paragraphClearOutput(this.paragraph.id);
    }
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
    if (!this.paragraph.config.results) {
      throw new Error('paragraph.config.results is required');
    }
    this.paragraph.config.results[index] = configResult;
    this.commitParagraph();
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
    public messageService: MessageService,
    private heliumService: HeliumService,
    private host: ElementRef,
    private noteVarShareService: NoteVarShareService,
    private nzModalService: NzModalService,
    private shortcutService: ShortcutService,
    noteStatusService: NoteStatusService,
    cdr: ChangeDetectorRef,
    ngZService: NgZService
  ) {
    super(messageService, noteStatusService, ngZService, cdr);
    this.keyBinderService = new KeyBinder(this.destroy$, this.host, this.shortcutService);
  }

  private handleKeyEvent(action: ParagraphActions, event: NullableKeyboardEvent) {
    const target = (event?.target || null) as HTMLElement | null;

    // Skip handling shortcut if focused element is an input (by Dynamic form)
    if (target?.tagName === 'INPUT') {
      return; // ignore shortcut to make input work
    }

    const handlerFn = this[ParagraphActionToHandlerName[action] as keyof AngularKeyboardEventHandler];
    if (!handlerFn) {
      return;
    }
    event?.preventDefault();
    handlerFn.call(this);
  }

  initKeyBindingsOnMonaco(editor: MonacoEditor.IStandaloneCodeEditor) {
    this.keyBinderService.initKeyBindingsOnMonaco(editor);
  }

  ngOnInit() {
    this.keyBinderService.initKeyBindingsOnAngular();
    this.keyBinderService
      .keyEvent()
      .pipe(takeUntil(this.destroy$))
      .subscribe(event => {
        this.handleKeyEvent(event.action, event.event);
        this.notebookParagraphCodeEditorComponent?.handleKeyEvent(event.action, event.event);
      });
    this.setResults(this.paragraph);
    this.originalText = this.paragraph.text;
    this.isEntireNoteRunning = this.noteStatusService.isEntireNoteRunning(this.note);
    this.isParagraphRunning = this.noteStatusService.isParagraphRunning(this.paragraph);
    this.noteVarShareService.set(this.paragraph.id + '_paragraphScope', this);
    this.initializeDefault(this.paragraph.config, this.paragraph.settings);
    this.angularContextManager
      .runParagraphAction()
      .pipe(takeUntil(this.destroy$))
      .subscribe(id => {
        if (id === this.paragraph.id) {
          this.runParagraph();
        }
      });
    this.angularContextManager
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

  scrollIfNeeded(): void {
    if (this.scrolled && this.host.nativeElement) {
      setTimeout(() => {
        this.host.nativeElement.scrollIntoView();
      });
    }
  }

  handleRun() {
    this.runParagraph();
  }

  handleRunAbove() {
    this.waitConfirmFromEdit = true;
    this.runAllAbove();
  }

  handleRunBelow() {
    this.waitConfirmFromEdit = true;
    this.runAllBelowAndCurrent();
  }

  handleCancel() {
    this.cancelParagraph();
  }

  handleDelete() {
    this.removeParagraph();
  }

  handleInsertAbove() {
    this.insertParagraph('above');
  }

  handleInsertBelow() {
    this.insertParagraph('below');
  }

  handleInsertCopyOfParagraphBelow() {
    this.cloneParagraph('below');
  }

  handleMoveParagraphUp() {
    this.moveParagraphUp();
  }

  handleMoveParagraphDown() {
    this.moveParagraphDown();
  }

  handleSwitchEnable() {
    this.paragraph.config.enabled = !this.paragraph.config.enabled;
    this.commitParagraph();
  }

  handleSwitchOutputShow() {
    this.setTableHide(!this.paragraph.config.tableHide);
    this.commitParagraph();
  }

  handleSwitchLineNumber() {
    this.paragraph.config.lineNumbers = !this.paragraph.config.lineNumbers;
    this.commitParagraph();
  }

  handleSwitchTitleShow() {
    this.paragraph.config.title = !this.paragraph.config.title;
    this.commitParagraph();
  }

  handleClear() {
    this.clearParagraphOutput();
  }

  handleLink() {
    this.openSingleParagraph(this.paragraph.id);
  }

  handleReduceWidth() {
    if (!this.paragraph.config.colWidth) {
      throw new Error('colWidth is required');
    }
    this.paragraph.config.colWidth = Math.max(1, this.paragraph.config.colWidth - 1);
    this.cdr.markForCheck();
    this.changeColWidth(true);
  }

  handleIncreaseWidth() {
    if (!this.paragraph.config.colWidth) {
      throw new Error('colWidth is required');
    }
    this.paragraph.config.colWidth = Math.min(12, this.paragraph.config.colWidth + 1);
    this.cdr.markForCheck();
    this.changeColWidth(true);
  }

  handleFindInCode() {
    this.searchCode.emit();
  }

  ngOnChanges(changes: SimpleChanges): void {
    const { index, select, scrolled } = changes;
    if (
      (index && index.currentValue !== index.previousValue && this.select) ||
      (select && select.currentValue === true && select.previousValue !== true)
    ) {
      setTimeout(() => {
        if (this.mode === 'command' && this.host.nativeElement) {
          (this.host.nativeElement as HTMLElement).focus();
        }
      });
    }
    if (scrolled) {
      this.scrollIfNeeded();
    }
  }

  getElement(): HTMLElement {
    return this.host && this.host.nativeElement;
  }

  ngAfterViewInit(): void {
    this.scrollIfNeeded();
  }

  ngOnDestroy(): void {
    super.ngOnDestroy();
  }
}
