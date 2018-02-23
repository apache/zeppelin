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
 * Visualize data in pie chart
 */
export default class PiechartVisualization extends Nvd3ChartVisualization {
  constructor(targetEl, config) {
    super(targetEl, config);
    this.pivot = new PivotTransformation(config);
  }

  type() {
    return 'pieChart';
  }

  getTransformation() {
    return this.pivot;
  }

  render(pivot) {
    // [ZEPPELIN-2253] New chart function will be created each time inside super.render()
    this.chart = null;
    const d3Data = this.d3DataFromPivot(
      pivot.schema,
      pivot.rows,
      pivot.keys,
      pivot.groups,
      pivot.values,
      true,
      false,
      false);
    const d = d3Data.d3g;

    let generateLabel;
    // data is grouped
    if (pivot.groups && pivot.groups.length > 0) {
      generateLabel = (suffix, prefix) => `${prefix}.${suffix}`;
    } else { // data isn't grouped
      generateLabel = (suffix) => suffix;
    }

    let d3g = d.map((group) => {
      return group.values.map((row) => ({
        label: generateLabel(row.x, group.key),
        value: row.y,
      }));
    });
    // the map function returns d3g as a nested array
    // [].concat flattens it, http://stackoverflow.com/a/10865042/5154397
    d3g = [].concat.apply([], d3g); // eslint-disable-line prefer-spread
    super.render({d3g: d3g});
  }

  /**
   * Set new config
   */
  setConfig(config) {
    super.setConfig(config);
    this.pivot.setConfig(config);
  }

  configureChart(chart) {
    chart.x(function(d) {
      return d.label;
    })
    .y(function(d) {
      return d.value;
    })
    .showLabels(false)
    .showTooltipPercent(true);
  }
}
