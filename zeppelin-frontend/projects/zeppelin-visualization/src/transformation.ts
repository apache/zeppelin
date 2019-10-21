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
