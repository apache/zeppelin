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

import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';

import * as distanceInWordsStrict from 'date-fns/distance_in_words_strict';
import * as distanceInWordsToNow from 'date-fns/distance_in_words_to_now';
import * as format from 'date-fns/format';

@Component({
  selector: 'zeppelin-notebook-paragraph-footer',
  templateUrl: './footer.component.html',
  styleUrls: ['./footer.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotebookParagraphFooterComponent implements OnChanges {
  @Input() dateStarted: string;
  @Input() dateFinished: string;
  @Input() dateUpdated: string;
  @Input() showExecutionTime = false;
  @Input() showElapsedTime = false;
  @Input() user: string;
  executionTime: string;
  elapsedTime: string;

  isResultOutdated() {
    return this.dateUpdated !== undefined && Date.parse(this.dateUpdated) > Date.parse(this.dateStarted);
  }

  getExecutionTime() {
    const end = this.dateFinished;
    const start = this.dateStarted;
    const timeMs = Date.parse(end) - Date.parse(start);
    if (isNaN(timeMs) || timeMs < 0) {
      if (this.isResultOutdated()) {
        return 'outdated';
      }
      return '';
    }

    const durationFormat = distanceInWordsStrict(start, end);
    const endFormat = format(this.dateFinished, 'MMMM DD YYYY, h:mm:ss A');

    const user = this.user === undefined || this.user === null ? 'anonymous' : this.user;
    let desc = `Took ${durationFormat}. Last updated by ${user} at ${endFormat}.`;

    if (this.isResultOutdated()) {
      desc += ' (outdated)';
    }

    return desc;
  }

  getElapsedTime() {
    // TODO(hsuanxyz) dateStarted undefined after start
    return `Started ${distanceInWordsToNow(this.dateStarted || new Date())} ago.`;
  }

  constructor() {}

  ngOnChanges() {
    this.executionTime = this.getExecutionTime();
    this.elapsedTime = this.getElapsedTime();
  }
}
