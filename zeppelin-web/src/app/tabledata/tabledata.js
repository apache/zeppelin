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

var zeppelin = zeppelin || {};

/**
 * Create table data object from paragraph table type result
 */
zeppelin.TableData = function(columns, rows, comment) {
  this.columns = columns || [];
  this.rows = rows || [];
  this.comment = comment || '';
};

zeppelin.TableData.prototype.loadParagraphResult = function(paragraphResult) {
  if (!paragraphResult || paragraphResult.type !== 'TABLE') {
    console.log('Can not load paragraph result');
    return;
  }

  var columnNames = [];
  var rows = [];
  var array = [];
  var textRows = paragraphResult.msg.split('\n');
  var comment = '';
  var commentRow = false;

  for (var i = 0; i < textRows.length; i++) {
    var textRow = textRows[i];
    if (commentRow) {
      comment += textRow;
      continue;
    }

    if (textRow === '') {
      if (rows.length > 0) {
        commentRow = true;
      }
      continue;
    }
    var textCols = textRow.split('\t');
    var cols = [];
    var cols2 = [];
    for (var j = 0; j < textCols.length; j++) {
      var col = textCols[j];
      if (i === 0) {
        columnNames.push({name: col, index: j, aggr: 'sum'});
      } else {
        var parsedCol = this.parseTableCell(col);
        cols.push(parsedCol);
        cols2.push({key: (columnNames[i]) ? columnNames[i].name : undefined, value: parsedCol});
      }
    }
    if (i !== 0) {
      rows.push(cols);
      array.push(cols2);
    }
  }
  this.comment = comment;
  this.columns = columnNames;
  this.rows = rows;
};

zeppelin.TableData.prototype.parseTableCell = function(cell) {
  if (!isNaN(cell)) {
    if (cell.length === 0 || Number(cell) > Number.MAX_SAFE_INTEGER || Number(cell) < Number.MIN_SAFE_INTEGER) {
      return cell;
    } else {
      return Number(cell);
    }
  }
  var d = moment(cell);
  if (d.isValid()) {
    return d;
  }
  return cell;
};

zeppelin.TableData.prototype.pivot = function(keys, groups, values, allowTextXAxis, fillMissingValues, chartType) {
  var aggrFunc = {
    sum: function(a, b) {
      var varA = (a !== undefined) ? (isNaN(a) ? 1 : parseFloat(a)) : 0;
      var varB = (b !== undefined) ? (isNaN(b) ? 1 : parseFloat(b)) : 0;
      return varA + varB;
    },
    count: function(a, b) {
      var varA = (a !== undefined) ? parseInt(a) : 0;
      var varB = (b !== undefined) ? 1 : 0;
      return varA + varB;
    },
    min: function(a, b) {
      var varA = (a !== undefined) ? (isNaN(a) ? 1 : parseFloat(a)) : 0;
      var varB = (b !== undefined) ? (isNaN(b) ? 1 : parseFloat(b)) : 0;
      return Math.min(varA,varB);
    },
    max: function(a, b) {
      var varA = (a !== undefined) ? (isNaN(a) ? 1 : parseFloat(a)) : 0;
      var varB = (b !== undefined) ? (isNaN(b) ? 1 : parseFloat(b)) : 0;
      return Math.max(varA,varB);
    },
    avg: function(a, b, c) {
      var varA = (a !== undefined) ? (isNaN(a) ? 1 : parseFloat(a)) : 0;
      var varB = (b !== undefined) ? (isNaN(b) ? 1 : parseFloat(b)) : 0;
      return varA + varB;
    }
  };

  var aggrFuncDiv = {
    sum: false,
    count: false,
    min: false,
    max: false,
    avg: true
  };

  var schema = {};
  var rows = {};

  for (var i = 0; i < this.rows.length; i++) {
    var row = this.rows[i];
    var s = schema;
    var p = rows;

    for (var k = 0; k < keys.length; k++) {
      var key = keys[k];

      // add key to schema
      if (!s[key.name]) {
        s[key.name] = {
          order: k,
          index: key.index,
          type: 'key',
          children: {}
        };
      }
      s = s[key.name].children;

      // add key to row
      var keyKey = row[key.index];
      if (!p[keyKey]) {
        p[keyKey] = {};
      }
      p = p[keyKey];
    }

    for (var g = 0; g < groups.length; g++) {
      var group = groups[g];
      var groupKey = row[group.index];

      // add group to schema
      if (!s[groupKey]) {
        s[groupKey] = {
          order: g,
          index: group.index,
          type: 'group',
          children: {}
        };
      }
      s = s[groupKey].children;

      // add key to row
      if (!p[groupKey]) {
        p[groupKey] = {};
      }
      p = p[groupKey];
    }

    for (var v = 0; v < values.length; v++) {
      var value = values[v];
      var valueKey = value.name + '(' + value.aggr + ')';

      // add value to schema
      if (!s[valueKey]) {
        s[valueKey] = {
          type: 'value',
          order: v,
          index: value.index
        };
      }

      // add value to row
      if (!p[valueKey]) {
        p[valueKey] = {
          value: (value.aggr !== 'count') ? row[value.index] : 1,
          count: 1
        };
      } else {
        p[valueKey] = {
          value: aggrFunc[value.aggr](p[valueKey].value, row[value.index], p[valueKey].count + 1),
          count: (aggrFuncDiv[value.aggr]) ?  p[valueKey].count + 1 : p[valueKey].count
        };
      }
    }
  }

  console.log("schema=%o, rows=%o", schema, rows);
  return {
    schema: schema,
    rows: rows
  }
};

zeppelin.TableData.prototype._buildTableDataFromPivot = function(schema, rows, keys, groups, values, values, allowTextXAxis, fillMissingValues, chartType) {
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
  var isMultiBarChart = (chartType === 'multiBarChart');

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
