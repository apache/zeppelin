import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnInit } from '@angular/core';

import { get } from 'lodash';

import { GraphConfig } from '@zeppelin/sdk';
import { TableData, Visualization } from '@zeppelin/visualization';

@Component({
  selector: 'zeppelin-visualization-scatter-setting',
  templateUrl: './scatter-setting.component.html',
  styleUrls: ['./scatter-setting.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class VisualizationScatterSettingComponent implements OnInit {
  @Input() visualization: Visualization;

  tableData: TableData;
  config: GraphConfig;
  columns = [];

  field = {
    xAxis: [],
    yAxis: [],
    group: [],
    size: []
  };

  // tslint:disable-next-line
  drop(event: CdkDragDrop<any[]>) {
    this.clean(event.container.data, false);
    event.container.data.push(event.previousContainer.data[event.previousIndex]);
    this.cdr.markForCheck();
    this.updateConfig();
  }

  // tslint:disable-next-line
  clean(data: any[], update = true): void {
    while (data.length > 0) {
      data.splice(0, 1);
    }
    if (update) {
      this.updateConfig();
    }
    this.cdr.markForCheck();
  }

  noReturnPredicate() {
    return false;
  }

  updateConfig() {
    if (!this.config.setting.scatterChart) {
      this.config.setting.scatterChart = {};
    }
    const scatterSetting = this.config.setting.scatterChart;
    scatterSetting.xAxis = this.field.xAxis[0];
    scatterSetting.yAxis = this.field.yAxis[0];
    scatterSetting.size = this.field.size[0];
    scatterSetting.group = this.field.group[0];
    this.visualization.configChange$.next(this.config);
  }

  constructor(private cdr: ChangeDetectorRef) {}

  init() {
    this.tableData = this.visualization.getTransformation().getTableData() as TableData;
    this.config = this.visualization.getConfig();
    this.columns = this.tableData.columns.map((name, index) => ({
      name,
      index,
      aggr: 'sum'
    }));

    const xAxis = get(this.config.setting, 'scatterChart.xAxis', this.columns[0]);
    const yAxis = get(this.config.setting, 'scatterChart.yAxis', this.columns[1]);
    const group = get(this.config.setting, 'scatterChart.group');
    const size = get(this.config.setting, 'scatterChart.size');
    const arrayWrapper = value => (value ? [value] : []);
    this.field.xAxis = arrayWrapper(xAxis);
    this.field.yAxis = arrayWrapper(yAxis);
    this.field.group = arrayWrapper(group);
    this.field.size = arrayWrapper(size);
    this.cdr.markForCheck();
  }

  ngOnInit() {
    this.init();
  }
}
