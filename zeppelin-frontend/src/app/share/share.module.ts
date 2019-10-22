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
  exports: [EXPORT_LIST, PIPES, MathJaxDirective, RunScriptsDirective],
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
    NzProgressModule
  ]
})
export class ShareModule {}
