import { DataSet } from '@antv/data-set';
import { get } from 'lodash';

import { TableData } from './table-data';
import { Transformation } from './transformation';

// tslint:disable-next-line:no-any
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

    if (config.mode !== 'scatterChart') {
      dv.transform({
        type: 'aggregate',
        fields: config.values.map(v => v.name),
        operations: aggregates,
        as: values,
        groupBy: [...keys, ...groups]
      });

      // dv.transform({
      //   type: 'fill-rows',
      //   groupBy: groups,
      //   orderBy: keys,
      //   fillBy: 'order'
      // });

      dv.transform({
        type: 'fill-rows',
        groupBy: [...keys, ...groups],
        fillBy: 'group'
      });

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
        groupsData.push({
          ...row,
          __key__: groupName ? `${row.__key__}.${groupName}` : row.__key__
        });
      });
    });

    groupsData.sort(
      (a, b) =>
        dv.origin.findIndex(o => o[firstKey] === a[firstKey]) - dv.origin.findIndex(o => o[firstKey] === b[firstKey])
    );

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
