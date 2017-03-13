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

export const Aggregator = {
  SUM: 'sum',
  COUNT: 'count',
  AVG: 'avg',
  MIN: 'min',
  MAX: 'max',
}

export function isAggregator(axisSpec) { return axisSpec.aggregator; }
export function isGroup(axisSpec) { return axisSpec.group; }
export function isGroupBase(axisSpec) { return axisSpec.groupBase; }
export function isSingleDimension(axisSpec) { return axisSpec.dimension === 'single'; }

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

  const columns = config.axis[config.chart.current][axisSpec.name]
  const uniqObject = columns.reduce((acc, col) => {
    if (!acc[col.name]) { acc[col.name] = col; }
    return acc
  }, {});

  const filtered = [] ;
  for (let name in uniqObject) {
    const col = uniqObject[name];
    filtered.push(col)
  }

  config.axis[config.chart.current][axisSpec.name] = filtered
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
  // if (!config.spec || config.spec.version !== spec.version) {
  //   clearConfig(config)
  // }

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


export function getGroupAndAggrColumns(axisSpecs, axisConfig) {
  const groupAxisNames = [];
  const groupBaseAxisNames = [];
  const aggrAxisNames = [];

  for(let i = 0; i < axisSpecs.length; i++) {
    const axisSpec = axisSpecs[i];

    // do not allow duplication and beware of if-else stmt order
    if (isGroupBase(axisSpec)) { groupBaseAxisNames.push(axisSpec.name); }
    else if (isGroup(axisSpec)) { groupAxisNames.push(axisSpec.name); }
    else if (isAggregator(axisSpec)) { aggrAxisNames.push(axisSpec.name); }
  }

  let groupBaseColumns = []; /** `groupBase` */
  let groupColumns = []; /** `group` */
  let aggregatorColumns = []; /** `aggregator` */
  let otherColumns = []; /** specified, but not group and aggregator */

  for(let colName in axisConfig) {
    const columns = axisConfig[colName];
    if (groupBaseAxisNames.includes(colName)) {
      groupBaseColumns = groupBaseColumns.concat(columns);
    } else if (groupAxisNames.includes(colName)) {
      groupColumns = groupColumns.concat(columns);
    } else if (aggrAxisNames.includes(colName)) {
      aggregatorColumns = aggregatorColumns.concat(columns);
    } else {
      otherColumns = otherColumns.concat(columns);
    }
  }

  return {
    groupBase: groupBaseColumns,
    group: groupColumns,
    aggregator: aggregatorColumns,
    others: otherColumns,
  }
}

export function getConfiguredColumnIndices(allColumns, configuredColumns) {

  const configuredColumnNames = configuredColumns.map(c => c.name);

  const configuredColumnIndices = allColumns.reduce((acc, c) => {
    if (configuredColumnNames.includes(c.name)) { return acc.concat(c.index) }
    else { return acc }
  }, []);

  return configuredColumnIndices;
}

export function groupAndAggregateRows(rows, groupBaseColumns, groupColumns, aggregatorColumns) {
  const groupColumnIndices = groupColumns.map(c => c.index);

  const converted = lo.chain(rows)
    .groupBy(row => {
      /** 1. group */
      let group = '';

      for (let i = 0; i < groupColumnIndices.length; i++) {
        const colIndex = groupColumnIndices[i];
        const colValue = row[colIndex];

        if (group === '') { group = colValue; }
        else { group = `${group}.${colValue}`; }
      }

      return group;
    })
    .map((groupedRows, groupKey) => {
      /** 2. aggregate */
      const aggregated = {};

      // avoid unnecessary computation
      if (!groupColumns.length || !groupedRows.length) {
        return { group: groupKey, rows: groupedRows, aggregatedValues: aggregated, }
      }

      // accumulate columnar values to compute
      const columnar = {}
      for (let i = 0; i < groupedRows.length; i++) {
        const row = groupedRows[i]

        for(let j = 0; j < aggregatorColumns.length; j++) {
          const aggrColumn = aggregatorColumns[j];
          if (!columnar[aggrColumn.name]) {
            columnar[aggrColumn.name] = { aggregator: aggrColumn.aggr, values: [], };
          }

          const colValue = row[aggrColumn.index];
          columnar[aggrColumn.name].values.push(colValue);
        }
      }

      // execute aggregator functions
      for(let aggrColName in columnar) {
        const { aggregator, values, } = columnar[aggrColName];
        let computed = null;

        try {
          switch(aggregator) {
            case Aggregator.SUM: computed = lo.sum(values); break
            case Aggregator.COUNT: computed = values.length; break
            case Aggregator.AVG: computed = lo.sum(values) / values.length; break
            case Aggregator.MIN: computed = lo.min(values); break
            case Aggregator.MAX: computed = lo.max(values); break
          }
        } catch(error) {
          console.error(`Failed to compute aggregator: ${aggregator} on the field ${aggrColName}`, error);
        }

        aggregated[aggrColName] = computed;
      }

      return { group: groupKey, rows: groupedRows, aggregatedValues: aggregated, }
    })
    .value();

  return converted;
}

