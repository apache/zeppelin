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

import { PortalModule } from '@angular/cdk/portal';
import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzRadioModule } from 'ng-zorro-antd/radio';
import { NzResizableModule } from 'ng-zorro-antd/resizable';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzSwitchModule } from 'ng-zorro-antd/switch';
import { NzToolTipModule } from 'ng-zorro-antd/tooltip';

import { ShareModule } from '@zeppelin/share';
import { VisualizationModule } from '@zeppelin/visualizations/visualization.module';

import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NotebookParagraphDynamicFormsComponent } from './dynamic-forms/dynamic-forms.component';
import { NotebookParagraphResultComponent } from './result/result.component';

@NgModule({
  exports: [NotebookParagraphResultComponent, NotebookParagraphDynamicFormsComponent],
  declarations: [NotebookParagraphResultComponent, NotebookParagraphDynamicFormsComponent],
  imports: [
    CommonModule,
    ShareModule,
    PortalModule,
    VisualizationModule,
    FormsModule,
    NzButtonModule,
    NzDropDownModule,
    NzRadioModule,
    NzResizableModule,
    NzToolTipModule,
    NzIconModule,
    NzCheckboxModule,
    NzSelectModule,
    NzSwitchModule,
    NzInputModule,
    NzGridModule
  ]
})
export class WorkspaceShareModule {}
