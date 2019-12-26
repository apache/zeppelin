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

import { copyArrayItem, moveItemInArray, transferArrayItem, CdkDragDrop } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnInit } from '@angular/core';

import { GraphConfig } from '@zeppelin/sdk';
import { TableData, Visualization } from '@zeppelin/visualization';

@Component({
  selector: 'zeppelin-visualization-pivot-setting',
  templateUrl: './pivot-setting.component.html',
  styleUrls: ['./pivot-setting.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class VisualizationPivotSettingComponent implements OnInit {
  @Input() visualization: Visualization;

  tableData: TableData;
  config: GraphConfig;
  columns = [];
  aggregates = ['sum', 'count', 'avg', 'min', 'max'];

  // tslint:disable-next-line
  drop(event: CdkDragDrop<any[]>) {
    if (event.container.id === 'columns-list') {
      return;
    }
    if (event.previousContainer === event.container) {
      moveItemInArray(event.container.data, event.previousIndex, event.currentIndex);
    } else {
      if (
        event.container.id !== 'value-list' &&
        event.container.data.findIndex(e => e.name === event.previousContainer.data[event.previousIndex].name) !== -1
      ) {
        return;
      }
      if (event.previousContainer.id === 'columns-list') {
        copyArrayItem(event.previousContainer.data, event.container.data, event.previousIndex, event.currentIndex);
      } else {
        transferArrayItem(event.previousContainer.data, event.container.data, event.previousIndex, event.currentIndex);
      }
    }
    this.visualization.configChange$.next(this.config);
  }

  // tslint:disable-next-line
  removeFieldAt(data: any[], index: number): void {
    data.splice(index, 1);
    this.visualization.configChange$.next(this.config);
    this.cdr.markForCheck();
  }

  changeAggregate(aggregates: string, index: number): void {
    this.config.values[index].aggr = aggregates;
    this.visualization.configChange$.next(this.config);
    this.cdr.markForCheck();
  }

  noReturnPredicate() {
    return false;
  }

  init() {
    this.tableData = this.visualization.getTransformation().getTableData() as TableData;
    this.config = this.visualization.getConfig();
    this.columns = this.tableData.columns.map((name, index) => ({
      name,
      index,
      aggr: 'sum'
    }));
    this.cdr.markForCheck();
  }

  constructor(private cdr: ChangeDetectorRef) {}

  ngOnInit() {
    this.init();
  }
}
