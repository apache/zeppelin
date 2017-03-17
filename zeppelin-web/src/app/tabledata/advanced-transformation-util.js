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

export function getCurrentChart(config) {
  return config.chart.current;
}

export function getCurrentChartTransform(config) {
  return config.spec.charts[getCurrentChart(config)].transform
}

export function getCurrentChartAxis(config) {
  return config.axis[getCurrentChart(config)]
}

export function getCurrentChartParam(config) {
  return config.parameter[getCurrentChart(config)]
}

export function getCurrentChartAxisSpecs(config) {
  return config.axisSpecs[getCurrentChart(config)]
}

export function getCurrentChartParamSpecs(config) {
  return config.paramSpecs[getCurrentChart(config)]
}

export const Widget = {
  INPUT: 'input', /** default */
  OPTION: 'option',
  CHECKBOX: 'checkbox',
  TEXTAREA: 'textarea',
}

export function isInputWidget(paramSpec) {
  return (paramSpec && !paramSpec.widget) || (paramSpec && paramSpec.widget === Widget.INPUT);
}

export function isOptionWidget(paramSpec) {
  return paramSpec && paramSpec.widget === Widget.OPTION;
}

export function isCheckboxWidget(paramSpec) {
  return paramSpec && paramSpec.widget === Widget.CHECKBOX;
}

export function isTextareaWidget(paramSpec) {
  return paramSpec && paramSpec.widget === Widget.TEXTAREA;
}

export const ParameterValueType = {
  INT: 'int',
  FLOAT: 'float',
  BOOLEAN: 'boolean',
  STRING: 'string',
  JSON: 'JSON',
}

export function parseParameter(paramSpecs, param) {
  /** copy original params */
  const parsed = JSON.parse(JSON.stringify(param))

  for (let i = 0 ; i < paramSpecs.length; i++) {
    const paramSpec = paramSpecs[i]
    const name = paramSpec.name

    if (paramSpec.valueType === ParameterValueType.INT &&
      typeof parsed[name] !== 'number') {

      try { parsed[name] = parseInt(parsed[name]); }
      catch (error) { parsed[name] = paramSpec.defaultValue; }
    }
    else if (paramSpec.valueType === ParameterValueType.FLOAT &&
      typeof parsed[name] !== 'number') {

      try { parsed[name] = parseFloat(parsed[name]); }
      catch (error) { parsed[name] = paramSpec.defaultValue; }
    }
    else if (paramSpec.valueType === ParameterValueType.BOOLEAN) {
      if (parsed[name] === 'false') {
        parsed[name] = false;
      } else if (parsed[name] === 'true') {
        parsed[name] = false;
      } else if (typeof parsed[name] !== 'boolean') {
        parsed[name] = paramSpec.defaultValue;
      }
    }
    else if (paramSpec.valueType === ParameterValueType.JSON) {
      if (parsed[name] !== null && typeof parsed[name] !== 'object') {
        try { parsed[name] = JSON.parse(parsed[name]); }
        catch (error) { parsed[name] = paramSpec.defaultValue; }
      } else if (parsed[name] === null) {
        parsed[name] = paramSpec.defaultValue;
      }
    }
  }

  return parsed
}


export const Aggregator = {
  SUM: 'sum',
  COUNT: 'count',
  AVG: 'avg',
  MIN: 'min',
  MAX: 'max',
}

