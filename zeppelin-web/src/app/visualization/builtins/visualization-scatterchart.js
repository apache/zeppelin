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
  var ColumnselectorTransformation = zeppelin.ColumnselectorTransformation;
  this.columnselectorProps = [
    {
      name: 'xAxis'
    },
    {
      name: 'yAxis'
    },
    {
      name: 'group'
    },
    {
      name: 'size',
      tooltip: `<li>Size option is valid only when you drop numeric field here.</li>
                <li>When data in each axis are discrete,
                'number of values in corresponding coordinate' will be used as size.</li>
                <li>Zeppelin consider values as discrete when the values contain string value
                or the number of distinct values are bigger than 5% of total number of values.</li>
                <li>Size field button turns to grey when the option you chose is not valid.</li>`
    }
  ];
  this.columnselector = new ColumnselectorTransformation(config, this.columnselectorProps);
};

zeppelin.ScatterchartVisualization.prototype = Object.create(zeppelin.Nvd3ChartVisualization.prototype);

zeppelin.ScatterchartVisualization.prototype.type = function() {
  return 'scatterChart';
};

zeppelin.ScatterchartVisualization.prototype.getTransformation = function() {
  return this.columnselector;
};

zeppelin.ScatterchartVisualization.prototype.render = function(tableData) {
  this.tableData = tableData;
  this.selectDefault();
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
    if (self.config.size &&
      self.isValidSizeOption(self.config, self.tableData.rows)) {
      tooltipContent += '<p>' + data.point.size + '</p>';
    }

    return tooltipContent;
  });

  chart.showDistX(true).showDistY(true);
  //handle the problem of tooltip not showing when muliple points have same value.
};

zeppelin.ScatterchartVisualization.prototype.selectDefault = function() {
  if (!this.config.xAxis && !this.config.yAxis) {
    if (this.tableData.columns.length > 1) {
      this.config.xAxis = this.tableData.columns[0];
      this.config.yAxis = this.tableData.columns[1];
    } else if (this.tableData.columns.length === 1) {
      this.config.xAxis = this.tableData.columns[0];
    }
  }
};

zeppelin.ScatterchartVisualization.prototype.setScatterChart = function(data, refresh) {
  var xAxis = this.config.xAxis;
  var yAxis = this.config.yAxis;
  var group = this.config.group;
  var size = this.config.size;

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
      size: isNaN(parseFloat(sz)) ? 1 : parseFloat(sz)
    });
  }

  return {
    xLabels: rowIndexValue,
    yLabels: colIndexValue,
    d3g: d3g
  };
};
zeppelin.ScatterchartVisualization.prototype.setDiscreteScatterData = function(data) {
  var xAxis = this.config.xAxis;
  var yAxis = this.config.yAxis;
  var group = this.config.group;

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
