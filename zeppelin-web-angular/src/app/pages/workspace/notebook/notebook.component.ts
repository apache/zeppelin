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
  OnDestroy,
  OnInit,
  QueryList,
  ViewChildren
} from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { isNil } from 'lodash';
import { Subject } from 'rxjs';
import { distinctUntilKeyChanged, map, startWith, takeUntil } from 'rxjs/operators';

import { NzResizeEvent } from 'ng-zorro-antd/resizable';

import { MessageListener, MessageListenersManager } from '@zeppelin/core';
import { Permissions } from '@zeppelin/interfaces';
import {
  DynamicFormParams,
  InterpreterBindingItem,
  MessageReceiveDataTypeMap,
  Note,
  OP,
  RevisionListItem
} from '@zeppelin/sdk';
import {
  MessageService,
  NgZService,
  NoteStatusService,
  NoteVarShareService,
  SecurityService,
  TicketService
} from '@zeppelin/services';

import { scrollIntoViewIfNeeded } from '@zeppelin/utility';
import { NotebookParagraphComponent } from './paragraph/paragraph.component';

@Component({
  selector: 'zeppelin-notebook',
  templateUrl: './notebook.component.html',
  styleUrls: ['./notebook.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotebookComponent extends MessageListenersManager implements OnInit, OnDestroy {
  @ViewChildren(NotebookParagraphComponent) listOfNotebookParagraphComponent!: QueryList<NotebookParagraphComponent>;
  private destroy$ = new Subject();
  note?: Exclude<Note['note'], undefined>;
  permissions?: Permissions;
  selectId: string | null = null;
  scrolledId: string | null = null;
  isOwner = true;
  noteRevisions: RevisionListItem[] = [];
  currentRevision?: string;
  collaborativeMode = false;
  revisionView = false;
  collaborativeModeUsers: string[] = [];
  isNoteDirty: boolean | null = false;
  isShowNoteForms = false;
  saveTimer: ReturnType<typeof setTimeout> | null = null;
  interpreterBindings: InterpreterBindingItem[] = [];
  activatedExtension: 'interpreter' | 'permissions' | 'revisions' | 'hide' = 'hide';
  sidebarWidth = 370;
  sidebarAnimationFrame = -1;
  isSidebarOpen = false;

  @MessageListener(OP.NOTE)
  getNote(data: MessageReceiveDataTypeMap[OP.NOTE]) {
    const note = data.note;
    if (isNil(note)) {
      this.router.navigate(['/']).then();
    } else {
      this.removeParagraphFromNgZ();
      this.note = note;
      const { paragraphId } = this.activatedRoute.snapshot.params;
      if (paragraphId) {
        this.note = this.cleanParagraphExcept(this.note, paragraphId);
        this.initializeLookAndFeel(this.note);
      } else {
        this.initializeLookAndFeel(this.note);
        this.getInterpreterBindings(this.note);
        this.getPermissions(this.note);
        this.note.config.personalizedMode =
          this.note.config.personalizedMode === undefined ? 'false' : this.note.config.personalizedMode;
      }
      if (this.note!.noteForms && this.note!.noteParams) {
        this.saveNoteForms({
          formsData: {
            forms: this.note!.noteForms,
            params: this.note!.noteParams
          }
        });
      }
      this.titleService.setTitle(this.note?.name + ' - Zeppelin');
      this.cdr.markForCheck();
    }
  }

  @MessageListener(OP.INTERPRETER_BINDINGS)
  loadInterpreterBindings(data: MessageReceiveDataTypeMap[OP.INTERPRETER_BINDINGS]) {
    this.interpreterBindings = data.interpreterBindings;
    if (!this.interpreterBindings.some(item => item.selected)) {
      this.activatedExtension = 'interpreter';
    }
    this.cdr.markForCheck();
  }

  @MessageListener(OP.PARAGRAPH_REMOVED)
  removeParagraph(data: MessageReceiveDataTypeMap[OP.PARAGRAPH_REMOVED]) {
    const { paragraphId } = this.activatedRoute.snapshot.params;
    if (paragraphId || this.revisionView) {
      return;
    }
    if (!this.note) {
      return;
    }
    const definedNote = this.note;
    const paragraphIndex = definedNote.paragraphs.findIndex(p => p.id === data.id);
    definedNote.paragraphs = definedNote.paragraphs.filter((p, index) => index !== paragraphIndex);
    const adjustedCursorIndex =
      paragraphIndex === definedNote.paragraphs.length ? paragraphIndex - 1 : paragraphIndex + 1;
    const targetParagraph = this.listOfNotebookParagraphComponent.find((_, index) => index === adjustedCursorIndex);
    if (targetParagraph) {
      targetParagraph.focusEditor();
    }
    this.cdr.markForCheck();
  }

  @MessageListener(OP.PARAGRAPH_ADDED)
  addParagraph(data: MessageReceiveDataTypeMap[OP.PARAGRAPH_ADDED]) {
    const { paragraphId } = this.activatedRoute.snapshot.params;
    if (paragraphId || this.revisionView) {
      return;
    }
    if (!this.note) {
      return;
    }
    const definedNote = this.note;
    definedNote.paragraphs.splice(data.index, 0, data.paragraph);
    const paragraphIndex = definedNote.paragraphs.findIndex(p => p.id === data.paragraph.id);

    definedNote.paragraphs[paragraphIndex].focus = true;
    this.cdr.markForCheck();
  }

  @MessageListener(OP.SAVE_NOTE_FORMS)
  saveNoteForms(data: MessageReceiveDataTypeMap[OP.SAVE_NOTE_FORMS]) {
    if (!this.note) {
      return;
    }
    const definedNote = this.note;
    definedNote.noteForms = data.formsData.forms;
    definedNote.noteParams = data.formsData.params;
    this.setNoteFormsStatus();
  }

  @MessageListener(OP.NOTE_REVISION)
  getNoteRevision(data: MessageReceiveDataTypeMap[OP.NOTE_REVISION]) {
    const note = data.note;
    if (isNil(note)) {
      this.router.navigate(['/']).then();
    } else {
      this.note = note;
      this.initializeLookAndFeel(this.note);
      this.cdr.markForCheck();
    }
  }

  @MessageListener(OP.SET_NOTE_REVISION)
  setNoteRevision(data: MessageReceiveDataTypeMap[OP.SET_NOTE_REVISION]) {
    const { noteId } = this.activatedRoute.snapshot.params;
    this.router.navigate(['/notebook', noteId]).then();
  }

  @MessageListener(OP.PARAGRAPH_MOVED)
  moveParagraph(data: MessageReceiveDataTypeMap[OP.PARAGRAPH_MOVED]) {
    if (!this.note) {
      return;
    }
    if (!this.revisionView) {
      const movedPara = this.note.paragraphs.find(p => p.id === data.id);
      if (movedPara) {
        const listOfRestPara = this.note.paragraphs.filter(p => p.id !== data.id);
        this.note.paragraphs = [...listOfRestPara.slice(0, data.index), movedPara, ...listOfRestPara.slice(data.index)];
        const paragraphComponent = this.listOfNotebookParagraphComponent.find(e => e.paragraph.id === data.id);
        this.cdr.markForCheck();
        if (paragraphComponent) {
          // Call when next tick
          setTimeout(() => {
            scrollIntoViewIfNeeded(paragraphComponent.getElement());
            paragraphComponent.focusEditor();
          });
        }
      }
    }
  }

  @MessageListener(OP.COLLABORATIVE_MODE_STATUS)
  getCollaborativeModeStatus(data: MessageReceiveDataTypeMap[OP.COLLABORATIVE_MODE_STATUS]) {
    this.collaborativeMode = Boolean(data.status);
    this.collaborativeModeUsers = data.users;
    this.cdr.markForCheck();
  }

  @MessageListener(OP.PATCH_PARAGRAPH)
  patchParagraph(data: MessageReceiveDataTypeMap[OP.PATCH_PARAGRAPH]) {
    this.collaborativeMode = true;
    this.cdr.markForCheck();
  }

  @MessageListener(OP.NOTE_UPDATED)
  noteUpdated(data: MessageReceiveDataTypeMap[OP.NOTE_UPDATED]) {
    if (!this.note) {
      return;
    }
    if (data.name !== this.note.name) {
      this.note.name = data.name;
    }
    this.note.config = data.config;
    this.note.info = data.info;
    this.initializeLookAndFeel(this.note);
    this.cdr.markForCheck();
  }

  @MessageListener(OP.LIST_REVISION_HISTORY)
  listRevisionHistory(data: MessageReceiveDataTypeMap[OP.LIST_REVISION_HISTORY]) {
    this.noteRevisions = data.revisionList;
    if (this.noteRevisions) {
      if (this.noteRevisions.length === 0 || this.noteRevisions[0].id !== 'Head') {
        this.noteRevisions.splice(0, 0, { id: 'Head', message: 'Head' });
      }
      const { revisionId } = this.activatedRoute.snapshot.params;
      if (revisionId) {
        const revisionItemFound = this.noteRevisions.find(r => r.id === revisionId);
        if (!revisionItemFound) {
          throw new Error(`Revision ${revisionId} not found`);
        }
        this.currentRevision = revisionItemFound.message;
      } else {
        this.currentRevision = 'Head';
      }
    }
    this.cdr.markForCheck();
  }

  saveParagraph(id: string) {
    const paragraphFound = this.listOfNotebookParagraphComponent.toArray().find(p => p.paragraph.id === id);
    if (!paragraphFound) {
      throw new Error(`Paragraph ${id} not found`);
    }
    paragraphFound.saveParagraph();
  }

  killSaveTimer() {
    if (this.saveTimer) {
      clearTimeout(this.saveTimer);
      this.saveTimer = null;
    }
  }

  startSaveTimer() {
    this.killSaveTimer();
    this.isNoteDirty = true;
    this.saveTimer = setTimeout(() => {
      this.saveNote();
    }, 10000);
  }

  onParagraphSelect(id: string | null) {
    this.selectId = id;
  }

  onParagraphScrolled(id: string | null) {
    this.scrolledId = id;
  }

  onSelectAtIndex(index: number) {
    if (!this.note) {
      throw new Error(`"note" is not defined. Please check if note data is loaded before calling this method.`);
    }
    const scopeIndex = Math.min(this.note.paragraphs.length, Math.max(0, index));
    if (this.note.paragraphs[scopeIndex]) {
      this.selectId = this.note.paragraphs[scopeIndex].id;
    }
  }

  saveNote() {
    if (this.note && this.note.paragraphs && this.listOfNotebookParagraphComponent) {
      this.listOfNotebookParagraphComponent.toArray().forEach(p => {
        p.saveParagraph();
      });
      this.isNoteDirty = null;
      this.cdr.markForCheck();
    }
  }

  getInterpreterBindings(note: Exclude<Note['note'], undefined>) {
    this.messageService.getInterpreterBindings(note.id);
  }

  getPermissions(note: Exclude<Note['note'], undefined>) {
    this.securityService.getPermissions(note.id).subscribe(data => {
      this.permissions = data;
      this.isOwner = !(
        this.permissions.owners.length && this.permissions.owners.indexOf(this.ticketService.ticket.principal) < 0
      );
      this.cdr.markForCheck();
    });
  }

  get viewOnly(): boolean {
    if (!this.note) {
      return false;
    }
    return this.noteStatusService.viewOnly(this.note);
  }

  initializeLookAndFeel(note: Exclude<Note['note'], undefined>) {
    note.config.looknfeel = note.config.looknfeel || 'default';
    if (note.paragraphs && note.paragraphs[0]) {
      note.paragraphs[0].focus = true;
    }
  }

  cleanParagraphExcept(note: Exclude<Note['note'], undefined>, paragraphId: string) {
    const targetParagraph = note.paragraphs.find(p => p.id === paragraphId);
    if (!targetParagraph) {
      throw new Error(`Paragraph ${paragraphId} not found`);
    }
    const config = targetParagraph.config || {};
    config.editorHide = true;
    config.tableHide = false;
    const paragraphs = [{ ...targetParagraph, config }];
    return { ...note, paragraphs };
  }

  setAllParagraphTableHide(tableHide: boolean) {
    this.listOfNotebookParagraphComponent.forEach(p => p.setTableHide(tableHide));
  }

  setAllParagraphEditorHide(editorHide: boolean) {
    this.listOfNotebookParagraphComponent.forEach(p => p.setEditorHide(editorHide));
  }

  onNoteFormChange(noteParams: DynamicFormParams) {
    if (!this.note) {
      throw new Error(`"note" is not defined. Please check if note data is loaded before calling this method.`);
    }
    this.messageService.saveNoteForms({
      noteParams,
      id: this.note.id
    });
  }

  onFormNameRemove(formName: string) {
    if (!this.note) {
      throw new Error(`"note" is not defined. Please check if note data is loaded before calling this method.`);
    }
    this.messageService.removeNoteForms(this.note, formName);
  }

  onNoteTitleChange(noteFormTitle: string) {
    if (!this.note) {
      throw new Error(`"note" is not defined. Please check if note data is loaded before calling this method.`);
    }
    this.messageService.updateNote(this.note.id, this.note.name, {
      ...this.note.config,
      noteFormTitle
    });
  }

  setNoteFormsStatus() {
    this.isShowNoteForms = !!this.note && this.note.noteForms && Object.keys(this.note.noteForms).length !== 0;
    this.cdr.markForCheck();
  }

  onSidebarOpenChange(isSidebarOpen: boolean) {
    this.isSidebarOpen = isSidebarOpen;
  }

  onResizeSidebar({ width }: NzResizeEvent): void {
    cancelAnimationFrame(this.sidebarAnimationFrame);
    this.sidebarAnimationFrame = requestAnimationFrame(() => {
      this.sidebarWidth = width!;
    });
  }

  constructor(
    public messageService: MessageService,
    protected ngZService: NgZService,
    private activatedRoute: ActivatedRoute,
    private cdr: ChangeDetectorRef,
    private noteStatusService: NoteStatusService,
    private noteVarShareService: NoteVarShareService,
    private ticketService: TicketService,
    private securityService: SecurityService,
    private router: Router,
    private titleService: Title
  ) {
    super(messageService);
  }

  ngOnInit() {
    this.activatedRoute.queryParamMap
      .pipe(
        startWith(this.activatedRoute.snapshot.queryParamMap),
        takeUntil(this.destroy$),
        map(data => data.get('paragraph'))
      )
      .subscribe(id => {
        this.onParagraphSelect(id);
        this.onParagraphScrolled(id);
      });
    this.activatedRoute.params.pipe(takeUntil(this.destroy$), distinctUntilKeyChanged('noteId')).subscribe(() => {
      this.noteVarShareService.clear();
    });
    this.activatedRoute.params.pipe(takeUntil(this.destroy$)).subscribe(param => {
      const { noteId, revisionId } = param;
      if (revisionId) {
        this.messageService.noteRevision(noteId, revisionId);
      } else {
        this.messageService.getNote(noteId);
      }
      this.revisionView = !!revisionId;
      this.cdr.markForCheck();
      this.messageService.listRevisionHistory(noteId);
      // TODO(hsuanxyz) scroll to current paragraph
    });
    this.revisionView = !!this.activatedRoute.snapshot.params.revisionId;
  }

  removeParagraphFromNgZ(): void {
    if (this.note && Array.isArray(this.note.paragraphs)) {
      this.note.paragraphs.forEach(p => {
        this.ngZService.removeParagraph(p.id);
      });
    }
  }

  ngOnDestroy(): void {
    super.ngOnDestroy();
    this.killSaveTimer();
    this.saveNote();
    this.destroy$.next();
    this.destroy$.complete();
    this.titleService.setTitle('Zeppelin');
  }
}
