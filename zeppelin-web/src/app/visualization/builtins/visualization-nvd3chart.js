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
zeppelin.Nvd3ChartVisualization = function(targetEl, config) {
  zeppelin.Visualization.call(this, targetEl, config);
  this.targetEl.append('<svg></svg>');
};

zeppelin.Nvd3ChartVisualization.prototype = Object.create(zeppelin.Visualization.prototype);

zeppelin.Visualization.prototype.refresh = function() {
  if (this.chart) {
    this.chart.update();
  }
};

zeppelin.Nvd3ChartVisualization.prototype.render = function(data) {
  var type = this.type();
  var d3g = data.d3g;

  if (!this.chart) {
    this.chart = nv.models[type]();
  }

  this.configureChart(this.chart);

  var animationDuration = 300;
  var numberOfDataThreshold = 150;
  var height = this.targetEl.height();

  // turn off animation when dataset is too large. (for performance issue)
  // still, since dataset is large, the chart content sequentially appears like animated.
  try {
    if (d3g[0].values.length > numberOfDataThreshold) {
      animationDuration = 0;
    }
  } catch (ignoreErr) {
  }

  d3.select('#' + this.targetEl[0].id + ' svg')
    .attr('height', height)
    .datum(d3g)
    .transition()
    .duration(animationDuration)
    .call(this.chart);
  d3.select('#' + this.targetEl[0].id + ' svg').style.height = height + 'px';
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

zeppelin.Nvd3ChartVisualization.prototype.d3DataFromPivot = function(
  schema, rows, keys, groups, values, allowTextXAxis, fillMissingValues, multiBarChart) {
  // construct table data
  var d3g = [];

  var concat = function(o, n) {
    if (!o) {
      return n;
    } else {
      return o + '.' + n;
    }
  };

  var getSchemaUnderKey = function(key, s) {
    for (var c in key.children) {
      s[c] = {};
      getSchemaUnderKey(key.children[c], s[c]);
    }
  };

  var traverse = function(sKey, s, rKey, r, func, rowName, rowValue, colName) {
    //console.log("TRAVERSE sKey=%o, s=%o, rKey=%o, r=%o, rowName=%o, rowValue=%o, colName=%o", sKey, s, rKey, r, rowName, rowValue, colName);

    if (s.type === 'key') {
      rowName = concat(rowName, sKey);
      rowValue = concat(rowValue, rKey);
    } else if (s.type === 'group') {
      colName = concat(colName, rKey);
    } else if (s.type === 'value' && sKey === rKey || valueOnly) {
      colName = concat(colName, rKey);
      func(rowName, rowValue, colName, r);
    }

    for (var c in s.children) {
      if (fillMissingValues && s.children[c].type === 'group' && r[c] === undefined) {
        var cs = {};
        getSchemaUnderKey(s.children[c], cs);
        traverse(c, s.children[c], c, cs, func, rowName, rowValue, colName);
        continue;
      }

      for (var j in r) {
        if (s.children[c].type === 'key' || c === j) {
          traverse(c, s.children[c], j, r[j], func, rowName, rowValue, colName);
        }
      }
    }
  };

  var valueOnly = (keys.length === 0 && groups.length === 0 && values.length > 0);
  var noKey = (keys.length === 0);
  var isMultiBarChart = multiBarChart;

  var sKey = Object.keys(schema)[0];

  var rowNameIndex = {};
  var rowIdx = 0;
  var colNameIndex = {};
  var colIdx = 0;
  var rowIndexValue = {};

  for (var k in rows) {
    traverse(sKey, schema[sKey], k, rows[k], function(rowName, rowValue, colName, value) {
      //console.log("RowName=%o, row=%o, col=%o, value=%o", rowName, rowValue, colName, value);
      if (rowNameIndex[rowValue] === undefined) {
        rowIndexValue[rowIdx] = rowValue;
        rowNameIndex[rowValue] = rowIdx++;
      }

      if (colNameIndex[colName] === undefined) {
        colNameIndex[colName] = colIdx++;
      }
      var i = colNameIndex[colName];
      if (noKey && isMultiBarChart) {
        i = 0;
      }

      if (!d3g[i]) {
        d3g[i] = {
          values: [],
          key: (noKey && isMultiBarChart) ? 'values' : colName
        };
      }

      var xVar = isNaN(rowValue) ? ((allowTextXAxis) ? rowValue : rowNameIndex[rowValue]) : parseFloat(rowValue);
      var yVar = 0;
      if (xVar === undefined) { xVar = colName; }
      if (value !== undefined) {
        yVar = isNaN(value.value) ? 0 : parseFloat(value.value) / parseFloat(value.count);
      }
      d3g[i].values.push({
        x: xVar,
        y: yVar
      });
    });
  }

  // clear aggregation name, if possible
  var namesWithoutAggr = {};
  var colName;
  var withoutAggr;
  // TODO - This part could use som refactoring - Weird if/else with similar actions and variable names
  for (colName in colNameIndex) {
    withoutAggr = colName.substring(0, colName.lastIndexOf('('));
    if (!namesWithoutAggr[withoutAggr]) {
      namesWithoutAggr[withoutAggr] = 1;
    } else {
      namesWithoutAggr[withoutAggr]++;
    }
  }

  if (valueOnly) {
    for (var valueIndex = 0; valueIndex < d3g[0].values.length; valueIndex++) {
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
    for (var d3gIndex = 0; d3gIndex < d3g.length; d3gIndex++) {
      colName = d3g[d3gIndex].key;
      withoutAggr = colName.substring(0, colName.lastIndexOf('('));
      if (namesWithoutAggr[withoutAggr] <= 1) {
        d3g[d3gIndex].key = withoutAggr;
      }
    }

    // use group name instead of group.value as a column name, if there're only one group and one value selected.
    if (groups.length === 1 && values.length === 1) {
      for (d3gIndex = 0; d3gIndex < d3g.length; d3gIndex++) {
        colName = d3g[d3gIndex].key;
        colName = colName.split('.').slice(0, -1).join('.');
        d3g[d3gIndex].key = colName;
      }
    }
  }

  return {
    xLabels: rowIndexValue,
    d3g: d3g
  };
};

/**
 * method will be invoked when visualization need to be destroyed.
 * Don't need to destroy this.targetEl.
 */
zeppelin.Visualization.prototype.destroy = function() {
  if (this.chart) {
    d3.selectAll('#' + this.targetEl[0].id + ' svg > *').remove();
    this.chart = undefined;
  }
};
