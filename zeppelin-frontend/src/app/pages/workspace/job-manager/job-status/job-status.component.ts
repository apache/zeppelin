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
