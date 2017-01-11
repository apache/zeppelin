/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import Nvd3ChartVisualization from './visualization-nvd3chart';
import PivotTransformation from '../../tabledata/pivot';

/**
 * Visualize data in area chart
 */
export default class AreachartVisualization extends Nvd3ChartVisualization {
  constructor(targetEl, config) {
    super(targetEl, config);

    this.pivot = new PivotTransformation(config);
    this.xLables = [];
  };

  type() {
    return 'stackedAreaChart';
  };

  getTransformation() {
    return this.pivot;
  };

  render(pivot) {
    let d3Data = this.d3DataFromPivot(
      pivot.schema,
      pivot.rows,
      pivot.keys,
      pivot.groups,
      pivot.values,
      false,
      true,
      false);

    this.xLabels = d3Data.xLabels;
    super.render(d3Data);
  };

  /**
   * Set new config
   */
  setConfig(config) {
    super.setConfig(config);
    this.pivot.setConfig(config);
  };

  configureChart(chart) {
    var self = this;
    chart.xAxis.tickFormat(function(d) {
return self.xAxisTickFormat(d, self.xLabels);
});
    chart.yAxisTickFormat(function(d) {
return self.yAxisTickFormat(d);
});
    chart.yAxis.axisLabelDistance(50);
    chart.useInteractiveGuideline(true); // for better UX and performance issue. (https://github.com/novus/nvd3/issues/691)

    this.chart.style(this.config.style || 'stack');

    var self = this;
    this.chart.dispatch.on('stateChange', function(s) {
      self.config.style = s.style;

      // give some time to animation finish
      setTimeout(function() {
        self.emitConfig(self.config);
      }, 500);
    });
  };
}
