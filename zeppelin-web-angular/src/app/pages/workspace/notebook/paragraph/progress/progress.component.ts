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

@Component({
  selector: 'zeppelin-notebook-paragraph-progress',
  templateUrl: './progress.component.html',
  styleUrls: ['./progress.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotebookParagraphProgressComponent implements OnChanges {
  @Input() progress = 0;
  displayProgress = 0;

  ngOnChanges(): void {
    if (this.progress > 0 && this.progress < 100) {
      this.displayProgress = this.progress;
    } else {
      this.displayProgress = 100;
    }
  }
}
