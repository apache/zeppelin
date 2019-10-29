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
import {
  NzAlertModule,
  NzBadgeModule,
  NzButtonModule,
  NzCardModule,
  NzCheckboxModule,
  NzDividerModule,
  NzDropDownModule,
  NzFormModule,
  NzIconModule,
  NzInputModule,
  NzMessageModule,
  NzModalModule,
  NzRadioModule,
  NzSelectModule,
  NzSwitchModule,
  NzTableModule,
  NzTagModule,
  NzToolTipModule
} from 'ng-zorro-antd';

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
