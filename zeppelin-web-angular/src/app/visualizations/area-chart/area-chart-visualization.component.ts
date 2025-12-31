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

import { GraphConfig, VisualizationStackedAreaChart } from '@zeppelin/sdk';
import { G2VisualizationComponentBase, Visualization, VISUALIZATION } from '@zeppelin/visualization';

import { VisualizationPivotSettingComponent } from '../common/pivot-setting/pivot-setting.component';
import { calcTickCount } from '../common/util/calc-tick-count';
import { setChartXAxis } from '../common/util/set-x-axis';
import { VisualizationXAxisSettingComponent } from '../common/x-axis-setting/x-axis-setting.component';

@Component({
  selector: 'zeppelin-area-chart-visualization',
  templateUrl: './area-chart-visualization.component.html',
  styleUrls: ['./area-chart-visualization.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AreaChartVisualizationComponent extends G2VisualizationComponentBase implements OnInit, AfterViewInit {
  @ViewChild('container', { static: false }) container!: ElementRef<HTMLDivElement>;
  @ViewChild(VisualizationXAxisSettingComponent, { static: false })
  xAxisSettingComponent!: VisualizationXAxisSettingComponent;
  @ViewChild(VisualizationPivotSettingComponent, { static: false })
  pivotSettingComponent!: VisualizationPivotSettingComponent;
  style: 'stream' | 'expand' | 'stack' = 'stack';

  constructor(
    @Inject(VISUALIZATION) public visualization: Visualization,
    private cdr: ChangeDetectorRef
  ) {
    super(visualization);
  }

  viewChange() {
    if (!this.config) {
      throw new Error('config is not defined');
    }
    if (!this.config.setting.stackedAreaChart) {
      this.config.setting.stackedAreaChart = new VisualizationStackedAreaChart();
    }
    this.config.setting.stackedAreaChart.style = this.style;
    if (!this.visualization.configChange$) {
      throw new Error('visualization.configChange$ is not defined');
    }
    this.visualization.configChange$.next(this.config);
  }

  ngOnInit() {}

  refreshSetting(config: GraphConfig) {
    if (!config.setting.stackedAreaChart) {
      throw new Error('config.setting.stackedAreaChart is not defined');
    }
    this.style = config.setting.stackedAreaChart.style;
    this.pivotSettingComponent.init();
    this.xAxisSettingComponent.init();
    this.cdr.markForCheck();
  }

  ngAfterViewInit(): void {
    this.render();
  }

  setScale(chart: G2.Chart) {
    const key = this.getKey();
    const tickCount = calcTickCount(this.container.nativeElement);
    chart.scale(key, {
      tickCount,
      type: 'cat'
    });
  }

  renderBefore(_config: GraphConfig, chart: G2.Chart) {
    const key = this.getKey();
    this.setScale(chart);
    if (this.style === 'stack') {
      // area:stack
      chart.areaStack().position(`${key}*__value__`).color('__key__');
    } else if (this.style === 'stream') {
      // area:stream
      chart.area().position(`${key}*__value__`).adjust(['stack', 'symmetric']).color('__key__');
    } else {
      // area:percent
      chart.areaStack().position(`${key}*__percent__`).color('__key__');
    }

    setChartXAxis(this.visualization, 'stackedAreaChart', chart, key);
  }
}
