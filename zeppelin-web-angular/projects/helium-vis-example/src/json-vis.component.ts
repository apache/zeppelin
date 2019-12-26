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

import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Inject, OnInit } from '@angular/core';
import { TableData, Visualization, VISUALIZATION } from '@zeppelin/visualization';

@Component({
  selector: 'lib-helium-vis-example',
  template: `
    <pre><code>{{tableData | json}}</code></pre>
  `,
  styles: [`
    pre {
      background: #fff7e7;
      padding: 10px;
      border: 1px solid #ffd278;
      color: #fa7e14;
      border-radius: 3px;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class JsonVisComponent implements OnInit {
  tableData: TableData;
  constructor(@Inject(VISUALIZATION) public visualization: Visualization, private cdr: ChangeDetectorRef) {}

  ngOnInit() {
  }

  render(): void {
    this.tableData = this.visualization.transformed;
  }

}
