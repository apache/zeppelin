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

import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Inject, OnInit, ViewChild } from '@angular/core';

import { filter, maxBy, minBy, orderBy, sumBy } from 'lodash';
import { NzTableComponent, NzTableSortOrder } from 'ng-zorro-antd/table';
import { utils, writeFile, WorkSheet } from 'xlsx';

import { TableData, Visualization, VISUALIZATION } from '@zeppelin/visualization';

type ColType = 'string' | 'date' | 'number';
type AggregationType = 'count' | 'sum' | 'min' | 'max' | 'avg';

class FilterOption {
  sort: 'desc' | 'asc' | '' = '';
  type: ColType = 'string';
  visible = true;
  pinned?: string;
  term = '';
  width: string | '*' = '*';
  aggregation: AggregationType | null = null;
  aggregationValue: number | null = null;
}

function typeCoercion(value: string, type: ColType): string | number | Date {
  switch (type) {
    case 'number':
      const num = Number.parseFloat(value);
      return Number.isNaN(num) ? value : num;
    case 'date':
      const date = new Date(value);
      return Number.isNaN(date.valueOf()) ? value : date;
    default:
      return value;
  }
}

@Component({
  selector: 'zeppelin-visualization-table-visualization',
  templateUrl: './table-visualization.component.html',
  styleUrls: ['./table-visualization.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TableVisualizationComponent implements OnInit {
  tableData?: TableData;
  // tslint:disable-next-line:no-any
  rows: any[] = [];
  columns: string[] = [];
  colOptions = new Map<string, FilterOption>();
  types: ColType[] = ['string', 'number', 'date'];
  aggregations: AggregationType[] = ['count', 'sum', 'min', 'max', 'avg'];
  @ViewChild(NzTableComponent, { static: false }) nzTable!: NzTableComponent;

  exportFile(type: 'csv' | 'xlsx', all = true) {
    const wb = utils.book_new();
    let ws: WorkSheet;
    if (all) {
      ws = utils.json_to_sheet(this.rows);
    } else {
      ws = utils.json_to_sheet(this.nzTable.data);
    }
    utils.book_append_sheet(wb, ws, 'Sheet1');
    writeFile(wb, `export.${type}`);
  }

  onChangeType(type: ColType, col: string) {
    this.getColOptionOrThrow(col).type = type;
    this.filterRows();
    this.aggregate();
  }

  onChangeAggregation(aggregation: AggregationType, col: string) {
    const opt = this.getColOptionOrThrow(col);
    opt.aggregation = opt.aggregation === aggregation ? null : aggregation;
    this.aggregate();
  }

  onSearch(): void {
    this.filterRows();
  }

  onSortChange(type: NzTableSortOrder, key: string): void {
    const opt = this.getColOptionOrThrow(key);
    this.colOptions.delete(key);
    if (type) {
      opt.sort = type === 'descend' ? 'desc' : 'asc';
    } else {
      opt.sort = '';
    }
    this.colOptions.set(key, opt);
    this.filterRows();
  }

  onTermChange(col: string, term: string) {
    this.getColOptionOrThrow(col).term = term;
  }

  getColOptionOrThrow(col: string): FilterOption {
    const opt = this.colOptions.get(col);
    if (!opt) {
      throw new Error('Column option should have been initialized');
    }
    return opt;
  }

  aggregate() {
    if (!this.tableData) {
      throw new Error('tableData is not defined');
    }
    const tableData = this.tableData;
    this.colOptions.forEach((opt, key) => {
      // tslint:disable-next-line:no-any
      const numValue = (row: any) => {
        const value = typeCoercion(row[key], opt.type);
        if (typeof value === 'number') {
          return value;
        }
        if (value instanceof Date) {
          return value.valueOf();
        }
        return value;
      };
      const getSum = () =>
        sumBy(tableData.rows, row => {
          const value = typeCoercion(row[key], 'number');
          return typeof value === 'number' ? value : 0;
        });

      switch (opt.aggregation) {
        case 'sum':
          opt.aggregationValue = getSum();
          break;
        case 'avg':
          opt.aggregationValue = getSum() / tableData.rows.length;
          break;
        case 'count':
          opt.aggregationValue = tableData.rows.length;
          break;
        case 'max':
          opt.aggregationValue = maxBy(tableData.rows, numValue)[key];
          break;
        case 'min':
          opt.aggregationValue = minBy(tableData.rows, numValue)[key];
          break;
        default:
          opt.aggregationValue = null;
      }
    });
  }

  filterRows() {
    if (!this.tableData) {
      throw new Error('tableData is not defined');
    }
    // tslint:disable-next-line:no-any
    const sortKeys: any[] = [];
    // tslint:disable-next-line:no-any
    const sortTypes: any[] = [];
    // tslint:disable-next-line:no-any
    const terms: any[] = [];
    this.colOptions.forEach((value, key) => {
      if (value.sort) {
        // tslint:disable-next-line:no-any
        sortKeys.push((row: any) => typeCoercion(row[key], value.type));
        sortTypes.push(value.sort);
      }
      // tslint:disable-next-line:no-any
      terms.push((row: any) => String(row[key]).search(value.term) !== -1);
    });
    this.rows = filter(this.tableData.rows, row => terms.every(term => term(row)));
    this.rows = orderBy(this.rows, sortKeys, sortTypes);
    this.cdr.markForCheck();
  }

  constructor(@Inject(VISUALIZATION) public visualization: Visualization, private cdr: ChangeDetectorRef) {}

  ngOnInit() {}

  render() {
    this.tableData = this.visualization.transformed;
    this.columns = this.tableData!.columns;
    this.rows = [...this.tableData!.rows];
    this.columns.forEach(col => {
      this.colOptions.set(col, new FilterOption());
    });
    this.filterRows();
  }
}
