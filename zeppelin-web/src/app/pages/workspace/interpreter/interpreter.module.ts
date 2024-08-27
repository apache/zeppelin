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
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { NzAlertModule } from 'ng-zorro-antd/alert';
import { NzBadgeModule } from 'ng-zorro-antd/badge';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';
import { NzDividerModule } from 'ng-zorro-antd/divider';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMessageModule } from 'ng-zorro-antd/message';
import { NzModalModule } from 'ng-zorro-antd/modal';
import { NzRadioModule } from 'ng-zorro-antd/radio';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzSwitchModule } from 'ng-zorro-antd/switch';
import { NzTableModule } from 'ng-zorro-antd/table';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzToolTipModule } from 'ng-zorro-antd/tooltip';

import { ShareModule } from '@zeppelin/share';

import { InterpreterCreateRepositoryModalComponent } from './create-repository-modal/create-repository-modal.component';
import { InterpreterRoutingModule } from './interpreter-routing.module';
import { InterpreterComponent } from './interpreter.component';
import { InterpreterItemComponent } from './item/item.component';

@NgModule({
  declarations: [InterpreterComponent, InterpreterCreateRepositoryModalComponent, InterpreterItemComponent],
  entryComponents: [InterpreterCreateRepositoryModalComponent],
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    InterpreterRoutingModule,
    ShareModule,
    NzFormModule,
    NzSelectModule,
    NzSwitchModule,
    NzToolTipModule,
    NzCheckboxModule,
    NzRadioModule,
    NzBadgeModule,
    NzButtonModule,
    NzModalModule,
    NzInputModule,
    NzDividerModule,
    NzTagModule,
    NzCardModule,
    NzDropDownModule,
    NzIconModule,
    NzTableModule,
    NzMessageModule,
    NzAlertModule
  ]
})
export class InterpreterModule {}
