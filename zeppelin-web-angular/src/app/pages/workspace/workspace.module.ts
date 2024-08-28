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
import { HttpClientModule } from '@angular/common/http';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';

import { HeliumManagerModule } from '@zeppelin/helium-manager';
import { ShareModule } from '@zeppelin/share';

import { NzMessageModule } from 'ng-zorro-antd/message';
import { WorkspaceComponent } from './workspace.component';

import { WorkspaceRoutingModule } from './workspace-routing.module';

@NgModule({
  declarations: [WorkspaceComponent],
  imports: [
    CommonModule,
    WorkspaceRoutingModule,
    FormsModule,
    HttpClientModule,
    ShareModule,
    RouterModule,
    HeliumManagerModule,
    NzMessageModule
  ]
})
export class WorkspaceModule {}
