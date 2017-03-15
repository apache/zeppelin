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

const lo = _; /** provided by bower */

import {
  getCurrentChartAxis,
} from './advanced-transformation-api'

export const Aggregator = {
  SUM: 'sum',
  COUNT: 'count',
  AVG: 'avg',
  MIN: 'min',
  MAX: 'max',
}

export function isAggregator(axisSpec) {
  return axisSpec && axisSpec.axisType === 'aggregator';
}
export function isGroup(axisSpec) {
  return axisSpec && axisSpec.axisType === 'group';
}
export function isKey(axisSpec) {
  return axisSpec && axisSpec.axisType === 'key';
}
export function isSingleDimension(axisSpec) {
  return axisSpec && axisSpec.dimension === 'single';
}

/**
 * before: { name: { ... } }
 * after: [ { name, ... } ]
 *
 * add the `name` field while converting to array to easily manipulate
 */
export function getSpecs(specObject) {
  const specs = [];
  for (let name in specObject) {
    const singleSpec = specObject[name];
    singleSpec.name = name;
    specs.push(singleSpec);
  }

  return specs
}

export function getAvailableChartNames(charts) {
  const available = []
  for (var name in charts) {
    available.push(name)
  }

  return available
}

export function removeDuplicatedColumnsInMultiDimensionAxis(config, axisSpec) {
  if (isSingleDimension(axisSpec)) { return config; }

  const columns = getCurrentChartAxis(config)[axisSpec.name]
  const uniqObject = columns.reduce((acc, col) => {
    if (!acc[col.name]) { acc[col.name] = col; }
    return acc
  }, {});

  const filtered = [] ;
  for (let name in uniqObject) {
    const col = uniqObject[name];
    filtered.push(col)
  }

  getCurrentChartAxis(config)[axisSpec.name] = filtered
  return config
}

export function clearConfig(config) {
  delete config.chart;      /** Object: contains current, available chart */
  delete config.panel;      /** Object: persisted config values for panel */
  delete config.spec;       /** Object: axis, parameter spec for each chart */

  delete config.axis;       /** Object: persisted axis for each chart */
  delete config.parameter;  /** Object: persisted parameter for each chart */
  delete config.axisSpecs;  /** Object: persisted axisSpecs for each chart */
  delete config.paramSpecs; /** Object: persisted paramSpecs for each chart */
}

export function initializeConfig(config, spec) {
  if (!config.spec || config.spec.version !== spec.version) {
    clearConfig(config)
  }

  const availableCharts = getAvailableChartNames(spec.charts);

  if (!config.spec) { config.spec = spec; }

  if (!config.chart) {
    config.chart = {};
    config.chart.current = availableCharts[0];
    config.chart.available = availableCharts;
  }

  /** initialize config.axis, config.axisSpecs for each chart */
  if (!config.axis) { config.axis = {}; }
  if (!config.axisSpecs) { config.axisSpecs = {}; }
  for (let i = 0; i < availableCharts.length; i++) {
    const chartName = availableCharts[i];

    if (!config.axis[chartName]) { config.axis[chartName] = {}; }
    const axisSpecs = getSpecs(spec.charts[chartName].axis)
    if (!config.axisSpecs[chartName]) { config.axisSpecs[chartName] = axisSpecs; }

    for (let i = 0; i < axisSpecs.length; i++) {
      const axisSpec = axisSpecs[i]
      if (!isSingleDimension(axisSpec) && !Array.isArray(config.axis[chartName][axisSpec.name])) {
        config.axis[chartName][axisSpec.name] = []
      }
    }
  }

  /** initialize config.parameter for each chart */
  if (!config.parameter) { config.parameter = {}; }
  if (!config.paramSpecs) { config.paramSpecs = {}; }
  for (let i = 0; i < availableCharts.length; i++) {
    const chartName = availableCharts[i];

    if (!config.parameter[chartName]) { config.parameter[chartName] = {}; }
    const paramSpecs = getSpecs(spec.charts[chartName].parameter)
    if (!config.paramSpecs[chartName]) { config.paramSpecs[chartName] = paramSpecs; }

    for (let i = 0; i < paramSpecs.length; i++) {
      const paramSpec = paramSpecs[i];
      if (!config.parameter[chartName][paramSpec.name]) {
        config.parameter[chartName][paramSpec.name] = paramSpec.defaultValue;
      }
    }
  }

  /** initialize config.panel */
  if (!config.panel) {
    config.panel = {
      columnPanelOpened: true,
      parameterPanelOpened: true,
    };
  }

  return config
}


