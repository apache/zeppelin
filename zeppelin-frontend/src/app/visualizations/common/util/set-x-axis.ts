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
