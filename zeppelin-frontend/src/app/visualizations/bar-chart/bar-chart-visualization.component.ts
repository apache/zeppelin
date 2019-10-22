import {
  AfterViewInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  Inject,
  OnInit,
  ViewChild
} from '@angular/core';

import { get } from 'lodash';

import { VisualizationMultiBarChart } from '@zeppelin/sdk';
import { G2VisualizationComponentBase, Visualization, VISUALIZATION } from '@zeppelin/visualization';

import { VisualizationPivotSettingComponent } from '../common/pivot-setting/pivot-setting.component';
import { calcTickCount } from '../common/util/calc-tick-count';
import { setChartXAxis } from '../common/util/set-x-axis';
import { VisualizationXAxisSettingComponent } from '../common/x-axis-setting/x-axis-setting.component';

@Component({
  selector: 'zeppelin-bar-chart-visualization',
  templateUrl: './bar-chart-visualization.component.html',
  styleUrls: ['./bar-chart-visualization.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BarChartVisualizationComponent extends G2VisualizationComponentBase implements OnInit, AfterViewInit {
  @ViewChild('container', { static: false }) container: ElementRef<HTMLDivElement>;
  @ViewChild(VisualizationXAxisSettingComponent, { static: false })
  xAxisSettingComponent: VisualizationXAxisSettingComponent;
  @ViewChild(VisualizationPivotSettingComponent, { static: false })
  pivotSettingComponent: VisualizationPivotSettingComponent;
  stacked = false;

  viewChange() {
    if (!this.config.setting.multiBarChart) {
      this.config.setting.multiBarChart = new VisualizationMultiBarChart();
    }
    this.config.setting.multiBarChart.stacked = this.stacked;
    this.visualization.configChange$.next(this.config);
  }

  constructor(@Inject(VISUALIZATION) public visualization: Visualization, private cdr: ChangeDetectorRef) {
    super(visualization);
  }

  ngOnInit() {}

  ngAfterViewInit() {
    this.render();
  }

  refreshSetting() {
    this.stacked = get(this.config.setting, 'multiBarChart.stacked', false);
    this.pivotSettingComponent.init();
    this.xAxisSettingComponent.init();
    this.cdr.markForCheck();
  }

  setScale() {
    const key = this.getKey();
    const tickCount = calcTickCount(this.container.nativeElement);
    this.chart.scale(key, {
      tickCount,
      type: 'cat'
    });
  }

  renderBefore(chart) {
    const key = this.getKey();
    this.setScale();

    if (get(this.config.setting, 'multiBarChart.stacked', false)) {
      this.chart
        .intervalStack()
        .position(`${key}*__value__`)
        .color('__key__')
        .opacity(1);
    } else {
      this.chart
        .interval()
        .position(`${key}*__value__`)
        .color('__key__')
        .opacity(1)
        .adjust([
          {
            type: 'dodge',
            marginRatio: 0
          }
        ]);
    }
    setChartXAxis(this.visualization, 'multiBarChart', this.chart, key);
  }
}
