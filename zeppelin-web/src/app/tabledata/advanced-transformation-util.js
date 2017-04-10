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

export function useSharedAxis(config, chart) {
  return config.spec.charts[chart].sharedAxis
}

export function serializeSharedAxes(config) {
  const availableCharts = getAvailableChartNames(config.spec.charts)
  for (let i = 0; i < availableCharts.length; i++) {
    const chartName = availableCharts[i];
    if (useSharedAxis(config, chartName)) {
      /** use reference :) in case of sharedAxis */
      config.axis[chartName] = config.sharedAxis
    }
  }
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
        parsed[name] = true;
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
    if (!singleSpec) { continue }
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
    if (!acc[`${col.name}(${col.aggr})`]) { acc[`${col.name}(${col.aggr})`] = col }
    return acc
  }, {})

  const filtered = []
  for (let name in uniqObject) {
    const col = uniqObject[name]
    filtered.push(col)
  }

  getCurrentChartAxis(config)[axisSpec.name] = filtered
  return config
}

export function clearAxisConfig(config) {
  delete config.axis /** Object: persisted axis for each chart */
  delete config.sharedAxis
}

export function initAxisConfig(config) {
  if (!config.axis) { config.axis = {} }
  if (!config.sharedAxis) { config.sharedAxis = {} }

  const spec = config.spec
  const availableCharts = getAvailableChartNames(spec.charts)

  if (!config.axisSpecs) { config.axisSpecs = {}; }
  for (let i = 0; i < availableCharts.length; i++) {
    const chartName = availableCharts[i];

    if (!config.axis[chartName]) {
      config.axis[chartName] = {};
    }
    const axisSpecs = getSpecs(spec.charts[chartName].axis)
    if (!config.axisSpecs[chartName]) {
      config.axisSpecs[chartName] = axisSpecs;
    }

    /** initialize multi-dimension axes */
    for (let i = 0; i < axisSpecs.length; i++) {
      const axisSpec = axisSpecs[i]
      if (isSingleDimensionAxis(axisSpec)) {
        continue;
      }

      /** intentionally nested if-stmt is used because order of conditions matter here */
      if (!useSharedAxis(config, chartName)) {
        if (!Array.isArray(config.axis[chartName][axisSpec.name])) {
          config.axis[chartName][axisSpec.name] = []
        }
      } else if (useSharedAxis(config, chartName)) {
        /**
         * initialize multiple times even if shared axis because it's not that expensive, assuming that
         * all charts using shared axis have the same axis specs
         */
        if (!Array.isArray(config.sharedAxis[axisSpec.name])) {
          config.sharedAxis[axisSpec.name] = []
        }
      }
    }
  }

  /** this function should be called after initializing */
  serializeSharedAxes(config)
}

export function resetAxisConfig(config) {
  clearAxisConfig(config)
  initAxisConfig(config)
}

export function clearParameterConfig(config) {
  delete config.parameter  /** Object: persisted parameter for each chart */
}

