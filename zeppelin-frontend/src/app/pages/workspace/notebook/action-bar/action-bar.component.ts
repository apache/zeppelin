import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  EventEmitter,
  Inject,
  Input,
  OnInit,
  Output
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { NzMessageService, NzModalService } from 'ng-zorro-antd';

import { MessageListener, MessageListenersManager } from '@zeppelin/core';
import { TRASH_FOLDER_ID_TOKEN } from '@zeppelin/interfaces';
import { Note, OP, RevisionListItem } from '@zeppelin/sdk';
import { MessageService, NoteActionService, NoteStatusService, SaveAsService, TicketService } from '@zeppelin/services';

import { NoteCreateComponent } from '@zeppelin/share/note-create/note-create.component';

@Component({
  selector: 'zeppelin-notebook-action-bar',
  templateUrl: './action-bar.component.html',
  styleUrls: ['./action-bar.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotebookActionBarComponent extends MessageListenersManager implements OnInit {
  @Input() note: Note['note'];
  @Input() isOwner = true;
  @Input() looknfeel: string;
  @Input() noteRevisions: RevisionListItem[] = [];
  @Input() currentRevision: string;
  @Input() collaborativeMode = false;
  @Input() collaborativeModeUsers = [];
  @Input() revisionView = false;
  @Input() activatedExtension: 'interpreter' | 'permissions' | 'revisions' | 'hide' = 'hide';
  @Output() readonly activatedExtensionChange = new EventEmitter<
    'interpreter' | 'permissions' | 'revisions' | 'hide'
  >();
  @Output() readonly editorHideChange = new EventEmitter<boolean>();
  @Output() readonly tableHideChange = new EventEmitter<boolean>();
  lfOption: Array<'report' | 'default' | 'simple'> = ['default', 'simple', 'report'];
  isNoteParagraphRunning = false;
  principal = this.ticketService.ticket.principal;
  editorHide = false;
  commitVisible = false;
  tableHide = false;
  isRevisionSupported = JSON.parse(this.ticketService.configuration.isRevisionSupported);
  cronOption = [
    { name: 'None', value: undefined },
    { name: '1m', value: '0 0/1 * * * ?' },
    { name: '5m', value: '0 0/5 * * * ?' },
    { name: '1h', value: '0 0 0/1 * * ?' },
    { name: '3h', value: '0 0 0/3 * * ?' },
    { name: '6h', value: '0 0 0/6 * * ?' },
    { name: '12h', value: '0 0 0/12 * * ?' },
    { name: '1d', value: '0 0 0 * * ?' }
  ];

  updateNoteName(name: string) {
    const trimmedNewName = name.trim();
    if (trimmedNewName.length > 0 && this.note.name !== trimmedNewName) {
      this.note.name = trimmedNewName;
      this.messageService.noteRename(this.note.id, this.note.name, true);
    }
  }

  visitRevision(revision: RevisionListItem) {
    if (revision.id) {
      if (revision.id === 'Head') {
        this.router.navigate(['/notebook', this.activatedRoute.snapshot.params.noteId]).then();
      } else {
        this.router.navigate(['/notebook', this.activatedRoute.snapshot.params.noteId, 'revision', revision.id]).then();
      }
    } else {
      this.nzMessageService.warning('There is a problem with this Revision');
    }
  }

  checkpointNote(value: string, e: MouseEvent) {
    e.preventDefault();
    this.commitVisible = false;
    this.messageService.checkpointNote(this.note.id, value);
  }

  setNoteRevision() {
    const { revisionId } = this.activatedRoute.snapshot.params;
    if (revisionId) {
      this.nzModalService.confirm({
        nzTitle: 'Set revision',
        nzContent: 'Set notebook head to current revision?',
        nzOnOk: () => {
          this.messageService.setNoteRevision(this.note.id, revisionId);
        }
      });
    }
  }

  toggleExtension(extension: 'interpreter' | 'permissions' | 'revisions' | 'hide') {
    if (this.activatedExtension === extension) {
      this.activatedExtension = 'hide';
    } else {
      this.activatedExtension = extension;
    }
    this.activatedExtensionChange.emit(this.activatedExtension);
  }

  @MessageListener(OP.PARAGRAPH)
  paragraphUpdate() {
    this.updateIsNoteParagraphRunning();
    this.cdr.markForCheck();
  }

  runAllParagraphs() {
    this.messageService.runAllParagraphs(
      this.note.id,
      this.note.paragraphs.map(p => {
        return {
          id: p.id,
          title: p.title,
          paragraph: p.text,
          config: p.config,
          params: p.settings.params
        };
      })
    );
  }

  clearAllParagraphOutput() {
    this.messageService.paragraphClearAllOutput(this.note.id);
  }

  setCronScheduler(cronExpr: string) {
    if (cronExpr) {
      if (!this.note.config.cronExecutingUser) {
        this.note.config.cronExecutingUser = this.ticketService.ticket.principal;
      }
      if (!this.note.config.cronExecutingRoles) {
        this.note.config.cronExecutingRoles = this.ticketService.ticket.roles;
      }
    } else {
      this.note.config.cronExecutingUser = '';
      this.note.config.cronExecutingRoles = '';
    }
    this.note.config.cron = cronExpr;
    this.setConfig();
  }

  setReleaseResource(releaseresource: boolean) {
    this.note.config.releaseresource = releaseresource;
    this.setConfig();
  }

  setConfig() {
    // TODO
  }

  cloneNote() {
    this.nzModalService.create({
      nzTitle: 'Clone Note',
      nzContent: NoteCreateComponent,
      nzComponentParams: {
        cloneNote: this.note
      },
      nzFooter: null
    });
  }

  exportNote() {
    const sizeLimit = +this.ticketService.configuration['zeppelin.websocket.max.text.message.size'];
    const jsonContent = JSON.stringify(this.note);
    if (jsonContent.length > sizeLimit) {
      this.nzModalService.confirm({
        nzTitle: `Note size exceeds importable limit (${sizeLimit})`,
        nzContent: 'Do you still want to export this note?',
        nzOnOk: () => {
          this.saveAsService.saveAs(jsonContent, this.note.name, 'zpln');
        }
      });
    } else {
      this.saveAsService.saveAs(jsonContent, this.note.name, 'zpln');
    }
  }

  toggleAllEditor() {
    this.editorHide = !this.editorHide;
    this.editorHideChange.emit(this.editorHide);
  }

  toggleAllTable() {
    this.tableHide = !this.tableHide;
    this.tableHideChange.emit(this.tableHide);
  }

  searchCode() {
    // TODO
  }

  deleteNote() {
    this.messageService.deleteNote(this.note.id);
  }

  moveNoteToTrash() {
    this.messageService.moveNoteToTrash(this.note.id);
  }

  get isTrash() {
    return this.noteStatusService.isTrash(this.note);
  }

  get viewOnly(): boolean {
    return this.noteStatusService.viewOnly(this.note);
  }

  updateIsNoteParagraphRunning() {
    this.isNoteParagraphRunning = this.noteStatusService.isNoteParagraphRunning(this.note);
  }

  showShortCut() {
    // TODO
  }

  togglePermissions() {
    this.toggleExtension('permissions');
  }

  setLookAndFeel(lf: 'report' | 'default' | 'simple') {
    this.note.config.looknfeel = lf;
    if (!this.activatedRoute.snapshot.params.revisionId) {
      this.messageService.updateNote(this.note.id, this.note.name, this.note.config);
    }
  }

  toggleNotePersonalizedMode() {
    const modeText = this.note.config.personalizedMode === 'true' ? 'collaborate' : 'personalize';
    if (this.isOwner) {
      this.nzModalService.confirm({
        nzTitle: 'Setting the result display',
        nzContent: `Do you want to ${modeText} your analysis?`,
        nzOnOk: () => {
          if (this.note.config.personalizedMode === undefined || this.note.config.personalizedMode === 'true') {
            this.note.config.personalizedMode = 'false';
          } else {
            this.note.config.personalizedMode = 'true';
          }
          this.messageService.updatePersonalizedMode(this.note.id, this.note.config.personalizedMode);
        }
      });
    }
  }

  get getCronOptionNameFromValue() {
    if (!this.note.config.cron) {
      return '';
    } else if (this.cronOption.find(cron => cron.value === this.note.config.cron)) {
      return this.cronOption.find(cron => cron.value === this.note.config.cron).name;
    } else {
      return this.note.config.cron;
    }
  }

  constructor(
    public messageService: MessageService,
    private nzModalService: NzModalService,
    private ticketService: TicketService,
    private nzMessageService: NzMessageService,
    private router: Router,
    private cdr: ChangeDetectorRef,
    private noteActionService: NoteActionService,
    private noteStatusService: NoteStatusService,
    @Inject(TRASH_FOLDER_ID_TOKEN) public TRASH_FOLDER_ID: string,
    private activatedRoute: ActivatedRoute,
    private saveAsService: SaveAsService
  ) {
    super(messageService);
  }

  ngOnInit(): void {
    this.updateIsNoteParagraphRunning();
  }
}
