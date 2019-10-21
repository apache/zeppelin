import { TableData } from './table-data';
import { Transformation } from './transformation';

// tslint:disable-next-line:no-any
export class TableTransformation extends Transformation {
  constructor(config) {
    super(config);
  }

  // tslint:disable-next-line:no-any
  transform(tableData: TableData): any {
    return tableData;
  }
}
