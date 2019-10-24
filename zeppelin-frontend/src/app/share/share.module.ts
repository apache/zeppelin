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

import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';

import {
  NzAlertModule,
  NzBadgeModule,
  NzButtonModule,
  NzCardModule,
  NzDividerModule,
  NzDropDownModule,
  NzFormModule,
  NzGridModule,
  NzIconModule,
  NzInputModule,
  NzMenuModule,
  NzMessageModule,
  NzModalModule,
  NzNotificationModule,
  NzPopconfirmModule,
  NzProgressModule,
  NzSelectModule,
  NzTabsModule,
  NzToolTipModule,
  NzTreeModule,
  NzUploadModule
} from 'ng-zorro-antd';

import { AboutZeppelinComponent } from '@zeppelin/share/about-zeppelin/about-zeppelin.component';
import { CodeEditorModule } from '@zeppelin/share/code-editor';
import { FolderRenameComponent } from '@zeppelin/share/folder-rename/folder-rename.component';
import { HeaderComponent } from '@zeppelin/share/header/header.component';
import { MathJaxDirective } from '@zeppelin/share/math-jax/math-jax.directive';
import { NodeListComponent } from '@zeppelin/share/node-list/node-list.component';
import { NoteCreateComponent } from '@zeppelin/share/note-create/note-create.component';
import { NoteImportComponent } from '@zeppelin/share/note-import/note-import.component';
import { NoteRenameComponent } from '@zeppelin/share/note-rename/note-rename.component';
import { PageHeaderComponent } from '@zeppelin/share/page-header/page-header.component';
import { HumanizeBytesPipe } from '@zeppelin/share/pipes';
import { RunScriptsDirective } from '@zeppelin/share/run-scripts/run-scripts.directive';
import { SpinComponent } from '@zeppelin/share/spin/spin.component';
import { ResizeHandleComponent } from './resize-handle';

const MODAL_LIST = [
  AboutZeppelinComponent,
  NoteImportComponent,
  NoteCreateComponent,
  NoteRenameComponent,
  FolderRenameComponent
];
const EXPORT_LIST = [HeaderComponent, NodeListComponent, PageHeaderComponent, SpinComponent, ResizeHandleComponent];
const PIPES = [HumanizeBytesPipe];

@NgModule({
  declarations: [MODAL_LIST, EXPORT_LIST, PIPES, MathJaxDirective, RunScriptsDirective],
  entryComponents: [MODAL_LIST],
  exports: [EXPORT_LIST, PIPES, MathJaxDirective, RunScriptsDirective, CodeEditorModule],
  imports: [
    FormsModule,
    CommonModule,
    NzMenuModule,
    NzIconModule,
    NzInputModule,
    NzDropDownModule,
    NzBadgeModule,
    NzGridModule,
    NzModalModule,
    NzTreeModule,
    RouterModule,
    NzButtonModule,
    NzNotificationModule,
    NzToolTipModule,
    NzDividerModule,
    NzMessageModule,
    NzCardModule,
    NzPopconfirmModule,
    NzPopconfirmModule,
    NzFormModule,
    NzTabsModule,
    NzUploadModule,
    NzSelectModule,
    NzAlertModule,
    NzProgressModule,
    CodeEditorModule
  ]
})
export class ShareModule {}
