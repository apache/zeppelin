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
 * Visualize data in scatter char
 */
zeppelin.ScatterchartVisualization = function(targetEl, config) {
  zeppelin.Nvd3ChartVisualization.call(this, targetEl, config);
};

zeppelin.ScatterchartVisualization.prototype = Object.create(zeppelin.Nvd3ChartVisualization.prototype);

zeppelin.ScatterchartVisualization.prototype.type = function() {
  return 'scatterChart';
};

zeppelin.ScatterchartVisualization.prototype.getTransformation = function() {
  return this.pivot;
};

zeppelin.ScatterchartVisualization.prototype.render = function(tableData) {
  this.tableData = tableData;
  var d3Data = this.setScatterChart(tableData, true);
  this.xLabels = d3Data.xLabels;
  this.yLabels = d3Data.yLabels;

  zeppelin.Nvd3ChartVisualization.prototype.render.call(this, d3Data);
};

zeppelin.ScatterchartVisualization.prototype.configureChart = function(chart) {
  var self = this;

  chart.xAxis.tickFormat(function(d) {return self.xAxisTickFormat(d, self.xLabels);});
  chart.yAxis.tickFormat(function(d) {return self.yAxisTickFormat(d, self.yLabels);});

  // configure how the tooltip looks.
  chart.tooltipContent(function(key, x, y, graph, data) {
    var tooltipContent = '<h3>' + key + '</h3>';

    if (self.config.scatter.labels) {
      tooltipContent += '<p>' + data.point.label + '</p>';
    }

    if (self.config.scatter.size &&
      self.isValidSizeOption(self.config.scatter, self.tableData.rows)) {
      tooltipContent += '<p>' + data.point.size + '</p>';
    }

    return tooltipContent;
  });

  chart.showDistX(true).showDistY(true);
  //handle the problem of tooltip not showing when muliple points have same value.
};

