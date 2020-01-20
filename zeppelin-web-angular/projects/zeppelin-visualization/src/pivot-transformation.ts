/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { DataSet } from '@antv/data-set';
import { get } from 'lodash';

import { TableData } from './table-data';
import { Transformation } from './transformation';

export class PivotTransformation extends Transformation {
  constructor(config) {
    super(config);
  }

  removeUnknown(array: Array<{ name: string }>, tableData: TableData): void {
    for (let i = 0; i < array.length; i++) {
      // remove non existing column
      let found = false;
      for (let j = 0; j < tableData.columns.length; j++) {
        const a = array[i];
        const b = tableData.columns[j];
        if (a.name === b) {
          found = true;
          break;
        }
      }
      if (!found) {
        array.splice(i, 1);
        i--;
      }
    }
  }

  setDefaultConfig(tableData: TableData) {
    const config = this.getConfig();
    config.keys = config.keys || [];
    config.groups = config.groups || [];
    config.values = config.values || [];
    this.removeUnknown(config.keys, tableData);
    this.removeUnknown(config.values, tableData);
    this.removeUnknown(config.groups, tableData);
    if (config.keys.length === 0 && config.groups.length === 0 && config.values.length === 0) {
      if (config.keys.length === 0 && tableData.columns[0]) {
        config.keys = [
          {
            name: tableData.columns[0],
            index: 0,
            aggr: 'sum'
          }
        ];
      }

      if (config.values.length === 0 && tableData.columns[1]) {
        config.values = [
          {
            name: tableData.columns[1],
            index: 1,
            aggr: 'sum'
          }
        ];
      }
    }
  }

  // tslint:disable-next-line:no-any
  transform(tableData: TableData): any {
    const config = this.getConfig();
    this.setDefaultConfig(tableData);
    const ds = new DataSet();
    let dv = ds.createView().source(tableData.rows);

    let firstKey = '';
    if (config.keys && config.keys[0]) {
      firstKey = config.keys[0].name;
    }
    let keys = [];
    let groups = [];
    let values = [];
    let aggregates = [];

    // set values from config
    if (config.mode !== 'scatterChart') {
      keys = config.keys.map(e => e.name);
      groups = config.groups.map(e => e.name);
      values = config.values.map(v => `${v.name}(${v.aggr})`);
      aggregates = config.values.map(v => (v.aggr === 'avg' ? 'mean' : v.aggr));
    } else {
      const xAxis = get(config.setting, 'scatterChart.xAxis.name', tableData.columns[0]);
      const yAxis = get(config.setting, 'scatterChart.yAxis.name', tableData.columns[1]);
      const group = get(config.setting, 'scatterChart.group.name');
      keys = xAxis ? [xAxis] : [];
      values = yAxis ? [yAxis] : [];
      groups = group ? [group] : [];
    }

    // try coercion to number type
    dv.transform({
      type: 'map',
      callback: row => {
        Object.keys(row).forEach(k => {
          if (config.keys.map(e => e.name).indexOf(k) === -1) {
            const numberValue = Number.parseFloat(row[k]);
            row[k] = Number.isFinite(numberValue) ? numberValue : row[k];
          }
        });
        return row;
      }
    });

    // not applicable with type scatter chart
    if (config.mode !== 'scatterChart') {

      // aggregate values
      dv.transform({
        type: 'aggregate',
        fields: config.values.map(v => v.name),
        operations: aggregates,
        as: values,
        groupBy: [...keys, ...groups]
      });

      // fill the rows to keep the charts is continuity
      dv.transform({
        type: 'fill-rows',
        groupBy: [...keys, ...groups],
        fillBy: 'group'
      });

      /**
       * fill the field to keep the charts is continuity
       *
       * before
       * ```
       * [
       *  { x: 0, y: 1 },
       *  { x: 0, y: 2 },
       *  { x: 0, y: 3 },
       *  { x: 0 }
       * ]
       * ```
       * after
       * ```
       * [
       *  { x: 0, y: 1 },
       *  { x: 0, y: 2 },
       *  { x: 0, y: 3 },
       *  { x: 0, y: 0 }
       * //      ^^^^^ filled this
       * ]
       * ```
       */
      config.values
        .map(v => `${v.name}(${v.aggr})`)
        .forEach(field => {
          dv.transform({
            field,
            type: 'impute',
            groupBy: keys,
            method: 'value',
            value: config.mode === 'stackedAreaChart' ? 0 : null
          });
        });
    }

    dv.transform({
      type: 'fold',
      fields: values,
      key: '__key__',
      value: '__value__'
    });

    dv.transform({
      type: 'partition',
      groupBy: groups
    });

    const groupsData = [];
    Object.keys(dv.rows).forEach(groupKey => {
      const groupName = groupKey.replace(/^_/, '');
      dv.rows[groupKey].forEach(row => {
        const getKey = () => {
          if (config.mode !== 'pieChart') {
            return groupName ? `${row.__key__}.${groupName}` : row.__key__
          } else {
            const keyName = keys.map(k => row[k]).join('.');
            return groupName ? `${keyName}.${groupName}` : keyName;
          }
        };
        groupsData.push({
          ...row,
          __key__: getKey()
        });
      });
    });

    groupsData.sort(
      (a, b) =>
        dv.origin.findIndex(o => o[firstKey] === a[firstKey]) - dv.origin.findIndex(o => o[firstKey] === b[firstKey])
    );

    console.log(groupsData);
    dv = ds
      .createView({
        state: {
          filterData: null
        }
      })
      .source(groupsData);

    if (config.mode === 'stackedAreaChart' || config.mode === 'pieChart') {
      dv.transform({
        type: 'percent',
        field: '__value__',
        dimension: '__key__',
        groupBy: keys,
        as: '__percent__'
      });
    }
    return dv;
  }
}
