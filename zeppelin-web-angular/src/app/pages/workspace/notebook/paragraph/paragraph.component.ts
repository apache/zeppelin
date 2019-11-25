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

import DiffMatchPatch from 'diff-match-patch';
import { isEmpty, isEqual } from 'lodash';
import { NzModalService } from 'ng-zorro-antd/modal';

import { MessageListener, MessageListenersManager } from '@zeppelin/core';
import {
  AngularObjectRemove,
  AngularObjectUpdate,
  GraphConfig,
  InterpreterBindingItem,
  MessageReceiveDataTypeMap,
  Note,
  OP,
  ParagraphConfig,
  ParagraphConfigResult,
  ParagraphEditorSetting,
  ParagraphItem,
  ParagraphIResultsMsgItem
} from '@zeppelin/sdk';
import {
  HeliumService,
  MessageService,
  NgZService,
  NoteStatusService,
  NoteVarShareService,
  ParagraphActions,
  ParagraphStatus,
  ShortcutsMap,
  ShortcutService
} from '@zeppelin/services';
import { SpellResult } from '@zeppelin/spell/spell-result';

import { NgTemplateAdapterService } from '@zeppelin/services/ng-template-adapter.service';
import { NzResizeEvent } from 'ng-zorro-antd/resizable';
import { NotebookParagraphCodeEditorComponent } from './code-editor/code-editor.component';
import { NotebookParagraphResultComponent } from './result/result.component';

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
export class NotebookParagraphComponent extends MessageListenersManager implements OnInit, OnChanges, OnDestroy {
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
  dirtyText: string;
  originalText: string;
  isEntireNoteRunning = false;
  diffMatchPatch = new DiffMatchPatch();
  isParagraphRunning = false;
  results = [];
  configs = {};
  progress = 0;
  colWidthOption = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12];
  editorSetting: ParagraphEditorSetting = {};

  @MessageListener(OP.PROGRESS)
  onProgress(data: MessageReceiveDataTypeMap[OP.PROGRESS]) {
    if (data.id === this.paragraph.id) {
      this.progress = data.progress;
      this.cdr.markForCheck();
    }
  }

  @MessageListener(OP.NOTE_RUNNING_STATUS)
  noteRunningStatusChange(data: MessageReceiveDataTypeMap[OP.NOTE_RUNNING_STATUS]) {
    this.isEntireNoteRunning = data.status;
    this.cdr.markForCheck();
  }

  @MessageListener(OP.PARAS_INFO)
  updateParaInfos(data: MessageReceiveDataTypeMap[OP.PARAS_INFO]) {
    if (this.paragraph.id === data.id) {
      this.paragraph.runtimeInfos = data.infos;
      this.cdr.markForCheck();
    }
  }

  @MessageListener(OP.EDITOR_SETTING)
  getEditorSetting(data: MessageReceiveDataTypeMap[OP.EDITOR_SETTING]) {
    if (this.paragraph.id === data.paragraphId) {
      this.paragraph.config.editorSetting = { ...this.paragraph.config.editorSetting, ...data.editor };
      this.cdr.markForCheck();
    }
  }

  @MessageListener(OP.PARAGRAPH)
  paragraphData(data: MessageReceiveDataTypeMap[OP.PARAGRAPH]) {
    const oldPara = this.paragraph;
    const newPara = data.paragraph;
    if (this.isUpdateRequired(oldPara, newPara)) {
      this.updateParagraph(oldPara, newPara, () => {
        if (newPara.results && newPara.results.msg) {
          // tslint:disable-next-line:no-for-in-array
          for (const i in newPara.results.msg) {
            if (newPara.results.msg[i]) {
              const newResult = newPara.results.msg ? newPara.results.msg[i] : new ParagraphIResultsMsgItem();
              const oldResult =
                oldPara.results && oldPara.results.msg ? oldPara.results.msg[i] : new ParagraphIResultsMsgItem();
              const newConfig = newPara.config.results ? newPara.config.results[i] : { graph: new GraphConfig() };
              const oldConfig = oldPara.config.results ? oldPara.config.results[i] : { graph: new GraphConfig() };
              if (!isEqual(newResult, oldResult) || !isEqual(newConfig, oldConfig)) {
                const resultComponent = this.notebookParagraphResultComponents.toArray()[i];
                if (resultComponent) {
                  resultComponent.updateResult(newConfig, newResult);
                }
              }
            }
          }
        }
        this.cdr.markForCheck();
      });
      this.cdr.markForCheck();
    }
  }

  @MessageListener(OP.PATCH_PARAGRAPH)
  patchParagraph(data: MessageReceiveDataTypeMap[OP.PATCH_PARAGRAPH]) {
    if (data.paragraphId === this.paragraph.id) {
      let patch = data.patch;
      patch = this.diffMatchPatch.patch_fromText(patch);
      if (!this.paragraph.text) {
        this.paragraph.text = '';
      }
      this.paragraph.text = this.diffMatchPatch.patch_apply(patch, this.paragraph.text)[0];
      this.originalText = this.paragraph.text;
      this.cdr.markForCheck();
    }
  }

  @MessageListener(OP.ANGULAR_OBJECT_UPDATE)
  angularObjectUpdate(data: AngularObjectUpdate) {
    if (data.paragraphId === this.paragraph.id) {
      const { name, object } = data.angularObject;
      this.ngZService.setContextValue(name, object, data.paragraphId, false);
    }
  }

  @MessageListener(OP.ANGULAR_OBJECT_REMOVE)
  angularObjectRemove(data: AngularObjectRemove) {
    if (data.paragraphId === this.paragraph.id) {
      this.ngZService.unsetContextValue(data.name, data.paragraphId, false);
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

  updateParagraph(oldPara: ParagraphItem, newPara: ParagraphItem, updateCallback: () => void) {
    // 1. can't update on revision view
    if (!this.revisionView) {
      // 2. get status, refreshed
      const statusChanged = newPara.status !== oldPara.status;
      const resultRefreshed =
        newPara.dateFinished !== oldPara.dateFinished ||
        isEmpty(newPara.results) !== isEmpty(oldPara.results) ||
        newPara.status === ParagraphStatus.ERROR ||
        (newPara.status === ParagraphStatus.FINISHED && statusChanged);

      // 3. update texts managed by paragraph
      this.updateAllScopeTexts(oldPara, newPara);
      // 4. execute callback to update result
      updateCallback();

      // 5. update remaining paragraph objects
      this.updateParagraphObjectWhenUpdated(newPara);

      // 6. handle scroll down by key properly if new paragraph is added
      if (statusChanged || resultRefreshed) {
        // when last paragraph runs, zeppelin automatically appends new paragraph.
        // this broadcast will focus to the newly inserted paragraph
        // TODO(hsuanxyz)
      }
      this.cdr.markForCheck();
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

  runParagraphUsingSpell(paragraphText: string, magic: string, propagated: boolean) {
    // TODO(hsuanxyz)
  }

  runParagraphUsingBackendInterpreter(paragraphText: string) {
    this.messageService.runParagraph(
      this.paragraph.id,
      this.paragraph.title,
      paragraphText,
      this.paragraph.config,
      this.paragraph.settings.params
    );
  }

  cancelParagraph() {
    if (!this.isEntireNoteRunning) {
      this.messageService.cancelParagraph(this.paragraph.id);
    }
  }

  updateAllScopeTexts(oldPara: ParagraphItem, newPara: ParagraphItem) {
    if (oldPara.text !== newPara.text) {
      if (this.dirtyText) {
        // check if editor has local update
        if (this.dirtyText === newPara.text) {
          // when local update is the same from remote, clear local update
          this.paragraph.text = newPara.text;
          this.dirtyText = undefined;
          this.originalText = newPara.text;
        } else {
          // if there're local update, keep it.
          this.paragraph.text = newPara.text;
        }
      } else {
        this.paragraph.text = newPara.text;
        this.originalText = newPara.text;
      }
    }
    this.cdr.markForCheck();
  }

  updateParagraphObjectWhenUpdated(newPara: ParagraphItem) {
    if (this.paragraph.config.colWidth !== newPara.config.colWidth) {
      this.changeColWidth(false);
    }
    this.paragraph.aborted = newPara.aborted;
    this.paragraph.user = newPara.user;
    this.paragraph.dateUpdated = newPara.dateUpdated;
    this.paragraph.dateCreated = newPara.dateCreated;
    this.paragraph.dateFinished = newPara.dateFinished;
    this.paragraph.dateStarted = newPara.dateStarted;
    this.paragraph.errorMessage = newPara.errorMessage;
    this.paragraph.jobName = newPara.jobName;
    this.paragraph.title = newPara.title;
    this.paragraph.lineNumbers = newPara.lineNumbers;
    this.paragraph.status = newPara.status;
    this.paragraph.fontSize = newPara.fontSize;
    if (newPara.status !== ParagraphStatus.RUNNING) {
      this.paragraph.results = newPara.results;
    }
    this.paragraph.settings = newPara.settings;
    this.paragraph.runtimeInfos = newPara.runtimeInfos;
    this.isParagraphRunning = this.noteStatusService.isParagraphRunning(newPara);
    this.paragraph.config = newPara.config;
    this.initializeDefault(this.paragraph.config);
    this.setResults();
    this.cdr.markForCheck();
  }

  isUpdateRequired(oldPara: ParagraphItem, newPara: ParagraphItem): boolean {
    return (
      newPara.id === oldPara.id &&
      (newPara.dateCreated !== oldPara.dateCreated ||
        newPara.text !== oldPara.text ||
        newPara.dateFinished !== oldPara.dateFinished ||
        newPara.dateStarted !== oldPara.dateStarted ||
        newPara.dateUpdated !== oldPara.dateUpdated ||
        newPara.status !== oldPara.status ||
        newPara.jobName !== oldPara.jobName ||
        newPara.title !== oldPara.title ||
        isEmpty(newPara.results) !== isEmpty(oldPara.results) ||
        newPara.errorMessage !== oldPara.errorMessage ||
        !isEqual(newPara.settings, oldPara.settings) ||
        !isEqual(newPara.config, oldPara.config) ||
        !isEqual(newPara.runtimeInfos, oldPara.runtimeInfos))
    );
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

  setResults() {
    if (this.paragraph.results) {
      this.results = this.paragraph.results.msg;
      this.configs = this.paragraph.config.results;
    }
    if (!this.paragraph.config) {
      this.paragraph.config = {};
    }
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

  initializeDefault(config: ParagraphConfig) {
    const forms = this.paragraph.settings.forms;

    if (!config.colWidth) {
      config.colWidth = 12;
    }

    if (!config.fontSize) {
      config.fontSize = 9;
    }

    if (config.enabled === undefined) {
      config.enabled = true;
    }

    for (const idx in forms) {
      if (forms[idx]) {
        if (forms[idx].options) {
          if (config.runOnSelectionChange === undefined) {
            config.runOnSelectionChange = true;
          }
        }
      }
    }

    if (!config.results) {
      config.results = {};
    }

    if (!config.editorSetting) {
      config.editorSetting = {};
    } else if (config.editorSetting.editOnDblClick) {
      this.editorSetting.isOutputHidden = config.editorSetting.editOnDblClick;
    }
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

  trackByIndexFn(index: number) {
    return index;
  }

  constructor(
    private heliumService: HeliumService,
    private noteStatusService: NoteStatusService,
    public messageService: MessageService,
    private nzModalService: NzModalService,
    private noteVarShareService: NoteVarShareService,
    private cdr: ChangeDetectorRef,
    private ngZService: NgZService,
    private shortcutService: ShortcutService,
    private host: ElementRef,
    private ngTemplateAdapterService: NgTemplateAdapterService
  ) {
    super(messageService);
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
              this.moveUpParagraph();
              break;
            case ParagraphActions.MoveToDown:
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
          (this.host.nativeElement as HTMLElement).focus();
        });
      }
    }
  }

  ngOnDestroy(): void {
    super.ngOnDestroy();
    this.ngZService.removeParagraph(this.paragraph.id);
  }
}
