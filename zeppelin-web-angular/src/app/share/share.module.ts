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

import { NzAlertModule } from 'ng-zorro-antd/alert';
import { NzBadgeModule } from 'ng-zorro-antd/badge';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzAddOnModule } from 'ng-zorro-antd/core';
import { NzDividerModule } from 'ng-zorro-antd/divider';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMenuModule } from 'ng-zorro-antd/menu';
import { NzMessageModule } from 'ng-zorro-antd/message';
import { NzModalModule } from 'ng-zorro-antd/modal';
import { NzNotificationModule } from 'ng-zorro-antd/notification';
import { NzPopconfirmModule } from 'ng-zorro-antd/popconfirm';
import { NzProgressModule } from 'ng-zorro-antd/progress';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzTabsModule } from 'ng-zorro-antd/tabs';
import { NzToolTipModule } from 'ng-zorro-antd/tooltip';
import { NzTreeModule } from 'ng-zorro-antd/tree';
import { NzUploadModule } from 'ng-zorro-antd/upload';

import { AboutZeppelinComponent } from '@zeppelin/share/about-zeppelin/about-zeppelin.component';
import { CodeEditorModule } from '@zeppelin/share/code-editor';
import { FolderRenameComponent } from '@zeppelin/share/folder-rename/folder-rename.component';
import { HeaderComponent } from '@zeppelin/share/header/header.component';
import { MathJaxDirective } from '@zeppelin/share/math-jax/math-jax.directive';
import { NodeListComponent } from '@zeppelin/share/node-list/node-list.component';
import { NoteCreateComponent } from '@zeppelin/share/note-create/note-create.component';
import { NoteImportComponent } from '@zeppelin/share/note-import/note-import.component';
import { NoteRenameComponent } from '@zeppelin/share/note-rename/note-rename.component';
import { NoteTocComponent } from '@zeppelin/share/note-toc/note-toc.component';
import { PageHeaderComponent } from '@zeppelin/share/page-header/page-header.component';
import { HumanizeBytesPipe } from '@zeppelin/share/pipes';
import { RunScriptsDirective } from '@zeppelin/share/run-scripts/run-scripts.directive';
import { ExternalLinkDirective } from "@zeppelin/share/external-links/external-link.directive";
import { SpinComponent } from '@zeppelin/share/spin/spin.component';
import { Ng1MigrationComponent } from './ng1-migration/ng1-migration.component';
import { ResizeHandleComponent } from './resize-handle';

const MODAL_LIST = [
  AboutZeppelinComponent,
  NoteImportComponent,
  NoteCreateComponent,
  NoteRenameComponent,
  FolderRenameComponent,
  Ng1MigrationComponent
];
const EXPORT_LIST = [
  HeaderComponent,
  NodeListComponent,
  NoteTocComponent,
  PageHeaderComponent,
  SpinComponent,
  ResizeHandleComponent
];
const PIPES = [HumanizeBytesPipe];

@NgModule({
  declarations: [MODAL_LIST, EXPORT_LIST, PIPES, MathJaxDirective, RunScriptsDirective, ExternalLinkDirective],
  entryComponents: [MODAL_LIST],
  exports: [EXPORT_LIST, PIPES, MathJaxDirective, RunScriptsDirective, ExternalLinkDirective, CodeEditorModule],
  imports: [
    FormsModule,
    CommonModule,
    NzMenuModule,
    NzAddOnModule,
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