export function isAggregatorAxis(axisSpec) {
  return axisSpec && axisSpec.axisType === 'aggregator';
}
export function isGroupAxis(axisSpec) {
  return axisSpec && axisSpec.axisType === 'group';
}
export function isKeyAxis(axisSpec) {
  return axisSpec && axisSpec.axisType === 'key';
}
export function isSingleDimensionAxis(axisSpec) {
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

export function applyMaxAxisCount(config, axisSpec) {
  if (isSingleDimensionAxis(axisSpec) || typeof axisSpec.maxAxisCount === 'undefined') {
    return;
  }

  const columns = getCurrentChartAxis(config)[axisSpec.name]
  if (columns.length <= axisSpec.maxAxisCount) { return; }

  const sliced = columns.slice(1)
  getCurrentChartAxis(config)[axisSpec.name] = sliced
}

export function removeDuplicatedColumnsInMultiDimensionAxis(config, axisSpec) {
  if (isSingleDimensionAxis(axisSpec)) { return config }

  const columns = getCurrentChartAxis(config)[axisSpec.name]
  const uniqObject = columns.reduce((acc, col) => {
    if (!acc[col.name]) { acc[col.name] = col }
    return acc
  }, {})

  const filtered = [] ;
  for (let name in uniqObject) {
    const col = uniqObject[name]
    filtered.push(col)
  }

  getCurrentChartAxis(config)[axisSpec.name] = filtered
  return config
}

export function clearChartConfig(config) {
  delete config.axis       /** Object: persisted axis for each chart */
  config.axis = {}

  const spec = config.spec
  const availableCharts = getAvailableChartNames(spec.charts)
  config.chart.current = availableCharts[0];

  if (!config.axisSpecs) { config.axisSpecs = {}; }
  for (let i = 0; i < availableCharts.length; i++) {
    const chartName = availableCharts[i];

    if (!config.axis[chartName]) { config.axis[chartName] = {}; }
    const axisSpecs = getSpecs(spec.charts[chartName].axis)
    if (!config.axisSpecs[chartName]) { config.axisSpecs[chartName] = axisSpecs; }

    for (let i = 0; i < axisSpecs.length; i++) {
      const axisSpec = axisSpecs[i]
      if (!isSingleDimensionAxis(axisSpec) &&
        !Array.isArray(config.axis[chartName][axisSpec.name])) {
        config.axis[chartName][axisSpec.name] = []
      }
    }
  }
}

export function clearParameterConfig(config) {
  delete config.parameter  /** Object: persisted parameter for each chart */
  config.parameter = {}

  const spec = config.spec
  const availableCharts = getAvailableChartNames(spec.charts)

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
}

export function initializeConfig(config, spec) {
  const currentVersion = JSON.stringify(spec)
  if (!config.spec || !config.spec.version || config.spec.version !== currentVersion) {
    spec.version = currentVersion
    delete config.chart      /** Object: contains current, available chart */
    delete config.spec       /** Object: axis, parameter spec for each chart */
    config.panel = { columnPanelOpened: true, parameterPanelOpened: false, }

    delete config.axisSpecs  /** Object: persisted axisSpecs for each chart */
    delete config.paramSpecs /** Object: persisted paramSpecs for each chart */
  }

  const availableCharts = getAvailableChartNames(spec.charts)

  if (!config.spec) { config.spec = spec; }

  if (!config.chart) {
    config.chart = {};
    config.chart.current = availableCharts[0];
    config.chart.available = availableCharts;
  }

  /** initialize config.axis, config.axisSpecs for each chart */
  clearChartConfig(config)

  /** initialize config.parameter for each chart */
  clearParameterConfig(config)
  return config
}


export function getColumnsFromAxis(axisSpecs, axis) {
  const keyAxisNames = [];
  const groupAxisNames = [];
  const aggrAxisNames = [];

  for(let i = 0; i < axisSpecs.length; i++) {
    const axisSpec = axisSpecs[i];

    if (isKeyAxis(axisSpec)) { keyAxisNames.push(axisSpec.name); }
    else if (isGroupAxis(axisSpec)) { groupAxisNames.push(axisSpec.name); }
    else if (isAggregatorAxis(axisSpec)) { aggrAxisNames.push(axisSpec.name); }
  }

  let keyColumns = [];
  let groupColumns = [];
  let aggregatorColumns = [];
  let customColumn = {};

  for(let axisName in axis) {
    const columns = axis[axisName];
    if (keyAxisNames.includes(axisName)) {
      keyColumns = keyColumns.concat(columns);
    } else if (groupAxisNames.includes(axisName)) {
      groupColumns = groupColumns.concat(columns);
    } else if (aggrAxisNames.includes(axisName)) {
      aggregatorColumns = aggregatorColumns.concat(columns);
    } else {
      const axisType = axisSpecs.filter(s => s.name === axisName)[0].axisType
      if (!customColumn[axisType]) { customColumn[axisType] = []; }
      customColumn[axisType] = customColumn[axisType].concat(columns);
    }
  }

  return {
    key: keyColumns,
    group: groupColumns,
    aggregator: aggregatorColumns,
    custom: customColumn,
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


export function getNames(obj) {
  const names = []
  for (let name in obj) {
    names.push(name)
  }

  return names
}

export function getFlattenRow(schema, obj, groupNameSet) {
  const aggrColumns = schema.aggregatorColumns
  const row = {}

  /** when group is empty */
  if (!schema.group) {
    for(let i = 0; i < aggrColumns.length; i++) {
      const aggrColumn = aggrColumns[i]

      row[aggrColumn.name] = obj[aggrColumn.name].value
      groupNameSet.add(aggrColumn.name)
    }

    return row
  }

  /** when group is specified */
  for(let i = 0; i < aggrColumns.length; i++) {
    const aggrColumn = aggrColumns[i]

    for (let groupName in obj) {
      const selector = `${groupName}.${aggrColumn.name}`
      groupNameSet.add(selector)
      const grouped = obj[groupName]
      row[selector] = grouped[aggrColumn.name].value
    }
  }

  return row
}

export function getFlattenCube(cube, schema) {
  let keys = getNames(cube)
  const keyColumnName = schema.keyColumns.map(c => c.name).join('.')

  if (!schema.key) {
    keys = [ 'root', ]
    cube = { root: cube, }
  }

  const groupNameSet = new Set()
  const rows = keys.reduce((acc, key) => {
    const keyed = cube[key]
    const row = getFlattenRow(schema, keyed, groupNameSet)
    if (schema.key) { row[keyColumnName] = key }
    acc.push(row)

    return acc
  }, [])

  return { rows: rows, keyColumnName: keyColumnName, groupNameSet: groupNameSet, }
}

/** return function for lazy computation */
export function getTransformer(conf, rows, keyColumns, groupColumns, aggregatorColumns) {
  let transformer = () => {
    /** default is flatten cube */
    const { cube, schema, } = getCubeWithSchema(rows, keyColumns, groupColumns, aggregatorColumns);
    return getFlattenCube(cube, schema)
  }

  const transformSpec = getCurrentChartTransform(conf)
  if (!transformSpec) { return transformer; }

  if (transformSpec.method === 'raw') {
    transformer = () => { return rows; }
  } else if (transformSpec.method === 'cube') {
    transformer = () => {
      const { cube, } = getCubeWithSchema(rows, keyColumns, groupColumns, aggregatorColumns);
      return cube
    }
  }

  return transformer
}