export function initParameterConfig(config) {
  if (!config.parameter) { config.parameter = {} }

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

export function resetParameterConfig(config) {
  clearParameterConfig(config)
  initParameterConfig(config)
}

export function initializeConfig(config, spec) {
  config.chartChanged = true
  config.parameterChanged = false

  const currentVersion = JSON.stringify(spec)
  if (!config.spec || !config.spec.version || config.spec.version !== currentVersion) {
    spec.version = currentVersion
    spec.initialized = true
    delete config.chart      /** Object: contains current, available chart */
    delete config.spec       /** Object: axis, parameter spec for each chart */
    config.panel = { columnPanelOpened: true, parameterPanelOpened: false, }
    clearAxisConfig(config)
    clearParameterConfig(config)

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
  initAxisConfig(config)

  /** initialize config.parameter for each chart */
  initParameterConfig(config)
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

export const Aggregator = {
  SUM: 'sum',
  COUNT: 'count',
  AVG: 'avg',
  MIN: 'min',
  MAX: 'max',
}

const TransformMethod = {
  /**
   * `raw` is designed for users who want to get raw (original) rows.
   */
  RAW: 'raw',
  /**
   * `object` is * designed for serial(line, area) charts.
   */
  OBJECT: 'object',
  /**
   * `array` is designed for column, pie charts which have categorical `key` values.
   * But some libraries (e.g amcharts) may require `OBJECT` transform method even if pie, column charts.
   *
   * `row.value` will be filled for `keyNames`.
   * In other words, if you have `keyNames` which is length 4,
   * every `row.value`'s length will be 4 too.
   * (DO NOT use this transform method for serial (numerical) x axis charts which have so-oo many keys)
   */
  ARRAY: 'array',
  DRILL_DOWN: 'drill-down',
}

/** return function for lazy computation */
export function getTransformer(conf, rows, keyColumns, groupColumns, aggregatorColumns) {
  let transformer = () => {}

  const transformSpec = getCurrentChartTransform(conf)
  if (!transformSpec) { return transformer }

  const method = transformSpec.method

  if (method === TransformMethod.RAW) {
    transformer = () => { return rows; }
  } else if (method === TransformMethod.OBJECT) {
    transformer = () => {
      const { cube, schema, keyColumnName,  keyNames, groupNameSet, selectorNameWithIndex, } =
        getKGACube(rows, keyColumns, groupColumns, aggregatorColumns)

      const {
        transformed, groupNames, sortedSelectors
      } = getObjectRowsFromKGACube(cube, schema, aggregatorColumns,
        keyColumnName, keyNames, groupNameSet, selectorNameWithIndex)

      return {
        rows: transformed, keyColumnName, keyNames,
        groupNames: groupNames,
        selectors: sortedSelectors,
      }
    }
  } else if (method === TransformMethod.ARRAY) {
    transformer = () => {
      const { cube, schema, keyColumnName, keyNames, groupNameSet, selectorNameWithIndex, } =
        getKGACube(rows, keyColumns, groupColumns, aggregatorColumns)

      const {
        transformed, groupNames, sortedSelectors,
      } = getArrayRowsFromKGACube(cube, schema, aggregatorColumns,
        keyColumnName, keyNames, groupNameSet, selectorNameWithIndex)

      return {
        rows: transformed, keyColumnName, keyNames,
        groupNames: groupNames,
        selectors: sortedSelectors,
      }
    }
  } else if (method === TransformMethod.DRILL_DOWN) {
    transformer = () => {
      const { cube, schema, keyColumnName, keyNames, groupNameSet, selectorNameWithIndex, } =
        getKAGCube(rows, keyColumns, groupColumns, aggregatorColumns)

      const {
        transformed, groupNames, sortedSelectors,
      } = getDrilldownRowsFromKAGCube(cube, schema, aggregatorColumns,
        keyColumnName, keyNames, groupNameSet, selectorNameWithIndex)

      return {
        rows: transformed, keyColumnName, keyNames,
        groupNames: groupNames,
        selectors: sortedSelectors,
      }
    }
  }

  return transformer
}
const AggregatorFunctions = {
  sum: function(a, b) {
    const varA = (a !== undefined) ? (isNaN(a) ? 1 : parseFloat(a)) : 0;
    const varB = (b !== undefined) ? (isNaN(b) ? 1 : parseFloat(b)) : 0;
    return varA + varB;
  },
  count: function(a, b) {
    const varA = (a !== undefined) ? parseInt(a) : 0;
    const varB = (b !== undefined) ? 1 : 0;
    return varA + varB;
  },
  min: function(a, b) {
    const varA = (a !== undefined) ? (isNaN(a) ? 1 : parseFloat(a)) : 0;
    const varB = (b !== undefined) ? (isNaN(b) ? 1 : parseFloat(b)) : 0;
    return Math.min(varA,varB);
  },
  max: function(a, b) {
    const varA = (a !== undefined) ? (isNaN(a) ? 1 : parseFloat(a)) : 0;
    const varB = (b !== undefined) ? (isNaN(b) ? 1 : parseFloat(b)) : 0;
    return Math.max(varA,varB);
  },
  avg: function(a, b, c) {
    const varA = (a !== undefined) ? (isNaN(a) ? 1 : parseFloat(a)) : 0;
    const varB = (b !== undefined) ? (isNaN(b) ? 1 : parseFloat(b)) : 0;
    return varA + varB;
  }
};

const AggregatorFunctionDiv = {
  sum: false,
  min: false,
  max: false,
  count: false,
  avg: true
};

/** nested cube `(key) -> (group) -> aggregator` */
export function getKGACube(rows, keyColumns, groupColumns, aggrColumns) {
  const schema = {
    key: keyColumns.length !== 0,
    group: groupColumns.length !== 0,
    aggregator: aggrColumns.length !== 0,
  };

  let cube = {}
  const entry = {}

  const keyColumnName = keyColumns.map(c => c.name).join('.')
  const groupNameSet = new Set()
  const keyNameSet = new Set()
  const selectorNameWithIndex = {} /** { selectorName: index } */
  let indexCounter = 0

  for (let i = 0; i < rows.length; i++) {
    const row = rows[i];
    let e = entry;
    let c = cube;

    // key: add to entry
    let mergedKeyName = undefined
    if (schema.key) {
      mergedKeyName = keyColumns.map(c => row[c.index]).join('.')
      if (!e[mergedKeyName]) { e[mergedKeyName] = { children: {}, } }
      e = e[mergedKeyName].children
      // key: add to row
      if (!c[mergedKeyName]) { c[mergedKeyName] = {} }
      c = c[mergedKeyName]

      keyNameSet.add(mergedKeyName)
    }

    let mergedGroupName = undefined
    if (schema.group) {
      mergedGroupName = groupColumns.map(c => row[c.index]).join('.')

      // add group to entry
      if (!e[mergedGroupName]) { e[mergedGroupName] = { children: {}, } }
      e = e[mergedGroupName].children
      // add group to row
      if (!c[mergedGroupName]) { c[mergedGroupName] = {} }
      c = c[mergedGroupName]
      groupNameSet.add(mergedGroupName)
    }

    for (let a = 0; a < aggrColumns.length; a++) {
      const aggrColumn = aggrColumns[a]
      const aggrName = `${aggrColumn.name}(${aggrColumn.aggr})`

      // update groupNameSet
      if (!mergedGroupName) {
        groupNameSet.add(aggrName) /** aggr column name will be used as group name if group is empty */
      }

      // update selectorNameWithIndex
      const selector = getSelectorName(mergedGroupName, aggrColumns.length, aggrName)
      if (typeof selectorNameWithIndex[selector] === 'undefined' /** value might be 0 */) {
        selectorNameWithIndex[selector] = indexCounter
        indexCounter = indexCounter + 1
      }

      // add aggregator to entry
      if (!e[aggrName]) {
        e[aggrName] = { type: 'aggregator', order: aggrColumn, index: aggrColumn.index, }
      }

      // add aggregatorName to row
      if (!c[aggrName]) {
        c[aggrName] = {
          aggr: aggrColumn.aggr,
          value: (aggrColumn.aggr !== 'count') ? row[aggrColumn.index] : 1,
          count: 1,
        }
      } else {
        const value = AggregatorFunctions[aggrColumn.aggr](
          c[aggrName].value, row[aggrColumn.index], c[aggrName].count + 1)
        const count = (AggregatorFunctionDiv[aggrColumn.aggr]) ?
          c[aggrName].count + 1 : c[aggrName].count

        c[aggrName].value = value
        c[aggrName].count = count
      }

    } /** end loop for aggrColumns */
  }

  let keyNames = null
  if (!schema.key) {
    const mergedGroupColumnName = groupColumns.map(c => c.name).join('.')
    cube = { [mergedGroupColumnName]: cube, }
    keyNames = [ mergedGroupColumnName, ]
  } else {
    keyNames = Object.keys(cube).sort() /** keys should be sorted */
  }

  return {
    cube: cube,
    schema: schema,
    keyColumnName: keyColumnName,
    keyNames: keyNames,
    groupNameSet: groupNameSet,
    selectorNameWithIndex: selectorNameWithIndex,
  }
}

/** nested cube `(key) -> aggregator -> (group)` for drill-down support */
export function getKAGCube(rows, keyColumns, groupColumns, aggrColumns) {
  const schema = {
    key: keyColumns.length !== 0,
    group: groupColumns.length !== 0,
    aggregator: aggrColumns.length !== 0,
  };

  let cube = {}

  const keyColumnName = keyColumns.map(c => c.name).join('.')
  const groupNameSet = new Set()
  const keyNameSet = new Set()
  const selectorNameWithIndex = {} /** { selectorName: index } */
  let indexCounter = 0

  for (let i = 0; i < rows.length; i++) {
    const row = rows[i];
    let c = cube;

    // key: add to entry
    let mergedKeyName = undefined
    if (schema.key) {
      mergedKeyName = keyColumns.map(c => row[c.index]).join('.')
      // key: add to row
      if (!c[mergedKeyName]) { c[mergedKeyName] = {} }
      c = c[mergedKeyName]

      keyNameSet.add(mergedKeyName)
    }

    let mergedGroupName = undefined
    if (schema.group) {
      mergedGroupName = groupColumns.map(c => row[c.index]).join('.')
      groupNameSet.add(mergedGroupName)
    }

    for (let a = 0; a < aggrColumns.length; a++) {
      const aggrColumn = aggrColumns[a]
      const aggrName = `${aggrColumn.name}(${aggrColumn.aggr})`

      // update groupNameSet
      if (!mergedGroupName) {
        groupNameSet.add(aggrName) /** aggr column name will be used as group name if group is empty */
      }

      // update selectorNameWithIndex
      const selector = getSelectorName(mergedKeyName, aggrColumns.length, aggrName)
      if (typeof selectorNameWithIndex[selector] === 'undefined' /** value might be 0 */) {
        selectorNameWithIndex[selector] = indexCounter
        indexCounter = indexCounter + 1
      }

      // add aggregatorName to row
      if (!c[aggrName]) {
        const value = (aggrColumn.aggr !== 'count') ? row[aggrColumn.index] : 1
        const count = 1

        c[aggrName] = { aggr: aggrColumn.aggr, value: value, count: count, children: {}, }
      } else {
        const value = AggregatorFunctions[aggrColumn.aggr](
          c[aggrName].value, row[aggrColumn.index], c[aggrName].count + 1)
        const count = (AggregatorFunctionDiv[aggrColumn.aggr]) ?
          c[aggrName].count + 1 : c[aggrName].count

        c[aggrName].value = value
        c[aggrName].count = count
      }

      // add aggregated group (for drill-down) to row iff group is enabled
      if (mergedGroupName) {
        if (!c[aggrName].children[mergedGroupName]) {
          const value = (aggrColumn.aggr !== 'count') ? row[aggrColumn.index] : 1
          const count = 1

          c[aggrName].children[mergedGroupName] = { value: value, count: count, }
        } else {
          const drillDownedValue = c[aggrName].children[mergedGroupName].value
          const drillDownedCount = c[aggrName].children[mergedGroupName].count
          const value = AggregatorFunctions[aggrColumn.aggr](
            drillDownedValue, row[aggrColumn.index], drillDownedCount + 1)
          const count = (AggregatorFunctionDiv[aggrColumn.aggr]) ?
            drillDownedCount + 1 : drillDownedCount

          c[aggrName].children[mergedGroupName].value = value
          c[aggrName].children[mergedGroupName].count = count
        }

      }

    } /** end loop for aggrColumns */
  }

  let keyNames = null
  if (!schema.key) {
    const mergedGroupColumnName = groupColumns.map(c => c.name).join('.')
    cube = { [mergedGroupColumnName]: cube, }
    keyNames = [ mergedGroupColumnName, ]
  } else {
    keyNames = Object.keys(cube).sort() /** keys should be sorted */
  }

  return {
    cube: cube,
    schema: schema,
    keyColumnName: keyColumnName,
    keyNames: keyNames,
    groupNameSet: groupNameSet,
    selectorNameWithIndex: selectorNameWithIndex,
  }
}

export function getSelectorName(mergedGroupName, aggrColumnLength, aggrColumnName) {
  if (!mergedGroupName) {
    return aggrColumnName
  } else {
    return (aggrColumnLength > 1) ?
      `${mergedGroupName} / ${aggrColumnName}` : mergedGroupName
  }
}

export function getCubeValue(obj, aggregator, aggrColumnName) {
  let value = null /** default is null */
  try {
    /** if AVG or COUNT, calculate it now, previously we can't because we were doing accumulation */
    if (aggregator === Aggregator.AVG) {
      value = obj[aggrColumnName].value / obj[aggrColumnName].count
    } else if (aggregator === Aggregator.COUNT) {
      value = obj[aggrColumnName].value
    } else {
      value = obj[aggrColumnName].value
    }

    if (typeof value === 'undefined') { value = null }
  } catch (error) { /** iognore */ }

  return value
}

export function getNameWithIndex(names) {
  const nameWithIndex = {}

  for (let i = 0; i < names.length; i++) {
    const name = names[i]
    nameWithIndex[name] = i
  }

  return nameWithIndex
}

export function getArrayRowsFromKGACube(cube, schema, aggregatorColumns,
                                        keyColumnName, keyNames, groupNameSet, selectorNameWithIndex) {

  const sortedSelectors = Object.keys(selectorNameWithIndex).sort()
  const sortedSelectorNameWithIndex = getNameWithIndex(sortedSelectors)

  const keyArrowRows = new Array(sortedSelectors.length)
  const keyNameWithIndex = getNameWithIndex(keyNames)

  for(let i = 0; i < keyNames.length; i++) {
    const key = keyNames[i]

    const obj = cube[key]
    fillArrayRow(schema, aggregatorColumns, obj,
      groupNameSet, sortedSelectorNameWithIndex,
      key, keyNames, keyArrowRows, keyNameWithIndex)
  }

  return {
    transformed: keyArrowRows,
    groupNames: Array.from(groupNameSet).sort(),
    sortedSelectors: sortedSelectors,
    sortedSelectorNameWithIndex: sortedSelectorNameWithIndex,
    keyNameWithIndex: keyNameWithIndex,
  }
}

/** truly mutable style func. will return nothing, just modify `keyArrayRows` */
export function fillArrayRow(schema, aggrColumns, obj,
                             groupNameSet, selectorNameWithIndex,
                             keyName, keyNames, keyArrayRows, keyNameWithIndex) {
  /** when group is empty */
  if (!schema.group) {
    for(let i = 0; i < aggrColumns.length; i++) {
      const aggrColumn = aggrColumns[i]
      const aggrName = `${aggrColumn.name}(${aggrColumn.aggr})`

      const value = getCubeValue(obj, aggrColumn.aggr, aggrName)
      const selector = getSelectorName(undefined, aggrColumns.length, aggrName)
      const selectorIndex = selectorNameWithIndex[selector]
      const keyIndex = keyNameWithIndex[keyName]

      if (typeof keyArrayRows[selectorIndex] === 'undefined') {
        keyArrayRows[selectorIndex] = {
          selector: selector, value: new Array(keyNames.length)
        }
      }
      keyArrayRows[selectorIndex].value[keyIndex] = value
    }
  } else {
    for(let i = 0; i < aggrColumns.length; i++) {
      const aggrColumn = aggrColumns[i]
      const aggrName = `${aggrColumn.name}(${aggrColumn.aggr})`

      for (let groupName of groupNameSet) {
        const grouped = obj[groupName]

        const value = getCubeValue(grouped, aggrColumn.aggr, aggrName)
        const selector = getSelectorName(groupName, aggrColumns.length, aggrName)
        const selectorIndex = selectorNameWithIndex[selector]
        const keyIndex = keyNameWithIndex[keyName]

        if (typeof keyArrayRows[selectorIndex] === 'undefined') {
          keyArrayRows[selectorIndex] = {
            selector: selector, value: new Array(keyNames.length)
          }
        }
        keyArrayRows[selectorIndex].value[keyIndex] = value
      }
    }
  }
}

export function getObjectRowsFromKGACube(cube, schema, aggregatorColumns,
                                         keyColumnName, keyNames, groupNameSet,
                                         selectorNameWithIndex) {

  const rows = keyNames.reduce((acc, key) => {
    const obj = cube[key]
    const row = getObjectRow(schema, aggregatorColumns, obj, groupNameSet)

    if (schema.key) { row[keyColumnName] = key }
    acc.push(row)

    return acc
  }, [])

  return {
    transformed: rows,
    sortedSelectors: Object.keys(selectorNameWithIndex).sort(),
    groupNames: Array.from(groupNameSet).sort(),
  }
}

export function getObjectRow(schema, aggrColumns, obj, groupNameSet) {
  const row = {}

  /** when group is empty */
  if (!schema.group) {
    for(let i = 0; i < aggrColumns.length; i++) {
      const aggrColumn = aggrColumns[i]
      const aggrName = `${aggrColumn.name}(${aggrColumn.aggr})`

      const value = getCubeValue(obj, aggrColumn.aggr, aggrName)
      const selector = getSelectorName(undefined, aggrColumns.length, aggrName)
      row[selector] = value
    }

    return row
  }

  /** when group is specified */
  for(let i = 0; i < aggrColumns.length; i++) {
    const aggrColumn = aggrColumns[i]
    const aggrName = `${aggrColumn.name}(${aggrColumn.aggr})`

    for (let groupName of groupNameSet) {
      const grouped = obj[groupName]

      if (grouped) {
        const value = getCubeValue(grouped, aggrColumn.aggr, aggrName)
        const selector = getSelectorName(groupName, aggrColumns.length, aggrName)
        row[selector] = value
      }
    }
  }

  return row
}

export function getDrilldownRowsFromKAGCube(cube, schema, aggregatorColumns,
                                            keyColumnName, keyNames, groupNameSet, selectorNameWithIndex) {

  const sortedSelectors = Object.keys(selectorNameWithIndex).sort()
  const sortedSelectorNameWithIndex = getNameWithIndex(sortedSelectors)

  const rows = new Array(sortedSelectors.length)

  const groupNames = Array.from(groupNameSet).sort()

  keyNames.map(key => {
    const obj = cube[key]
    fillDrillDownRow(schema, obj, rows, key,
      sortedSelectorNameWithIndex, aggregatorColumns, groupNames)
  })

  return {
    transformed: rows,
    groupNames: groupNames,
    sortedSelectors: sortedSelectors,
    sortedSelectorNameWithIndex: sortedSelectorNameWithIndex,
  }
}

/** truly mutable style func. will return nothing, just modify `rows` */
export function fillDrillDownRow(schema, obj, rows, key,
                                 selectorNameWithIndex, aggrColumns, groupNames) {
  /** when group is empty */
  for(let i = 0; i < aggrColumns.length; i++) {
    const row = {}
    const aggrColumn = aggrColumns[i]
    const aggrName = `${aggrColumn.name}(${aggrColumn.aggr})`

    const value = getCubeValue(obj, aggrColumn.aggr, aggrName)
    const selector = getSelectorName((schema.key) ? key : undefined, aggrColumns.length, aggrName)

    const selectorIndex = selectorNameWithIndex[selector]
    row.value = value
    row.drillDown = []
    row.selector = selector

    if (schema.group) {
      row.drillDown = []

      for(let groupName of groupNames) {
        const value = getCubeValue(obj[aggrName].children, aggrColumn.aggr, groupName)
        row.drillDown.push({ group: groupName, value: value, })
      }
    }

    rows[selectorIndex] = row
  }
}
