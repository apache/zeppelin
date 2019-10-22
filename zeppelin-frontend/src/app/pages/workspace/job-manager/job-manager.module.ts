import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { IconDefinition } from '@ant-design/icons-angular';
import { ClockCircleOutline, FileOutline, FileUnknownOutline, SearchOutline } from '@ant-design/icons-angular/icons';
import {
  NzBadgeModule,
  NzCardModule,
  NzDividerModule,
  NzEmptyModule,
  NzFormModule,
  NzGridModule,
  NzIconModule,
  NzInputModule,
  NzModalModule,
  NzProgressModule,
  NzSelectModule,
  NzSkeletonModule,
  NzToolTipModule,
  NZ_ICONS
} from 'ng-zorro-antd';

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
