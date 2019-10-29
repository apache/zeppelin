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

import { Injectable } from '@angular/core';

import { NzModalService } from 'ng-zorro-antd';

import { FolderRenameComponent } from '@zeppelin/share/folder-rename/folder-rename.component';
import { NoteCreateComponent } from '@zeppelin/share/note-create/note-create.component';
import { NoteImportComponent } from '@zeppelin/share/note-import/note-import.component';
import { NoteRenameComponent } from '@zeppelin/share/note-rename/note-rename.component';

@Injectable({
  providedIn: 'root'
})
export class NoteActionService {
  renameNote(id: string, path: string, name: string) {
    this.nzModalService.create({
      nzTitle: 'Rename note',
      nzContent: NoteRenameComponent,
      nzComponentParams: {
        id,
        newName: path || name
      },
      nzWidth: '800px',
      nzFooter: null
    });
  }

  renameFolder(path: string) {
    this.nzModalService.create({
      nzTitle: 'Rename folder',
      nzContent: FolderRenameComponent,
      nzComponentParams: {
        folderId: path,
        newFolderPath: path
      },
      nzWidth: '800px',
      nzFooter: null
    });
  }

  importNote() {
    this.nzModalService.create({
      nzTitle: 'Import New Note',
      nzContent: NoteImportComponent,
      nzWidth: '800px',
      nzFooter: null
    });
  }

  createNote(path?: string) {
    this.nzModalService.create({
      nzTitle: 'Create New Note',
      nzContent: NoteCreateComponent,
      nzComponentParams: { path },
      nzWidth: '800px',
      nzFooter: null
    });
  }

  constructor(private nzModalService: NzModalService) {}
}
