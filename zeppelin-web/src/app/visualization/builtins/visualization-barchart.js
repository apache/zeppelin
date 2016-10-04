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

'use strict';

/**
 * Visualize data in table format
 */
zeppelin.BarchartVisualization = function(targetEl) {
  zeppelin.Nvd3ChartVisualization.call(this, targetEl);
};

zeppelin.BarchartVisualization.prototype = Object.create(zeppelin.Nvd3ChartVisualization.prototype);

zeppelin.BarchartVisualization.prototype.type = function() {
  return 'multiBarChart';
};

zeppelin.BarchartVisualization.prototype.configureChart = function(chart) {
  // override this to configure chart
  chart.yAxis.axisLabelDistance(50);
  chart.yAxis.tickFormat(function(d) {return this.yAxisTickFormat(d);});
};
