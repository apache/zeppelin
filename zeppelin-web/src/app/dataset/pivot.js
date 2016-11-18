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
 * pivot table data and return d3 chart data
 */
zeppelin.PivotTransformation = function(config) {
  zeppelin.Transformation.call(this, config);
};

zeppelin.PivotTransformation.prototype = Object.create(zeppelin.Transformation.prototype);

/**
 * Method will be invoked when tableData or config changes
 */
zeppelin.PivotTransformation.prototype.transform = function(tableData) {
  return this.pivot(
    tableData,
    this.config.keys,
    this.config.groups,
    this.config.values);
};

zeppelin.PivotTransformation.prototype.pivot = function(data, keys, groups, values) {
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

  for (var i = 0; i < data.rows.length; i++) {
    var row = data.rows[i];
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

  //console.log('schema=%o, rows=%o', schema, rows);
  return {
    keys: keys,
    groups: groups,
    values: values,
    schema: schema,
    rows: rows
  };
};

