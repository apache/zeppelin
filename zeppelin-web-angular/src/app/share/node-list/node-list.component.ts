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

import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Inject, Input, OnInit } from '@angular/core';
import { TRASH_FOLDER_ID_TOKEN } from '@zeppelin/interfaces';

import { NzTreeNode } from 'ng-zorro-antd/core';
import { NzModalService } from 'ng-zorro-antd/modal';

import { MessageListener, MessageListenersManager } from '@zeppelin/core';
import { MessageReceiveDataTypeMap, OP } from '@zeppelin/sdk';
import { MessageService, NoteActionService, NoteListService } from '@zeppelin/services';

@Component({
  selector: 'zeppelin-node-list',
  templateUrl: './node-list.component.html',
  providers: [NoteListService],
  styleUrls: ['./node-list.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NodeListComponent extends MessageListenersManager implements OnInit {
  @Input() headerMode = false;
  searchValue: string;
  nodes = [];
  activatedId: string;

  activeNote(id: string) {
    this.activatedId = id;
  }

  moveFolderToTrash(id: string) {
    return this.messageService.moveFolderToTrash(id);
  }

  restoreFolder(id: string) {
    return this.messageService.restoreFolder(id);
  }

  removeFolder(id: string) {
    return this.messageService.removeFolder(id);
  }

  paragraphClearAllOutput(id: string) {
    return this.messageService.paragraphClearAllOutput(id);
  }

  moveNoteToTrash(id: string) {
    return this.messageService.moveNoteToTrash(id);
  }

  restoreNote(id: string) {
    return this.messageService.restoreNote(id);
  }

  deleteNote(id: string) {
    return this.messageService.deleteNote(id);
  }

  restoreAll() {
    return this.messageService.restoreAll();
  }

  emptyTrash() {
    return this.messageService.emptyTrash();
  }

  toggleFolder(node: NzTreeNode) {
    node.isExpanded = !node.isExpanded;
    this.cdr.markForCheck();
  }

  renameNote(id: string, path: string, name: string) {
    this.noteActionService.renameNote(id, path, name);
  }

  renameFolder(path) {
    this.noteActionService.renameFolder(path);
  }

  importNote() {
    this.noteActionService.importNote();
  }

  createNote(path?: string) {
    this.noteActionService.createNote(path);
  }

  @MessageListener(OP.NOTES_INFO)
  getNotes(data: MessageReceiveDataTypeMap[OP.NOTES_INFO]) {
    this.noteListService.setNotes(data.notes);
    this.nodes = this.noteListService.notes.root.children
      .sort((v1, v2) => this.noteComparator(v1, v2))
      .map(item => {
        return { ...item, key: item.id };
      });
    this.cdr.markForCheck();
  }

  getNoteName(note) {
    if (note.title === undefined || note.title.trim() === '') {
      return 'Note ' + note.id;
    } else {
      return note.title;
    }
  }

  noteComparator(v1, v2) {
    const note1 = v1;
    const note2 = v2;
    if (note1.id === this.TRASH_FOLDER_ID) {
      return 1;
    }
    if (note2.id === this.TRASH_FOLDER_ID) {
      return -1;
    }
    if (note1.children === undefined && note2.children !== undefined) {
      return 1;
    }
    if (note1.children !== undefined && note2.children === undefined) {
      return -1;
    }
    const noteName1 = this.getNoteName(note1);
    const noteName2 = this.getNoteName(note2);
    return noteName1.localeCompare(noteName2);
  }

  constructor(
    private noteListService: NoteListService,
    public messageService: MessageService,
    @Inject(TRASH_FOLDER_ID_TOKEN) public TRASH_FOLDER_ID: string,
    private nzModalService: NzModalService,
    private noteActionService: NoteActionService,
    private cdr: ChangeDetectorRef
  ) {
    super(messageService);
  }

  ngOnInit() {
    this.messageService.listNodes();
  }
}
