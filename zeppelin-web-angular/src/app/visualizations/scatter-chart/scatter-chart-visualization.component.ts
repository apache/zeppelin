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

import { get } from 'lodash';

import { G2VisualizationComponentBase, Visualization, VISUALIZATION } from '@zeppelin/visualization';

import { VisualizationScatterSettingComponent } from '../common/scatter-setting/scatter-setting.component';
import { calcTickCount } from '../common/util/calc-tick-count';

@Component({
  selector: 'zeppelin-scatter-chart-visualization',
  templateUrl: './scatter-chart-visualization.component.html',
  styleUrls: ['./scatter-chart-visualization.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ScatterChartVisualizationComponent extends G2VisualizationComponentBase implements OnInit, AfterViewInit {
  @ViewChild('container', { static: false }) container: ElementRef<HTMLDivElement>;
  @ViewChild(VisualizationScatterSettingComponent, { static: false })
  scatterSettingComponent: VisualizationScatterSettingComponent;

  constructor(@Inject(VISUALIZATION) public visualization: Visualization, private cdr: ChangeDetectorRef) {
    super(visualization);
  }

  refreshSetting() {
    this.scatterSettingComponent.init();
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

  renderBefore() {
    const key = this.getKey();
    const size = get(this.config.setting, 'scatterChart.size.name');
    this.setScale();
    this.chart.tooltip({
      crosshairs: {
        type: 'cross'
      }
    });
    this.chart.legend('__value__', false);
    // point
    const geom = this.chart
      .point()
      .position(`${key}*__value__`)
      .color('__key__')
      // .adjust('jitter')
      .opacity(0.65)
      .shape('circle');

    if (size) {
      geom.size('__value__');
    }
  }

  ngOnInit() {}

  ngAfterViewInit() {
    this.render();
  }
}
