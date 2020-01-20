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
import { RouterModule } from '@angular/router';
import { IconDefinition } from '@ant-design/icons-angular';
import { ClockCircleOutline, FileOutline, FileUnknownOutline, SearchOutline } from '@ant-design/icons-angular/icons';
import { NzBadgeModule } from 'ng-zorro-antd/badge';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzHighlightModule } from 'ng-zorro-antd/core';
import { NzDividerModule } from 'ng-zorro-antd/divider';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzIconModule, NZ_ICONS } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzModalModule } from 'ng-zorro-antd/modal';
import { NzProgressModule } from 'ng-zorro-antd/progress';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzSkeletonModule } from 'ng-zorro-antd/skeleton';
import { NzToolTipModule } from 'ng-zorro-antd/tooltip';

import { ShareModule } from '@zeppelin/share';

import { JobManagerRoutingModule } from './job-manager-routing.module';
import { JobManagerComponent } from './job-manager.component';
import { JobManagerJobStatusComponent } from './job-status/job-status.component';
import { JobManagerJobComponent } from './job/job.component';

const icons: IconDefinition[] = [SearchOutline, FileOutline, FileUnknownOutline, ClockCircleOutline];

@NgModule({
  declarations: [JobManagerComponent, JobManagerJobComponent, JobManagerJobStatusComponent],
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    NzHighlightModule,
    ShareModule,
    NzIconModule,
    NzInputModule,
    NzBadgeModule,
    NzGridModule,
    NzModalModule,
    RouterModule,
    NzSelectModule,
    NzInputModule,
    NzFormModule,
    JobManagerRoutingModule,
    NzDividerModule,
    NzCardModule,
    NzToolTipModule,
    NzProgressModule,
    NzSkeletonModule,
    NzEmptyModule
  ],
  providers: [{ provide: NZ_ICONS, useValue: icons }]
})
export class JobManagerModule {}
