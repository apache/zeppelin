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


export function getGroupAndAggrColumns(axisSpecs, axisConfig) {
  const groupAxisNames = [];
  const aggrAxisNames = [];

  for(let i = 0; i < axisSpecs.length; i++) {
    const axisSpec = axisSpecs[i];

    // if duplicated, use it as `group`
    if (isGroup(axisSpec)) { groupAxisNames.push(axisSpec.name); }
    else if (isAggregator(axisSpec)) { aggrAxisNames.push(axisSpec.name); }
  }

  let groupColumns = []; /** `group` */
  let aggregatedColumns = []; /** `aggregator` */
  let normalColumns = []; /** specified, but not group and aggregator */

  for(let colName in axisConfig) {
    const columns = axisConfig[colName];
    if (groupAxisNames.includes(colName)) {
      groupColumns = groupColumns.concat(columns);
    } else if (aggrAxisNames.includes(colName)) {
      aggregatedColumns = aggregatedColumns.concat(columns);
    } else {
      normalColumns = normalColumns.concat(columns);
    }
  }

  return {
    groupColumns: groupColumns,
    aggregatedColumns: aggregatedColumns,
    normalColumns: normalColumns,
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

export function groupAndAggregateRows(rows, groupColumns, aggregatedColumns) {
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
        return { group: groupKey, groupedRows: groupedRows, aggregated: aggregated, };
      }

      // accumulate columnar values to compute
      const columnar = {}
      for (let i = 0; i < groupedRows.length; i++) {
        const row = groupedRows[i]

        for(let j = 0; j < aggregatedColumns.length; j++) {
          const aggrColumn = aggregatedColumns[j];
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

      return { group: groupKey, groupedRows: groupedRows, aggregated: aggregated, }
    })
    .value();

  return converted;
}

