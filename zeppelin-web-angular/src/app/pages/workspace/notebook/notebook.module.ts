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

import { DragDropModule } from '@angular/cdk/drag-drop';
import { PortalModule } from '@angular/cdk/portal';
import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCodeEditorModule } from 'ng-zorro-antd/code-editor';
import { NzNoAnimationModule } from 'ng-zorro-antd/core';
import { NzDividerModule } from 'ng-zorro-antd/divider';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzPopconfirmModule } from 'ng-zorro-antd/popconfirm';
import { NzPopoverModule } from 'ng-zorro-antd/popover';
import { NzProgressModule } from 'ng-zorro-antd/progress';
import { NzRadioModule } from 'ng-zorro-antd/radio';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzSwitchModule } from 'ng-zorro-antd/switch';
import { NzToolTipModule } from 'ng-zorro-antd/tooltip';

import { ShareModule } from '@zeppelin/share';

import { NotebookAddParagraphComponent } from './add-paragraph/add-paragraph.component';
import { NotebookInterpreterBindingComponent } from './interpreter-binding/interpreter-binding.component';
import { NotebookParagraphCodeEditorComponent } from './paragraph/code-editor/code-editor.component';
import { NotebookParagraphControlComponent } from './paragraph/control/control.component';
import { NotebookParagraphFooterComponent } from './paragraph/footer/footer.component';
import { NotebookParagraphComponent } from './paragraph/paragraph.component';
import { NotebookParagraphProgressComponent } from './paragraph/progress/progress.component';
import { NotebookPermissionsComponent } from './permissions/permissions.component';
import { NotebookRevisionsComparatorComponent } from './revisions-comparator/revisions-comparator.component';

import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';
import { WorkspaceShareModule } from '../../workspace/share/share.module';
import { NotebookActionBarComponent } from './action-bar/action-bar.component';
import { NotebookRoutingModule } from './notebook-routing.module';
import { NotebookComponent } from './notebook.component';
import { NotebookShareModule } from './share/share.module';

@NgModule({
  declarations: [
    NotebookComponent,
    NotebookActionBarComponent,
    NotebookInterpreterBindingComponent,
    NotebookPermissionsComponent,
    NotebookRevisionsComparatorComponent,
    NotebookParagraphComponent,
    NotebookAddParagraphComponent,
    NotebookParagraphCodeEditorComponent,
    NotebookParagraphProgressComponent,
    NotebookParagraphFooterComponent,
    NotebookParagraphControlComponent
  ],
  imports: [
    CommonModule,
    PortalModule,
    WorkspaceShareModule,
    NotebookRoutingModule,
    ShareModule,
    NotebookShareModule,
    NzButtonModule,
    NzIconModule,
    NzDropDownModule,
    NzNoAnimationModule,
    NzToolTipModule,
    NzPopconfirmModule,
    NzFormModule,
    NzPopoverModule,
    NzInputModule,
    FormsModule,
    ReactiveFormsModule,
    NzDividerModule,
    NzProgressModule,
    NzSwitchModule,
    NzSelectModule,
    NzGridModule,
    NzRadioModule,
    DragDropModule,
    NzCodeEditorModule,
    NzCheckboxModule
  ]
})
export class NotebookModule {}
