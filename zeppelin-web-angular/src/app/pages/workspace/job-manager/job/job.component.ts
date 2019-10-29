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

import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnInit,
  Output,
  SimpleChanges
} from '@angular/core';

import * as distanceInWords from 'date-fns/distance_in_words';

import { JobsItem, JobStatus } from '@zeppelin/sdk';

@Component({
  selector: 'zeppelin-job-manager-job',
  templateUrl: './job.component.html',
  styleUrls: ['./job.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class JobManagerJobComponent implements OnInit, OnChanges {
  @Input() note: JobsItem;
  @Input() highlight: string | null = null;
  @Output() readonly start = new EventEmitter<string>();
  @Output() readonly stop = new EventEmitter<string>();

  icon = 'file';
  relativeTime = '';
  progress = 0;

  setIcon(): void {
    const noteType = this.note.noteType;
    if (noteType === 'normal') {
      this.icon = 'file';
    } else if (noteType === 'cron') {
      this.icon = 'close-circle';
    } else {
      this.icon = 'file-unknown';
    }
  }

  setRelativeTime(): void {
    this.relativeTime = distanceInWords(new Date(), new Date(this.note.unixTimeLastRun));
  }

  setProgress(): void {
    const runningCount = this.note.paragraphs.filter(
      paragraph => [JobStatus.FINISHED, JobStatus.RUNNING].indexOf(paragraph.status) !== -1
    ).length;
    this.progress = runningCount / this.note.paragraphs.length;
  }

  onStartClick(): void {
    this.start.emit(this.note.noteId);
  }

  onStopClick(): void {
    this.stop.emit(this.note.noteId);
  }

  constructor() {}

  ngOnInit() {}

  ngOnChanges(changes: SimpleChanges): void {
    this.setIcon();
    this.setRelativeTime();
    this.setProgress();
  }
}