zeppelin.ScatterchartVisualization.prototype.setScatterChart = function(data, refresh) {
  var xAxis = this.config.scatter.xAxis;
  var yAxis = this.config.scatter.yAxis;
  var group = this.config.scatter.group;
  var size = this.config.scatter.size;
  var labels = this.config.scatter.labels;

  var xValues = [];
  var yValues = [];
  var rows = {};
  var d3g = [];

  var rowNameIndex = {};
  var colNameIndex = {};
  var grpNameIndex = {};
  var rowIndexValue = {};
  var colIndexValue = {};
  var grpIndexValue = {};
  var rowIdx = 0;
  var colIdx = 0;
  var grpIdx = 0;
  var grpName = '';
  var labelValue = '';

  var xValue;
  var yValue;
  var row;

  if (!xAxis && !yAxis) {
    return {
      d3g: []
    };
  }

  for (var i = 0; i < data.rows.length; i++) {
    row = data.rows[i];
    if (xAxis) {
      xValue = row[xAxis.index];
      xValues[i] = xValue;
    }
    if (yAxis) {
      yValue = row[yAxis.index];
      yValues[i] = yValue;
    }
  }

  var isAllDiscrete = ((xAxis && yAxis && this.isDiscrete(xValues) && this.isDiscrete(yValues)) ||
  (!xAxis && this.isDiscrete(yValues)) ||
  (!yAxis && this.isDiscrete(xValues)));

  if (isAllDiscrete) {
    rows = this.setDiscreteScatterData(data);
  } else {
    rows = data.rows;
  }

  if (!group && isAllDiscrete) {
    grpName = 'count';
  } else if (!group && !size) {
    if (xAxis && yAxis) {
      grpName = '(' + xAxis.name + ', ' + yAxis.name + ')';
    } else if (xAxis && !yAxis) {
      grpName = xAxis.name;
    } else if (!xAxis && yAxis) {
      grpName = yAxis.name;
    }
  } else if (!group && size) {
    grpName = size.name;
  }

  for (i = 0; i < rows.length; i++) {
    row = rows[i];
    if (xAxis) {
      xValue = row[xAxis.index];
    }
    if (yAxis) {
      yValue = row[yAxis.index];
    }
    if (group) {
      grpName = row[group.index];
    }
    if (labels) {
      labelValue = row[labels.index];
    }
    var sz = (isAllDiscrete) ? row[row.length - 1] : ((size) ? row[size.index] : 1);

    if (grpNameIndex[grpName] === undefined) {
      grpIndexValue[grpIdx] = grpName;
      grpNameIndex[grpName] = grpIdx++;
    }

    if (xAxis && rowNameIndex[xValue] === undefined) {
      rowIndexValue[rowIdx] = xValue;
      rowNameIndex[xValue] = rowIdx++;
    }

    if (yAxis && colNameIndex[yValue] === undefined) {
      colIndexValue[colIdx] = yValue;
      colNameIndex[yValue] = colIdx++;
    }

    if (!d3g[grpNameIndex[grpName]]) {
      d3g[grpNameIndex[grpName]] = {
        key: grpName,
        values: []
      };
    }

    d3g[grpNameIndex[grpName]].values.push({
      x: xAxis ? (isNaN(xValue) ? rowNameIndex[xValue] : parseFloat(xValue)) : 0,
      y: yAxis ? (isNaN(yValue) ? colNameIndex[yValue] : parseFloat(yValue)) : 0,
      size: isNaN(parseFloat(sz)) ? 1 : parseFloat(sz),
      label: labelValue,
    });
  }

  return {
    xLabels: rowIndexValue,
    yLabels: colIndexValue,
    d3g: d3g
  };
};
zeppelin.ScatterchartVisualization.prototype.setDiscreteScatterData = function(data) {
  var xAxis = this.config.scatter.xAxis;
  var yAxis = this.config.scatter.yAxis;
  var group = this.config.scatter.group;

  var xValue;
  var yValue;
  var grp;

  var rows = {};

  for (var i = 0; i < data.rows.length; i++) {
    var row = data.rows[i];
    if (xAxis) {
      xValue = row[xAxis.index];
    }
    if (yAxis) {
      yValue = row[yAxis.index];
    }
    if (group) {
      grp = row[group.index];
    }

    var key = xValue + ',' + yValue +  ',' + grp;

    if (!rows[key]) {
      rows[key] = {
        x: xValue,
        y: yValue,
        group: grp,
        size: 1
      };
    } else {
      rows[key].size++;
    }
  }

  // change object into array
  var newRows = [];
  for (var r in rows) {
    var newRow = [];
    if (xAxis) { newRow[xAxis.index] = rows[r].x; }
    if (yAxis) { newRow[yAxis.index] = rows[r].y; }
    if (group) { newRow[group.index] = rows[r].group; }
    newRow[data.rows[0].length] = rows[r].size;
    newRows.push(newRow);
  }
  return newRows;
};

zeppelin.ScatterchartVisualization.prototype.isDiscrete = function(field) {
  var getUnique = function(f) {
    var uniqObj = {};
    var uniqArr = [];
    var j = 0;
    for (var i = 0; i < f.length; i++) {
      var item = f[i];
      if (uniqObj[item] !== 1) {
        uniqObj[item] = 1;
        uniqArr[j++] = item;
      }
    }
    return uniqArr;
  };

  for (var i = 0; i < field.length; i++) {
    if (isNaN(parseFloat(field[i])) &&
      (typeof field[i] === 'string' || field[i] instanceof String)) {
      return true;
    }
  }

  var threshold = 0.05;
  var unique = getUnique(field);
  if (unique.length / field.length < threshold) {
    return true;
  } else {
    return false;
  }
};

zeppelin.ScatterchartVisualization.prototype.isValidSizeOption = function(options) {
  var xValues = [];
  var yValues = [];
  var rows = this.tableData.rows;

  for (var i = 0; i < rows.length; i++) {
    var row = rows[i];
    var size = row[options.size.index];

    //check if the field is numeric
    if (isNaN(parseFloat(size)) || !isFinite(size)) {
      return false;
    }

    if (options.xAxis) {
      var x = row[options.xAxis.index];
      xValues[i] = x;
    }
    if (options.yAxis) {
      var y = row[options.yAxis.index];
      yValues[i] = y;
    }
  }

  //check if all existing fields are discrete
  var isAllDiscrete = ((options.xAxis && options.yAxis && this.isDiscrete(xValues) && this.isDiscrete(yValues)) ||
  (!options.xAxis && this.isDiscrete(yValues)) ||
  (!options.yAxis && this.isDiscrete(xValues)));

  if (isAllDiscrete) {
    return false;
  }

  return true;
};
