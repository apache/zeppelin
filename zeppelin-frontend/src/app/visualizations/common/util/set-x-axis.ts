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

import * as G2 from '@antv/g2';
import { get } from 'lodash';

import { Visualization } from '@zeppelin/visualization';

export function setChartXAxis(
  visualization: Visualization,
  mode: 'lineChart' | 'multiBarChart' | 'stackedAreaChart',
  chart: G2.Chart,
  key: string
) {
  const config = visualization.getConfig();
  const setting = config.setting[mode];
  chart.axis(key, {
    label: {
      textStyle: {
        rotate: 0
      }
    }
  });
  switch (setting.xLabelStatus) {
    case 'hide':
      chart.axis(key, false);
      break;
    case 'rotate':
      chart.axis(key, {
        label: {
          textStyle: {
            rotate: Number.parseInt(get(setting, 'rotate.degree', '-45'), 10)
          }
        }
      });
  }
}
