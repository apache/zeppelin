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

import { DataSet as AntvDataSet } from '@antv/data-set';

import { DatasetType, ParagraphIResultsMsgItem } from '@zeppelin/sdk';
import { DataSet } from './data-set';

export class TableData extends DataSet {
  columns: string[] = [];
  // tslint:disable-next-line
  rows: any[] = [];

  loadParagraphResult({ data, type }: ParagraphIResultsMsgItem): void {
    if (type !== DatasetType.TABLE) {
      console.error('Can not load paragraph result');
      return;
    }
    const ds = new AntvDataSet();
    const dv = ds.createView().source(data, {
      type: 'tsv'
    });
    this.columns = dv.origin && dv.origin.columns ? dv.origin.columns : [];
    this.rows = dv.rows || [];
  }
}
