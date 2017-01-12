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
import ColumnselectorTransformation from '../../tabledata/columnselector';

/**
 * Visualize data in scatter char
 */
export default class ScatterchartVisualization extends Nvd3ChartVisualization {
  constructor(targetEl, config) {
    super(targetEl, config);

    this.columnselectorProps = [
      {
        name: 'xAxis',
      },
      {
        name: 'yAxis',
      },
      {
        name: 'group',
      },
      {
        name: 'size',
        tooltip: `<li>Size option is valid only when you drop numeric field here.</li>
                  <li>When data in each axis are discrete,
                  'number of values in corresponding coordinate' will be used as size.</li>
                  <li>Zeppelin consider values as discrete when the values contain string value
                  or the number of distinct values are bigger than 5% of total number of values.</li>
                  <li>Size field button turns to grey when the option you chose is not valid.</li>`,
      },
    ];
    this.columnselector = new ColumnselectorTransformation(config, this.columnselectorProps);
  }

  type() {
    return 'scatterChart';
  }

  getTransformation() {
    return this.columnselector;
  }

  render(tableData) {
    this.tableData = tableData;
    this.selectDefault();
    let d3Data = this.setScatterChart(tableData, true);
    this.xLabels = d3Data.xLabels;
    this.yLabels = d3Data.yLabels;

    super.render(d3Data);
  }

  configureChart(chart) {
    let self = this;

    chart.xAxis.tickFormat(function(d) {
return self.xAxisTickFormat(d, self.xLabels);
});
    chart.yAxis.tickFormat(function(d) {
return self.yAxisTickFormat(d, self.yLabels);
});

    // configure how the tooltip looks.
    chart.tooltipContent(function(key, x, y, graph, data) {
      let tooltipContent = '<h3>' + key + '</h3>';
      if (self.config.size &&
        self.isValidSizeOption(self.config, self.tableData.rows)) {
        tooltipContent += '<p>' + data.point.size + '</p>';
      }

      return tooltipContent;
    });

    chart.showDistX(true).showDistY(true);
    // handle the problem of tooltip not showing when muliple points have same value.
  }

  selectDefault() {
    if (!this.config.xAxis && !this.config.yAxis) {
      if (this.tableData.columns.length > 1) {
        this.config.xAxis = this.tableData.columns[0];
        this.config.yAxis = this.tableData.columns[1];
      } else if (this.tableData.columns.length === 1) {
        this.config.xAxis = this.tableData.columns[0];
      }
    }
  }

  setScatterChart(data, refresh) {
    let xAxis = this.config.xAxis;
    let yAxis = this.config.yAxis;
    let group = this.config.group;
    let size = this.config.size;

    let xValues = [];
    let yValues = [];
    let rows = {};
    let d3g = [];

    let rowNameIndex = {};
    let colNameIndex = {};
    let grpNameIndex = {};
    let rowIndexValue = {};
    let colIndexValue = {};
    let grpIndexValue = {};
    let rowIdx = 0;
    let colIdx = 0;
    let grpIdx = 0;
    let grpName = '';

    let xValue;
    let yValue;
    let row;

    if (!xAxis && !yAxis) {
      return {
        d3g: [],
      };
    }

    for (let i = 0; i < data.rows.length; i++) {
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

    let isAllDiscrete = ((xAxis && yAxis && this.isDiscrete(xValues) && this.isDiscrete(yValues)) ||
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

    for (let i = 0; i < rows.length; i++) {
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
      let sz = (isAllDiscrete) ? row[row.length - 1] : ((size) ? row[size.index] : 1);

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
          values: [],
        };
      }

      d3g[grpNameIndex[grpName]].values.push({
        x: xAxis ? (isNaN(xValue) ? rowNameIndex[xValue] : parseFloat(xValue)) : 0,
        y: yAxis ? (isNaN(yValue) ? colNameIndex[yValue] : parseFloat(yValue)) : 0,
        size: isNaN(parseFloat(sz)) ? 1 : parseFloat(sz),
      });
    }

    return {
      xLabels: rowIndexValue,
      yLabels: colIndexValue,
      d3g: d3g,
    };
  }

  setDiscreteScatterData(data) {
    let xAxis = this.config.xAxis;
    let yAxis = this.config.yAxis;
    let group = this.config.group;

    let xValue;
    let yValue;
    let grp;

    let rows = {};

    for (let i = 0; i < data.rows.length; i++) {
      let row = data.rows[i];
      if (xAxis) {
        xValue = row[xAxis.index];
      }
      if (yAxis) {
        yValue = row[yAxis.index];
      }
      if (group) {
        grp = row[group.index];
      }

      let key = xValue + ',' + yValue + ',' + grp;

      if (!rows[key]) {
        rows[key] = {
          x: xValue,
          y: yValue,
          group: grp,
          size: 1,
        };
      } else {
        rows[key].size++;
      }
    }

    // change object into array
    let newRows = [];
    for (let r in rows) {
      let newRow = [];
      if (xAxis) {
 newRow[xAxis.index] = rows[r].x;
}
      if (yAxis) {
 newRow[yAxis.index] = rows[r].y;
}
      if (group) {
 newRow[group.index] = rows[r].group;
}
      newRow[data.rows[0].length] = rows[r].size;
      newRows.push(newRow);
    }
    return newRows;
  }

  isDiscrete(field) {
    let getUnique = function(f) {
      let uniqObj = {};
      let uniqArr = [];
      let j = 0;
      for (let i = 0; i < f.length; i++) {
        let item = f[i];
        if (uniqObj[item] !== 1) {
          uniqObj[item] = 1;
          uniqArr[j++] = item;
        }
      }
      return uniqArr;
    };

    for (let i = 0; i < field.length; i++) {
      if (isNaN(parseFloat(field[i])) &&
        (typeof field[i] === 'string' || field[i] instanceof String)) {
        return true;
      }
    }

    let threshold = 0.05;
    let unique = getUnique(field);
    if (unique.length / field.length < threshold) {
      return true;
    } else {
      return false;
    }
  }

  isValidSizeOption(options) {
    let xValues = [];
    let yValues = [];
    let rows = this.tableData.rows;

    for (let i = 0; i < rows.length; i++) {
      let row = rows[i];
      let size = row[options.size.index];

      // check if the field is numeric
      if (isNaN(parseFloat(size)) || !isFinite(size)) {
        return false;
      }

      if (options.xAxis) {
        let x = row[options.xAxis.index];
        xValues[i] = x;
      }
      if (options.yAxis) {
        let y = row[options.yAxis.index];
        yValues[i] = y;
      }
    }

    // check if all existing fields are discrete
    let isAllDiscrete = ((options.xAxis && options.yAxis && this.isDiscrete(xValues) && this.isDiscrete(yValues)) ||
    (!options.xAxis && this.isDiscrete(yValues)) ||
    (!options.yAxis && this.isDiscrete(xValues)));

    if (isAllDiscrete) {
      return false;
    }

    return true;
  }
}
