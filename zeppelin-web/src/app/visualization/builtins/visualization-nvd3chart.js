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

import Visualization from '../visualization';

/**
 * Visualize data in table format
 */
export default class Nvd3ChartVisualization extends Visualization {
  constructor(targetEl, config) {
    super(targetEl, config);
    this.targetEl.append('<svg></svg>');
  }

  refresh() {
    if (this.chart) {
      this.chart.update();
    }
  }

  render(data) {
    let type = this.type();
    let d3g = data.d3g;

    if (!this.chart) {
      this.chart = nv.models[type]();
    }

    this.configureChart(this.chart);

    let animationDuration = 300;
    let numberOfDataThreshold = 150;
    let height = this.targetEl.height();

    // turn off animation when dataset is too large. (for performance issue)
    // still, since dataset is large, the chart content sequentially appears like animated
    try {
      if (d3g[0].values.length > numberOfDataThreshold) {
        animationDuration = 0;
      }
    } catch (err) { /** ignore */ }

    d3.select('#' + this.targetEl[0].id + ' svg')
      .attr('height', height)
      .datum(d3g)
      .transition()
      .duration(animationDuration)
      .call(this.chart);
    d3.select('#' + this.targetEl[0].id + ' svg').style.height = height + 'px';
  }

  type() {
    // override this and return chart type
  }

  configureChart(chart) {
    // override this to configure chart
  }

  groupedThousandsWith3DigitsFormatter(x) {
    return d3.format(',')(d3.round(x, 3));
  }

  customAbbrevFormatter(x) {
    let s = d3.format('.3s')(x);
    switch (s[s.length - 1]) {
      case 'G': return s.slice(0, -1) + 'B';
    }
    return s;
  }

  defaultY() {
    return 0;
  }

  xAxisTickFormat(d, xLabels) {
    if (xLabels[d] && (isNaN(parseFloat(xLabels[d])) || !isFinite(xLabels[d]))) { // to handle string type xlabel
      return xLabels[d];
    } else {
      return d;
    }
  }

  yAxisTickFormat(d) {
    if (Math.abs(d) >= Math.pow(10, 6)) {
      return this.customAbbrevFormatter(d);
    }
    return this.groupedThousandsWith3DigitsFormatter(d);
  }

