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

export function clearConfig(configInstance, axisSpecs, paramSpecs) {
  delete configInstance.panel
  delete configInstance.axis
  delete configInstance.parameter

  return initializeConfig(configInstance, axisSpecs, paramSpecs)
}

export function initializeConfig(config, axisSpecs, paramSpecs) {

  /** initialize config.axis */
  if (!config.axis) { config.axis = {}; }
  for (let i = 0; i < axisSpecs.length; i++) {
    const axisSpec = axisSpecs[i];
    const persistedConfig = config.axis[axisSpec.name];

    // behavior of jqyoui-element depends on its model (ng-model)
    // so, we have to initialize its underlying ng-model to array if it's not array
    if (!isSingleDimension(axisSpec) && !Array.isArray(persistedConfig)) {
      config.axis[axisSpec.name] = [];
    } else if (isSingleDimension(axisSpec) && Array.isArray(persistedConfig)) {
      config.axis[axisSpec.name] = {};
    }
  }

  /** initialize config.parameter*/
  if (!config.parameter) { config.parameter = {}; }
  for (let i = 0; i < paramSpecs.length; i++) {
    const paramSpec = paramSpecs[i];
    if (!config.parameter[paramSpec.name]) {
      config.parameter[paramSpec.name] = paramSpec.defaultValue;
    }
  }

  /** initialize config.panel */
  if (!config.panel) {
    config.panel = { columnPanelOpened: true, parameterPanelOpened: true, };
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

