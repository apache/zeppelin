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
zeppelin.Nvd3ChartVisualization = function(targetEl) {
  zeppelin.Visualization.call(this, targetEl);
};

zeppelin.Nvd3ChartVisualization.prototype = Object.create(zeppelin.Visualization.prototype);

zeppelin.Nvd3ChartVisualization.prototype.render = function(tableData) {
  var type = this.type();
  console.log('chart type = %o', type);

  if (!this.chart) {
    this.chart = nv.models[type]();
  }

  tableData.pivot(['key'],[],['value']);

  this.configureChart(this.chart);
};

zeppelin.Nvd3ChartVisualization.prototype.type = function() {
  // override this and return chart type
};

zeppelin.Nvd3ChartVisualization.prototype.configureChart = function(chart) {
  // override this to configure chart
};

zeppelin.Nvd3ChartVisualization.prototype.groupedThousandsWith3DigitsFormatter = function(x) {
  return d3.format(',')(d3.round(x, 3));
};

zeppelin.Nvd3ChartVisualization.prototype.customAbbrevFormatter = function(x) {
  var s = d3.format('.3s')(x);
  switch (s[s.length - 1]) {
    case 'G': return s.slice(0, -1) + 'B';
  }
  return s;
};

zeppelin.Nvd3ChartVisualization.prototype.xAxisTickFormat = function(d, xLabels) {
  if (xLabels[d] && (isNaN(parseFloat(xLabels[d])) || !isFinite(xLabels[d]))) { // to handle string type xlabel
    return xLabels[d];
  } else {
    return d;
  }
};

zeppelin.Nvd3ChartVisualization.prototype.yAxisTickFormat = function(d) {
  if (Math.abs(d) >= Math.pow(10,6)) {
    return this.customAbbrevFormatter(d);
  }
  return this.groupedThousandsWith3DigitsFormatter(d);
};
