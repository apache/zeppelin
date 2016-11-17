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
 * Visualize data in line chart
 */
zeppelin.LinechartVisualization = function(targetEl, config) {
  zeppelin.Nvd3ChartVisualization.call(this, targetEl, config);

  var PivotTransformation = zeppelin.PivotTransformation;
  this.pivot = new PivotTransformation(config);
  this.xLables = [];
};

zeppelin.LinechartVisualization.prototype = Object.create(zeppelin.Nvd3ChartVisualization.prototype);

zeppelin.LinechartVisualization.prototype.type = function() {
  if (this.config.lineWithFocus) {
    return 'lineWithFocusChart';
  } else {
    return 'lineChart';
  }
};

zeppelin.LinechartVisualization.prototype.getTransformation = function() {
  return this.pivot;
};

zeppelin.LinechartVisualization.prototype.render = function(tableData) {
  this.tableData = tableData;
  var pivot = this.pivot.transform(tableData);
  var d3Data = this.d3DataFromPivot(
    pivot.schema,
    pivot.rows,
    pivot.keys,
    pivot.groups,
    pivot.values,
    false,
    true,
    false);

  this.xLabels = d3Data.xLabels;
  zeppelin.Nvd3ChartVisualization.prototype.render.call(this, d3Data);
};

/**
 * Set new config
 */
zeppelin.LinechartVisualization.prototype.setConfig = function(config) {
  zeppelin.Nvd3ChartVisualization.prototype.setConfig.call(this, config);
  this.pivot.setConfig(config);

  // change mode
  if (this.currentMode !== config.lineWithFocus) {
    zeppelin.Nvd3ChartVisualization.prototype.destroy.call(this);
    this.currentMode = config.lineWithFocus;
  }
};

zeppelin.LinechartVisualization.prototype.configureChart = function(chart) {
  var self = this;
  chart.xAxis.tickFormat(function(d) {return self.xAxisTickFormat(d, self.xLabels);});
  chart.yAxis.tickFormat(function(d) {return self.yAxisTickFormat(d, self.xLabels);});
  chart.yAxis.axisLabelDistance(50);
  if (chart.useInteractiveGuideline) {   // lineWithFocusChart hasn't got useInteractiveGuideline
    chart.useInteractiveGuideline(true); // for better UX and performance issue. (https://github.com/novus/nvd3/issues/691)
  }
  if (this.config.forceY) {
    chart.forceY([0]); // force y-axis minimum to 0 for line chart.
  } else {
    chart.forceY([]);
  }
};
