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

import { ElementRef, OnDestroy } from '@angular/core';

import * as G2 from '@antv/g2';

import { GraphConfig } from '@zeppelin/sdk';
import { Visualization } from './visualization';

export abstract class G2VisualizationComponentBase implements OnDestroy {
  abstract container: ElementRef<HTMLDivElement>;
  chart: G2.Chart;
  config: GraphConfig;

  constructor(public visualization: Visualization) {}

  abstract renderBefore(chart: G2.Chart): void;

  abstract refreshSetting(): void;
  abstract setScale(): void;

  render() {
    this.config = this.visualization.getConfig();
    this.refreshSetting();
    this.initChart();
    this.chart.source(this.visualization.transformed);
    this.renderBefore(this.chart);
    this.chart.render();
    this.renderAfter();
  }

  renderAfter(): void {}

  getKey(): string {
    let key = '';
    if (this.config.keys && this.config.keys[0]) {
      key = this.config.keys[0].name;
    }
    return key;
  }

  refresh(): void {
    this.config = this.visualization.getConfig();
    this.chart.changeHeight(this.config.height || 400);
    setTimeout(() => {
      this.setScale();
      this.chart.forceFit();
    });
  }

  initChart() {
    if (this.chart) {
      this.chart.clear();
    } else {
      if (this.container && this.container.nativeElement) {
        this.chart = new G2.Chart({
          forceFit: true,
          container: this.container.nativeElement,
          height: this.config.height || 400,
          padding: {
            top: 80,
            left: 50,
            right: 50,
            bottom: 50
          }
        });
        this.chart.legend({
          position: 'top-right'
          // tslint:disable-next-line
        } as any);
      } else {
        throw new Error(`Can't find the container, Please make sure on correct assignment.`);
      }
    }
  }

  ngOnDestroy(): void {
    if (this.chart) {
      this.chart.destroy();
      this.chart = null;
    }
  }
}
