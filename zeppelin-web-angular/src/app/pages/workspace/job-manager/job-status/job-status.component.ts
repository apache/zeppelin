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

import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

import { JobStatus } from '@zeppelin/sdk';

@Component({
  selector: 'zeppelin-job-manager-job-status',
  templateUrl: './job-status.component.html',
  styleUrls: ['./job-status.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class JobManagerJobStatusComponent {
  @Input() status: JobStatus;
  @Input() showText = false;
  jobStatus = JobStatus;
  statusMap = {
    [JobStatus.READY]: 'success',
    [JobStatus.FINISHED]: 'success',
    [JobStatus.ABORT]: 'warning',
    [JobStatus.ERROR]: 'error',
    [JobStatus.PENDING]: 'default',
    [JobStatus.RUNNING]: 'processing'
  };

  constructor() {}
}
