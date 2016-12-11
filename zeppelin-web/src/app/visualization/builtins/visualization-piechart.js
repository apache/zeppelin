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
 * Visualize data in pie chart
 */
zeppelin.PiechartVisualization = function(targetEl, config) {
  zeppelin.Nvd3ChartVisualization.call(this, targetEl, config);

  var PivotTransformation = zeppelin.PivotTransformation;
  this.pivot = new PivotTransformation(config);
};

zeppelin.PiechartVisualization.prototype = Object.create(zeppelin.Nvd3ChartVisualization.prototype);

zeppelin.PiechartVisualization.prototype.type = function() {
  return 'pieChart';
};

zeppelin.PiechartVisualization.prototype.getTransformation = function() {
  return this.pivot;
};

zeppelin.PiechartVisualization.prototype.render = function(pivot) {
  var d3Data = this.d3DataFromPivot(
    pivot.schema,
    pivot.rows,
    pivot.keys,
    pivot.groups,
    pivot.values,
    true,
    false,
    false);

  var d = d3Data.d3g;
  var d3g = [];
  if (d.length > 0) {
    for (var i = 0; i < d[0].values.length ; i++) {
      var e = d[0].values[i];
      d3g.push({
        label: e.x,
        value: e.y
      });
    }
  }
  zeppelin.Nvd3ChartVisualization.prototype.render.call(this, {d3g: d3g});
};

/**
 * Set new config
 */
zeppelin.PiechartVisualization.prototype.setConfig = function(config) {
  zeppelin.Nvd3ChartVisualization.prototype.setConfig.call(this, config);
  this.pivot.setConfig(config);
};

zeppelin.PiechartVisualization.prototype.configureChart = function(chart) {
  chart.x(function(d) { return d.label;}).y(function(d) { return d.value;});
};