  d3DataFromPivot(
    schema, rows, keys, groups, values, allowTextXAxis, fillMissingValues, multiBarChart) {
    let self = this;
    // construct table data
    let d3g = [];

    let concat = function(o, n) {
      if (!o) {
        return n;
      } else {
        return o + '.' + n;
      }
    };

    const getSchemaUnderKey = function(key, s) {
      for (let c in key.children) {
        if(key.children.hasOwnProperty(c)) {
          s[c] = {};
          getSchemaUnderKey(key.children[c], s[c]);
        }
      }
    };

    const traverse = function(sKey, s, rKey, r, func, rowName, rowValue, colName) {
      // console.log("TRAVERSE sKey=%o, s=%o, rKey=%o, r=%o, rowName=%o, rowValue=%o, colName=%o", sKey, s, rKey, r, rowName, rowValue, colName);

      if (s.type === 'key') {
        rowName = concat(rowName, sKey);
        rowValue = concat(rowValue, rKey);
      } else if (s.type === 'group') {
        colName = concat(colName, rKey);
      } else if (s.type === 'value' && sKey === rKey || valueOnly) {
        colName = concat(colName, rKey);
        func(rowName, rowValue, colName, r);
      }

      for (let c in s.children) {
        if (fillMissingValues && s.children[c].type === 'group' && r[c] === undefined) {
          let cs = {};
          getSchemaUnderKey(s.children[c], cs);
          traverse(c, s.children[c], c, cs, func, rowName, rowValue, colName);
          continue;
        }

        for (let j in r) {
          if (s.children[c].type === 'key' || c === j) {
            traverse(c, s.children[c], j, r[j], func, rowName, rowValue, colName);
          }
        }
      }
    };

    const valueOnly = (keys.length === 0 && groups.length === 0 && values.length > 0);
    let noKey = (keys.length === 0);
    let isMultiBarChart = multiBarChart;

    let sKey = Object.keys(schema)[0];

    let rowNameIndex = {};
    let rowIdx = 0;
    let colNameIndex = {};
    let colIdx = 0;
    let rowIndexValue = {};

    for (let k in rows) {
      if (rows.hasOwnProperty(k)) {
        traverse(sKey, schema[sKey], k, rows[k], function(rowName, rowValue, colName, value) {
          // console.log("RowName=%o, row=%o, col=%o, value=%o", rowName, rowValue, colName, value);
          if (rowNameIndex[rowValue] === undefined) {
            rowIndexValue[rowIdx] = rowValue;
            rowNameIndex[rowValue] = rowIdx++;
          }

          if (colNameIndex[colName] === undefined) {
            colNameIndex[colName] = colIdx++;
          }
          let i = colNameIndex[colName];
          if (noKey && isMultiBarChart) {
            i = 0;
          }

          if (!d3g[i]) {
            d3g[i] = {
              values: [],
              key: (noKey && isMultiBarChart) ? 'values' : colName,
            };
          }

          let xVar = isNaN(rowValue) ? ((allowTextXAxis) ? rowValue : rowNameIndex[rowValue]) : parseFloat(rowValue);
          let yVar = self.defaultY();
          if (xVar === undefined) {
            xVar = colName;
          }
          if (value !== undefined) {
            yVar = isNaN(value.value) ? self.defaultY() : parseFloat(value.value) / parseFloat(value.count);
          }
          d3g[i].values.push({
            x: xVar,
            y: yVar,
          });
        });
      }
    }

    // clear aggregation name, if possible
    let namesWithoutAggr = {};
    let colName;
    let withoutAggr;
    // TODO - This part could use som refactoring - Weird if/else with similar actions and variable names
    for (colName in colNameIndex) {
      if (colNameIndex.hasOwnProperty(colName)) {
        withoutAggr = colName.substring(0, colName.lastIndexOf('('));
        if (!namesWithoutAggr[withoutAggr]) {
          namesWithoutAggr[withoutAggr] = 1;
        } else {
          namesWithoutAggr[withoutAggr]++;
        }
      }
    }

    if (valueOnly) {
      for (let valueIndex = 0; valueIndex < d3g[0].values.length; valueIndex++) {
        colName = d3g[0].values[valueIndex].x;
        if (!colName) {
          continue;
        }

        withoutAggr = colName.substring(0, colName.lastIndexOf('('));
        if (namesWithoutAggr[withoutAggr] <= 1) {
          d3g[0].values[valueIndex].x = withoutAggr;
        }
      }
    } else {
      for (let d3gIndex = 0; d3gIndex < d3g.length; d3gIndex++) {
        colName = d3g[d3gIndex].key;
        withoutAggr = colName.substring(0, colName.lastIndexOf('('));
        if (namesWithoutAggr[withoutAggr] <= 1) {
          d3g[d3gIndex].key = withoutAggr;
        }
      }

      // use group name instead of group.value as a column name, if there're only one group and one value selected.
      if (groups.length === 1 && values.length === 1) {
        for (let d3gIndex = 0; d3gIndex < d3g.length; d3gIndex++) {
          colName = d3g[d3gIndex].key;
          colName = colName.split('.').slice(0, -1).join('.');
          d3g[d3gIndex].key = colName;
        }
      }
    }

    return {
      xLabels: rowIndexValue,
      d3g: d3g,
    };
  }

  /**
   * method will be invoked when visualization need to be destroyed.
   * Don't need to destroy this.targetEl.
   */
  destroy() {
    if (this.chart) {
      d3.selectAll('#' + this.targetEl[0].id + ' svg > *').remove();
      this.chart = undefined;
    }
  }
}
