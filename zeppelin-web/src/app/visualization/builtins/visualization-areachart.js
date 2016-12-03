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
 * Visualize data in area chart
 */
zeppelin.AreachartVisualization = function(targetEl, config) {
  zeppelin.Nvd3ChartVisualization.call(this, targetEl, config);

  var PivotTransformation = zeppelin.PivotTransformation;
  this.pivot = new PivotTransformation(config);
  this.xLables = [];
};

zeppelin.AreachartVisualization.prototype = Object.create(zeppelin.Nvd3ChartVisualization.prototype);

zeppelin.AreachartVisualization.prototype.type = function() {
  return 'stackedAreaChart';
};

zeppelin.AreachartVisualization.prototype.getTransformation = function() {
  return this.pivot;
};

zeppelin.AreachartVisualization.prototype.render = function(pivot) {
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
zeppelin.AreachartVisualization.prototype.setConfig = function(config) {
  zeppelin.Nvd3ChartVisualization.prototype.setConfig.call(this, config);
  this.pivot.setConfig(config);
};

zeppelin.AreachartVisualization.prototype.configureChart = function(chart) {
  var self = this;
  chart.xAxis.tickFormat(function(d) {return self.xAxisTickFormat(d, self.xLabels);});
  chart.yAxisTickFormat(function(d) {return self.yAxisTickFormat(d);});
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
