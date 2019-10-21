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
