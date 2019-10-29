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

import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnInit } from '@angular/core';

import { NzModalRef } from 'ng-zorro-antd';

import { MessageService } from '@zeppelin/services/message.service';
import { NoteListService } from '@zeppelin/services/note-list.service';

@Component({
  selector: 'zeppelin-folder-rename',
  templateUrl: './folder-rename.component.html',
  styleUrls: ['./folder-rename.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FolderRenameComponent implements OnInit {
  @Input() newFolderPath: string;
  @Input() folderId: string;
  willMerged = false;

  checkMerged() {
    const newFolderPath = this.normalizeFolderId(this.newFolderPath);
    this.willMerged = this.folderId !== this.newFolderPath && !!this.noteListService.notes.flatFolderMap[newFolderPath];
    this.cdr.markForCheck();
  }

  rename() {
    this.messageService.folderRename(this.folderId, this.newFolderPath);
    this.nzModalRef.destroy();
  }

  normalizeFolderId(folderId) {
    let normalizeFolderId = folderId.trim();

    while (normalizeFolderId.indexOf('\\') > -1) {
      normalizeFolderId = normalizeFolderId.replace('\\', '/');
    }

    while (normalizeFolderId.indexOf('///') > -1) {
      normalizeFolderId = normalizeFolderId.replace('///', '/');
    }

    normalizeFolderId = normalizeFolderId.replace('//', '/');

    if (normalizeFolderId === '/') {
      return '/';
    }

    if (normalizeFolderId[0] === '/') {
      normalizeFolderId = normalizeFolderId.substring(1);
    }

    if (normalizeFolderId.slice(-1) === '/') {
      normalizeFolderId = normalizeFolderId.slice(0, -1);
    }

    return normalizeFolderId;
  }

  constructor(
    private noteListService: NoteListService,
    private cdr: ChangeDetectorRef,
    private messageService: MessageService,
    private nzModalRef: NzModalRef
  ) {}

  ngOnInit() {
    this.checkMerged();
  }
}
