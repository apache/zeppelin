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
 * Visualize data in bar char
 */
zeppelin.BarchartVisualization = function(targetEl, config) {
  zeppelin.Nvd3ChartVisualization.call(this, targetEl, config);

  var PivotTransformation = zeppelin.PivotTransformation;
  this.pivot = new PivotTransformation(config);
};

zeppelin.BarchartVisualization.prototype = Object.create(zeppelin.Nvd3ChartVisualization.prototype);

zeppelin.BarchartVisualization.prototype.type = function() {
  return 'multiBarChart';
};

zeppelin.BarchartVisualization.prototype.getTransformation = function() {
  return this.pivot;
};

zeppelin.BarchartVisualization.prototype.render = function(pivot) {
  var d3Data = this.d3DataFromPivot(
    pivot.schema,
    pivot.rows,
    pivot.keys,
    pivot.groups,
    pivot.values,
    true,
    false,
    true);

  zeppelin.Nvd3ChartVisualization.prototype.render.call(this, d3Data);
};

/**
 * Set new config
 */
zeppelin.BarchartVisualization.prototype.setConfig = function(config) {
  zeppelin.Nvd3ChartVisualization.prototype.setConfig.call(this, config);
  this.pivot.setConfig(config);
};

zeppelin.BarchartVisualization.prototype.configureChart = function(chart) {
  var self = this;
  chart.yAxis.axisLabelDistance(50);
  chart.yAxis.tickFormat(function(d) {return self.yAxisTickFormat(d);});

  this.chart.stacked(this.config.stacked);

  var self = this;
  this.chart.dispatch.on('stateChange', function(s) {
    self.config.stacked = s.stacked;

    // give some time to animation finish
    setTimeout(function() {
      self.emitConfig(self.config);
    }, 500);
  });
};
