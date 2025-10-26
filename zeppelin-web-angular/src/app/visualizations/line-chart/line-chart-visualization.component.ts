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
import * as G2 from '@antv/g2';
import { GraphConfig, VisualizationLineChart } from '@zeppelin/sdk';

import { G2VisualizationComponentBase, Visualization, VISUALIZATION } from '@zeppelin/visualization';

import { VisualizationPivotSettingComponent } from '../common/pivot-setting/pivot-setting.component';
import { calcTickCount } from '../common/util/calc-tick-count';
import { setChartXAxis } from '../common/util/set-x-axis';
import { VisualizationXAxisSettingComponent } from '../common/x-axis-setting/x-axis-setting.component';

@Component({
  selector: 'zeppelin-line-chart-visualization',
  templateUrl: './line-chart-visualization.component.html',
  styleUrls: ['./line-chart-visualization.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LineChartVisualizationComponent extends G2VisualizationComponentBase implements OnInit, AfterViewInit {
  @ViewChild('container', { static: false }) container!: ElementRef<HTMLDivElement>;
  @ViewChild(VisualizationXAxisSettingComponent, { static: false })
  xAxisSettingComponent!: VisualizationXAxisSettingComponent;
  @ViewChild(VisualizationPivotSettingComponent, { static: false })
  pivotSettingComponent!: VisualizationPivotSettingComponent;
  forceY = false;
  lineWithFocus = false;
  isDateFormat = false;
  dateFormat = '';

  settingChange(): void {
    if (!this.config || !this.visualization.configChange$) {
      throw new Error('config or visualization.configChange$ is not defined');
    }
    if (!this.config.setting.lineChart) {
      this.config.setting.lineChart = new VisualizationLineChart();
    }
    const setting = this.config.setting.lineChart;
    setting.lineWithFocus = this.lineWithFocus;
    setting.forceY = this.forceY;
    setting.isDateFormat = this.isDateFormat;
    setting.dateFormat = this.dateFormat;
    this.visualization.configChange$.next(this.config);
  }

  constructor(
    @Inject(VISUALIZATION) public visualization: Visualization,
    private cdr: ChangeDetectorRef
  ) {
    super(visualization);
  }

  ngOnInit() {}

  refreshSetting(config: GraphConfig) {
    if (!config.setting.lineChart) {
      config.setting.lineChart = new VisualizationLineChart();
    }
    const setting = config.setting.lineChart;
    this.forceY = setting.forceY || false;
    this.lineWithFocus = setting.lineWithFocus || false;
    this.isDateFormat = setting.isDateFormat || false;
    this.dateFormat = setting.dateFormat || '';
    this.pivotSettingComponent.init();
    this.xAxisSettingComponent.init();
    this.cdr.markForCheck();
  }

  setScale(chart: G2.Chart) {
    const key = this.getKey();
    const tickCount = calcTickCount(this.container.nativeElement);
    chart.scale(key, {
      tickCount,
      type: 'cat'
    });
  }

  renderBefore(config: GraphConfig, chart: G2.Chart) {
    const key = this.getKey();
    if (!config.setting.lineChart) {
      config.setting.lineChart = new VisualizationLineChart();
    }
    const setting = config.setting.lineChart;
    this.setScale(chart);
    chart.line().position(`${key}*__value__`).color('__key__');
    setChartXAxis(this.visualization, 'lineChart', chart, key);

    if (setting.isDateFormat) {
      if (this.visualization.transformed && this.visualization.transformed.rows) {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const invalid = this.visualization.transformed.rows.some((r: any) => {
          const isInvalidDate = Number.isNaN(new Date(r[key]).valueOf());
          if (isInvalidDate) {
            console.warn(`${r[key]} is [Invalid Date]`);
          }
          return isInvalidDate;
        });
        if (invalid) {
          return;
        }
        chart.scale({
          [key]: {
            type: 'time',
            mask: setting.dateFormat || 'YYYY-MM-DD'
          }
        });
      }
    }

    if (setting.forceY) {
      chart.scale({
        __value__: {
          min: 0
        }
      });
    }
  }

  renderAfter(config: GraphConfig) {
    if (!config.setting.lineChart) {
      config.setting.lineChart = new VisualizationLineChart();
    }
    const setting = config.setting.lineChart;
    if (setting.lineWithFocus) {
      // eslint-disable-next-line
      (this.chart as any).interact('brush');
    } else {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (this.chart as any).clearInteraction();
    }
  }

  ngAfterViewInit() {
    this.render();
  }
}
