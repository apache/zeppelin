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

import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';

import { NzModalService } from 'ng-zorro-antd/modal';

import { MessageListener, MessageListenersManager } from '@zeppelin/core';
import { JobsItem, JobStatus, ListNoteJobs, ListUpdateNoteJobs, OP } from '@zeppelin/sdk';
import { JobManagerService, MessageService } from '@zeppelin/services';

enum JobDateSortKeys {
  RECENTLY_UPDATED = 'Recently Update',
  OLDEST_UPDATED = 'Oldest Updated'
}

interface FilterForm {
  noteName: string;
  interpreter: string;
  sortBy: string;
}

@Component({
  selector: 'zeppelin-job-manager',
  templateUrl: './job-manager.component.html',
  styleUrls: ['./job-manager.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class JobManagerComponent extends MessageListenersManager implements OnInit, OnDestroy {
  form: FormGroup;
  jobStatusKeys = Object.keys(JobStatus).map(k => JobStatus[k]);
  sortKeys = Object.keys(JobDateSortKeys).map(k => JobDateSortKeys[k]);
  interpreters: string[] = [];
  filteredJobs: JobsItem[] = [];
  filterString: string = '';
  jobs: JobsItem[] = [];
  loading = true;

  @MessageListener(OP.LIST_NOTE_JOBS)
  setJobs(data: ListNoteJobs) {
    this.jobs = data.noteJobs.jobs.filter(j => typeof j.interpreter !== 'undefined');
    const interpreters = this.jobs.map(job => job.interpreter);
    this.interpreters = Array.from(new Set(interpreters));
    this.loading = false;
    this.filterJobs();
  }

  @MessageListener(OP.LIST_UPDATE_NOTE_JOBS)
  updateJobs(data: ListUpdateNoteJobs) {
    data.noteRunningJobs.jobs.forEach(updateJob => {
      const currentJobIndex = this.jobs.findIndex(job => job.noteId === updateJob.noteId);
      if (currentJobIndex === -1) {
        this.jobs.push(updateJob);
      } else {
        if (updateJob.isRemoved) {
          this.jobs.splice(currentJobIndex, 1);
        } else {
          this.jobs[currentJobIndex] = updateJob;
        }
      }
    });
    this.filterJobs();
  }

  filterJobs() {
    const filterData = this.form.getRawValue() as FilterForm;
    this.filterString = filterData.noteName;
    const isSortByAsc = filterData.sortBy === JobDateSortKeys.OLDEST_UPDATED;
    this.filteredJobs = this.jobs
      .filter(job => {
        const escapedString = filterData.noteName.replace(/([.*+?^=!:${}()|[\]\/\\])/g, '\\$&');
        const noteNameReg = new RegExp(escapedString, 'gi');
        return (
          (filterData.interpreter === '*' || job.interpreter === filterData.interpreter) &&
          job.noteName.match(noteNameReg)
        );
      })
      .sort((x, y) => (isSortByAsc ? x.unixTimeLastRun - y.unixTimeLastRun : y.unixTimeLastRun - x.unixTimeLastRun));
    this.cdr.markForCheck();
  }

  onStart(noteId: string): void {
    this.nzModalService.confirm({
      nzTitle: 'Job Dialog',
      nzContent: 'Run all paragraphs?',
      nzOnOk: () => {
        this.jobManagerService.startJob(noteId).subscribe();
      }
    });
  }

  onStop(noteId: string): void {
    this.nzModalService.confirm({
      nzTitle: 'Job Dialog',
      nzContent: 'Stop all paragraphs?',
      nzOnOk: () => {
        this.jobManagerService.stopJob(noteId).subscribe();
      }
    });
  }

  constructor(
    public messageService: MessageService,
    private jobManagerService: JobManagerService,
    private fb: FormBuilder,
    private cdr: ChangeDetectorRef,
    private nzModalService: NzModalService
  ) {
    super(messageService);
  }

  ngOnInit() {
    this.form = this.fb.group({
      noteName: [''],
      interpreter: ['*'],
      sortBy: [JobDateSortKeys.RECENTLY_UPDATED]
    });

    this.form.valueChanges.subscribe(() => this.filterJobs());

    this.messageService.listNoteJobs();
  }

  ngOnDestroy(): void {
    this.messageService.unsubscribeUpdateNoteJobs();
    super.ngOnDestroy();
  }
}