export function getColumnsFromAxis(axisSpecs, axis) {
  const keyAxisNames = [];
  const groupAxisNames = [];
  const aggrAxisNames = [];

  for(let i = 0; i < axisSpecs.length; i++) {
    const axisSpec = axisSpecs[i];

    if (isKey(axisSpec)) { keyAxisNames.push(axisSpec.name); }
    else if (isGroup(axisSpec)) { groupAxisNames.push(axisSpec.name); }
    else if (isAggregator(axisSpec)) { aggrAxisNames.push(axisSpec.name); }
  }

  let keyColumns = [];
  let groupColumns = [];
  let aggregatorColumns = [];
  let otherColumns = [];

  for(let axisName in axis) {
    const columns = axis[axisName];
    if (keyAxisNames.includes(axisName)) {
      keyColumns = keyColumns.concat(columns);
    } else if (groupAxisNames.includes(axisName)) {
      groupColumns = groupColumns.concat(columns);
    } else if (aggrAxisNames.includes(axisName)) {
      aggregatorColumns = aggregatorColumns.concat(columns);
    } else {
      otherColumns = otherColumns.concat(columns);
    }
  }

  return {
    key: keyColumns,
    group: groupColumns,
    aggregator: aggregatorColumns,
    others: otherColumns,
  }
}

const AggregatorFunctions = {
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

var AggregatorFunctionDiv = {
  sum: false,
  count: false,
  min: false,
  max: false,
  avg: true
};

export function getCubeWithSchema(rows, keyColumns, groupColumns, aggrColumns) {

  const schema = {
    key: keyColumns.length !== 0,
    keyColumns: keyColumns,
    group: groupColumns.length !== 0,
    groupColumns: groupColumns,
    aggregator: aggrColumns.length !== 0,
    aggregatorColumns: aggrColumns,
  };

  const cube = {};
  const entry = {};

  for (let i = 0; i < rows.length; i++) {
    const row = rows[i];
    let e = entry;
    let c = cube;

    // key: add to entry
    if (keyColumns.length > 0) {
      const mergedKeyName = keyColumns.map(c => row[c.index]).join('.')
      if (!e[mergedKeyName]) { e[mergedKeyName] = { children: {}, }; }
      e = e[mergedKeyName].children;
      // key: add to row
      if (!c[mergedKeyName]) { c[mergedKeyName] = {}; }
      c = c[mergedKeyName];
    }

    // group: add to entry
    if (groupColumns.length > 0) {
      const mergedGroupName = groupColumns.map(c => row[c.index]).join('.')
      if (!e[mergedGroupName]) { e[mergedGroupName] = { children: {}, }; }
      e = e[mergedGroupName].children;
      // group: add to row
      if (!c[mergedGroupName]) { c[mergedGroupName] = {}; }
      c = c[mergedGroupName];
    }

    for (let a = 0; a < aggrColumns.length; a++) {
      const aggrColumn = aggrColumns[a];
      const aggrName = aggrColumn.name;

      // add aggregator to entry
      if (!e[aggrName]) {
        e[aggrName] = { type: 'aggregator', order: aggrColumn, index: aggrColumn.index, };
      }

      // add aggregatorName to row
      if (!c[aggrName]) {
        c[aggrName] = {
          aggr: aggrColumn.aggr,
          value: (aggrColumn.aggr !== 'count') ? row[aggrColumn.index] : 1,
          count: 1,
        };
      } else {
        const value = AggregatorFunctions[aggrColumn.aggr](
          c[aggrName].value, row[aggrColumn.index], c[aggrName].count + 1);
        const count = (AggregatorFunctionDiv[aggrColumn.aggr]) ?
          c[aggrName].count + 1 : c[aggrName].count;

        c[aggrName].value = value;
        c[aggrName].count = count;
      }
    }
  }

  return { cube: cube, schema: schema, };
}

