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
import { ShareModule } from '@zeppelin/share';
import { NzAutocompleteModule } from 'ng-zorro-antd/auto-complete';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzDividerModule } from 'ng-zorro-antd/divider';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMessageModule } from 'ng-zorro-antd/message';
import { NzPopconfirmModule } from 'ng-zorro-antd/popconfirm';
import { NzTableModule } from 'ng-zorro-antd/table';
import { NzToolTipModule } from 'ng-zorro-antd/tooltip';
import { CredentialRoutingModule } from './credential-routing.module';
import { CredentialComponent } from './credential.component';

@NgModule({
  declarations: [CredentialComponent],
  imports: [
    CommonModule,
    CredentialRoutingModule,
    FormsModule,
    ShareModule,
    ReactiveFormsModule,
    NzFormModule,
    NzAutocompleteModule,
    NzButtonModule,
    NzCardModule,
    NzIconModule,
    NzDividerModule,
    NzInputModule,
    NzMessageModule,
    NzTableModule,
    NzPopconfirmModule,
    NzGridModule,
    NzToolTipModule
  ]
})
export class CredentialModule {}
