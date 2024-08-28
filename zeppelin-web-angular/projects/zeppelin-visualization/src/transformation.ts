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

import { GraphConfig } from '@zeppelin/sdk';

import { DataSet } from './data-set';

export interface Setting {
  // tslint:disable-next-line:no-any
  template: any;
  // tslint:disable-next-line:no-any
  scope: any;
}

export abstract class Transformation {
  dataset: DataSet;
  constructor(private config: GraphConfig) {}

  // tslint:disable-next-line:no-any
  abstract transform(tableData): any;

  setConfig(config) {
    this.config = config;
  }

  setTableData(dataset: DataSet) {
    this.dataset = dataset;
  }

  getTableData(): DataSet {
    return this.dataset;
  }

  getConfig() {
    return this.config;
  }
}
